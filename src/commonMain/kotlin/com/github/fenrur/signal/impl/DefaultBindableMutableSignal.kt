package com.github.fenrur.signal.impl

import com.github.fenrur.signal.BindableMutableSignal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * Default implementation of [BindableMutableSignal] with glitch-free semantics.
 *
 * Implements [ComputedSignalNode] to participate in the dependency graph.
 * When the bound signal changes, this signal propagates dirty marks to its targets.
 * Uses lazy subscription - only subscribes to bound signal when there are listeners or targets.
 *
 * ## Thread-Safety
 *
 * All operations are thread-safe. The binding operation uses a two-phase commit:
 * 1. Pre-check: Validate that binding would not create a cycle (fast path rejection)
 * 2. Atomic bind: Atomically update the binding reference
 * 3. Post-check: Verify no cycle was created by a concurrent binding operation
 * 4. Rollback if needed: If a cycle was detected post-binding, rollback and throw
 *
 * This ensures that even under concurrent binding operations, no cycles can exist
 * in the binding graph.
 *
 * @param T the type of value held by the signal
 * @param initial optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding or closing
 */
class DefaultBindableMutableSignal<T>(
    initial: MutableSignal<T>? = null,
    private val takeOwnership: Boolean = false
) : BindableMutableSignal<T>, ComputedSignalNode {

    private data class BindingData<T>(
        val signal: MutableSignal<T>,
        val unSubscriber: UnSubscriber
    )

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val bindingData = AtomicReference<BindingData<T>?>(null)

    // Lazy subscription
    private val subscribed = AtomicBoolean(false)

    // Glitch-free infrastructure
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val flag = AtomicReference(SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.load()
    private val cachedValue = AtomicReference<T?>(null)
    private val lastSourceVersion = AtomicLong(-1L)
    private val lastNotifiedVersion = AtomicLong(-1L)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                val data = bindingData.load()
                if (data != null) {
                    val currentValue = validateAndGetTyped()
                    val currentVersion = _version.load()
                    if (lastNotifiedVersion.exchange(currentVersion) != currentVersion) {
                        notifyAllValue(listeners, currentValue)
                    }
                }
            }
        }
    }

    init {
        if (initial != null) {
            bindToInternal(initial, notifyListeners = false)
        }
    }

    private fun getSourceVersion(signal: Signal<*>): Long = when (signal) {
        is SourceSignalNode -> signal.version
        is ComputedSignalNode -> {
            signal.validateAndGet()
            signal.version
        }
        else -> SignalGraph.globalVersion.load()
    }

    private fun registerAsTarget(signal: Signal<*>) {
        when (signal) {
            is SourceSignalNode -> signal.addTarget(this)
            is ComputedSignalNode -> signal.addTarget(this)
        }
    }

    private fun unregisterAsTarget(signal: Signal<*>) {
        when (signal) {
            is SourceSignalNode -> signal.removeTarget(this)
            is ComputedSignalNode -> signal.removeTarget(this)
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            val data = bindingData.load()
            if (data != null) {
                registerAsTarget(data.signal)
            }

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load() && data != null) {
                unregisterAsTarget(data.signal)
            }
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            val data = bindingData.load()
            if (data != null) {
                unregisterAsTarget(data.signal)
            }

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.load()) {
                ensureSubscribed()
            }
        }
    }

    private fun validateAndGetTyped(): T {
        val data = bindingData.load()
            ?: throw IllegalStateException("BindableMutableSignal is not bound")

        when (flag.load()) {
            SignalFlag.CLEAN -> {
                val sourceVersion = getSourceVersion(data.signal)
                if (sourceVersion == lastSourceVersion.load()) {
                    @Suppress("UNCHECKED_CAST")
                    return cachedValue.load() as T
                }
            }
            SignalFlag.MAYBE_DIRTY -> {
                val sourceVersion = getSourceVersion(data.signal)
                if (sourceVersion != lastSourceVersion.load()) {
                    flag.store(SignalFlag.DIRTY)
                } else {
                    flag.store(SignalFlag.CLEAN)
                    @Suppress("UNCHECKED_CAST")
                    return cachedValue.load() as T
                }
            }
            SignalFlag.DIRTY -> {}
        }

        // Recompute
        val newValue = data.signal.value
        val oldValue = cachedValue.load()

        lastSourceVersion.store(getSourceVersion(data.signal))

        if (oldValue != newValue) {
            cachedValue.store(newValue)
            _version.incrementAndFetch()
        }

        flag.store(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean
        get() = closed.load()

    override var value: T
        get() = validateAndGetTyped()
        set(newValue) {
            if (isClosed) return
            // Capture binding data atomically to prevent race with concurrent bindTo()
            // We intentionally write to the signal that was bound at the time of this call,
            // even if another thread is concurrently rebinding to a different signal.
            val signal = bindingData.load()?.signal
                ?: throw IllegalStateException("BindableMutableSignal is not bound")
            if (signal.isClosed) {
                throw IllegalStateException("Bound signal has been closed")
            }
            signal.value = newValue
        }

    override fun update(transform: (T) -> T) {
        if (isClosed) return
        // Capture binding data atomically to prevent race with concurrent bindTo()
        val signal = bindingData.load()?.signal
            ?: throw IllegalStateException("BindableMutableSignal is not bound")
        if (signal.isClosed) {
            throw IllegalStateException("Bound signal has been closed")
        }
        signal.update(transform)
    }

    override fun validateAndGet(): Any? = validateAndGetTyped()

    override fun markDirty() {
        if (flag.exchange(SignalFlag.DIRTY) == SignalFlag.CLEAN) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun markMaybeDirty() {
        // Use CAS to atomically transition CLEAN -> MAYBE_DIRTY
        if (flag.compareAndSet(SignalFlag.CLEAN, SignalFlag.MAYBE_DIRTY)) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun addTarget(target: DirtyMarkable) {
        targets += target
        ensureSubscribed()
    }

    override fun removeTarget(target: DirtyMarkable) {
        targets -= target
        maybeUnsubscribe()
    }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Result.success(value))
        lastNotifiedVersion.store(_version.load())
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            bindingData.exchange(null)?.let { data ->
                try {
                    data.unSubscriber.invoke()
                } catch (_: Throwable) {
                }
                if (subscribed.load()) {
                    unregisterAsTarget(data.signal)
                }
                if (takeOwnership) {
                    try {
                        data.signal.close()
                    } catch (_: Throwable) {
                    }
                }
            }
            listeners.clear()
            targets.clear()
            subscribed.store(false)
        }
    }

    private fun bindToInternal(newSignal: MutableSignal<T>, notifyListeners: Boolean) {
        if (isClosed) return

        // Phase 1: Pre-check for cycles (fast path rejection)
        if (BindableMutableSignal.wouldCreateCycle(this, newSignal)) {
            throw IllegalStateException("Circular binding detected: binding would create a cycle")
        }

        // Subscribe for error propagation
        val unSub = newSignal.subscribe { result ->
            if (isClosed) return@subscribe
            result.onFailure { ex -> notifyAllError(listeners, ex) }
        }

        // Phase 2: Atomic bind
        val oldData = bindingData.exchange(BindingData(newSignal, unSub))

        // Phase 3: Post-check for cycles (detect concurrent binding race)
        // Another thread might have bound in a way that now creates a cycle
        if (BindableMutableSignal.wouldCreateCycle(this, newSignal)) {
            // Phase 4: Rollback - restore old binding
            bindingData.store(oldData)
            try {
                unSub.invoke()
            } catch (_: Throwable) {
            }
            throw IllegalStateException("Circular binding detected: concurrent binding created a cycle")
        }

        // Clean up old binding
        oldData?.let { data ->
            try {
                data.unSubscriber.invoke()
            } catch (_: Throwable) {
            }
            if (subscribed.load()) {
                unregisterAsTarget(data.signal)
            }
            if (takeOwnership && data.signal !== newSignal) {
                try {
                    data.signal.close()
                } catch (_: Throwable) {
                }
            }
        }

        // Register as target if already subscribed
        if (subscribed.load()) {
            registerAsTarget(newSignal)
        }

        // Mark dirty and trigger update
        flag.store(SignalFlag.DIRTY)
        _version.incrementAndFetch()
        SignalGraph.incrementGlobalVersion()

        if (notifyListeners) {
            SignalGraph.startBatch()
            try {
                for (target in targets) {
                    target.markDirty()
                }
                if (listeners.isNotEmpty()) {
                    SignalGraph.scheduleEffect(listenerEffect)
                }
            } finally {
                SignalGraph.endBatch()
            }
        }
    }

    override fun bindTo(newSignal: MutableSignal<T>) {
        bindToInternal(newSignal, notifyListeners = true)
    }

    override fun currentSignal(): MutableSignal<T>? = bindingData.load()?.signal

    override fun isBound(): Boolean = bindingData.load() != null

    override fun toString(): String {
        val boundValue = try {
            value.toString()
        } catch (_: IllegalStateException) {
            "<not bound>"
        }
        return "DefaultBindableMutableSignal(value=$boundValue, version=$version, isClosed=$isClosed, isBound=${isBound()})"
    }
}
