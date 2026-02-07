package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that flattens a nested Signal<Signal<T>> to Signal<T> with glitch-free semantics.
 *
 * When the outer signal changes, this signal switches to the new inner signal.
 * Similar to RxJS switchMap or Kotlin's flatMapLatest.
 * Uses lazy subscription and push-pull validation to prevent memory leaks
 * and ensure subscribers never see inconsistent intermediate states.
 *
 * @param T the type of the inner signal value
 * @param source the outer signal containing inner signals
 */
class FlattenSignal<T>(
    private val source: Signal<Signal<T>>
) : Signal<T>, ComputedSignalNode {

    private val cachedValue = AtomicReference<T>(null as T)
    private val hasInitialValue = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val innerUnsubscribe = AtomicReference<UnSubscriber> {}
    private val unsubscribeOuter = AtomicReference<UnSubscriber> {}
    private val currentInner = AtomicReference<Signal<T>>(null)

    // Glitch-free infrastructure
    private val flag = AtomicReference(SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.get()
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val lastOuterVersion = AtomicLong(-1L)
    private val lastInnerVersion = AtomicLong(-1L)
    private val lastNotifiedVersion = AtomicLong(-1L)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.set(false)
            if (!closed.get() && listeners.isNotEmpty()) {
                val currentValue = this@FlattenSignal.value
                val currentVersion = _version.get()
                if (lastNotifiedVersion.getAndSet(currentVersion) != currentVersion) {
                    notifyAllValue(listeners.toList(), currentValue)
                }
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            // Register as target of outer signal
            registerAsTarget(source)

            // Subscribe to outer signal for error propagation
            unsubscribeOuter.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners.toList(), ex) }
            })
        }
    }

    private fun registerAsTarget(source: Signal<*>) {
        when (source) {
            is SourceSignalNode -> source.addTarget(this)
            is ComputedSignalNode -> source.addTarget(this)
        }
    }

    private fun unregisterAsTarget(source: Signal<*>) {
        when (source) {
            is SourceSignalNode -> source.removeTarget(this)
            is ComputedSignalNode -> source.removeTarget(this)
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            unregisterAsTarget(source)
            currentInner.get()?.let { unregisterAsTarget(it) }
            innerUnsubscribe.getAndSet {}.invoke()
            unsubscribeOuter.getAndSet {}.invoke()
        }
    }

    private fun getVersion(s: Signal<*>): Long = when (s) {
        is SourceSignalNode -> s.version
        is ComputedSignalNode -> { s.validateAndGet(); s.version }
        else -> SignalGraph.globalVersion.get()
    }

    private fun validateAndGetTyped(): T {
        when (flag.get()) {
            SignalFlag.CLEAN -> {
                // Check source versions for non-subscribed reads
                val outerVer = getVersion(source)
                val inner = currentInner.get()
                val innerVer = inner?.let { getVersion(it) } ?: -1L
                if (outerVer == lastOuterVersion.get() && innerVer == lastInnerVersion.get()) {
                    return cachedValue.get()
                }
                // Source changed, fall through to recompute
            }
            SignalFlag.MAYBE_DIRTY -> {
                val outerVer = getVersion(source)
                val inner = currentInner.get()
                val innerVer = inner?.let { getVersion(it) } ?: -1L

                if (outerVer != lastOuterVersion.get() || innerVer != lastInnerVersion.get()) {
                    flag.set(SignalFlag.DIRTY)
                } else {
                    flag.set(SignalFlag.CLEAN)
                    return cachedValue.get()
                }
            }
            SignalFlag.DIRTY -> {}
        }

        // Get current inner signal
        val newInner = source.value
        val oldInner = currentInner.get()

        // If inner signal changed, update registration
        if (newInner !== oldInner) {
            oldInner?.let { unregisterAsTarget(it) }
            currentInner.set(newInner)
            registerAsTarget(newInner)

            // Update inner subscription for errors
            innerUnsubscribe.getAndSet {}.invoke()
            innerUnsubscribe.set(newInner.subscribe { result ->
                if (closed.get()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners.toList(), ex) }
            })
        }

        val newValue = newInner.value
        val oldValue = cachedValue.get()

        lastOuterVersion.set(getVersion(source))
        lastInnerVersion.set(getVersion(newInner))

        if (!hasInitialValue.getAndSet(true) || oldValue != newValue) {
            cachedValue.set(newValue)
            _version.incrementAndGet()
        }

        flag.set(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: T get() = validateAndGetTyped()
    override fun validateAndGet(): Any? = validateAndGetTyped()

    override fun markDirty() {
        if (flag.getAndSet(SignalFlag.DIRTY) == SignalFlag.CLEAN) {
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

    override fun addTarget(target: DirtyMarkable) { targets += target; ensureSubscribed() }
    override fun removeTarget(target: DirtyMarkable) { targets -= target; maybeUnsubscribe() }

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Result.success(value))
        lastNotifiedVersion.set(_version.get())
        listeners += listener
        return { listeners -= listener; maybeUnsubscribe() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            targets.clear()
            if (subscribed.compareAndSet(true, false)) {
                unregisterAsTarget(source)
                currentInner.get()?.let { unregisterAsTarget(it) }
                innerUnsubscribe.getAndSet {}.invoke()
                unsubscribeOuter.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "FlattenSignal(value=$value, isClosed=$isClosed)"
}
