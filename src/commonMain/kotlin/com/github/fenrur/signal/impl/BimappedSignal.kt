package com.github.fenrur.signal.impl

import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * A bidirectionally-mapped [MutableSignal] that transforms values in both directions,
 * with glitch-free semantics.
 *
 * Reading applies [forward] to the source value, writing applies [reverse] before
 * setting the source. Subscribers receive forward-transformed values.
 * Uses lazy subscription and push-pull validation to prevent memory leaks
 * and ensure subscribers never see inconsistent intermediate states.
 *
 * ## Exception Handling
 *
 * - **forward transform exceptions**: Caught and propagated to subscribers as Result.failure().
 *   The cached value is preserved, and subsequent reads will retry the transform.
 * - **reverse transform exceptions**: Caught and propagated to subscribers as Result.failure().
 *   The source signal is not modified.
 *
 * Thread-safety: All operations are thread-safe, delegating to the source signal's
 * thread-safety guarantees.
 *
 * @param S the type of the source signal value
 * @param R the type of the mapped signal value
 * @param source the underlying mutable signal
 * @param forward transforms source values to mapped values (read direction)
 * @param reverse transforms mapped values back to source values (write direction)
 */
class BimappedSignal<S, R>(
    private val source: MutableSignal<S>,
    private val forward: (S) -> R,
    private val reverse: (R) -> S
) : MutableSignal<R>, ComputedSignalNode {

    private val cachedValue = AtomicReference<R>(null as R)
    private val hasInitialValue = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    // Track last transform error for proper error propagation
    private val lastTransformError = AtomicReference<Throwable?>(null)

    // Glitch-free infrastructure
    private val flag = AtomicReference(SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.load()
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val lastSourceVersion = AtomicLong(-1L)

    private val lastNotifiedVersion = AtomicLong(-1L)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                // Read value FIRST - this triggers validation and may increment version
                try {
                    val currentValue = this@BimappedSignal.value
                    val currentVersion = _version.load()
                    if (lastNotifiedVersion.exchange(currentVersion) != currentVersion) {
                        notifyAllValue(listeners, currentValue)
                    }
                } catch (e: Throwable) {
                    notifyAllError(listeners, e)
                }
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            registerAsTarget(source)
            unsubscribeSource.store(source.subscribe { result ->
                if (closed.load()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners, ex) }
            })

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load()) {
                unregisterAsTarget(source)
                unsubscribeSource.exchange {}.invoke()
            }
        }
    }

    private fun registerAsTarget(source: MutableSignal<*>) {
        if (source is SourceSignalNode) {
            source.addTarget(this)
        } else if (source is ComputedSignalNode) {
            source.addTarget(this)
        }
    }

    private fun unregisterAsTarget(source: MutableSignal<*>) {
        if (source is SourceSignalNode) {
            source.removeTarget(this)
        } else if (source is ComputedSignalNode) {
            source.removeTarget(this)
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            unregisterAsTarget(source)
            unsubscribeSource.exchange {}.invoke()

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.load()) {
                ensureSubscribed()
            }
        }
    }

    private fun getSourceVersion(): Long {
        return if (source is SourceSignalNode) {
            source.version
        } else if (source is ComputedSignalNode) {
            source.validateAndGet()
            source.version
        } else {
            SignalGraph.globalVersion.load()
        }
    }

    private fun validateAndGetTyped(): R {
        // Check for previous error - rethrow to allow caller to handle
        lastTransformError.load()?.let { error ->
            // Clear the error and rethrow
            lastTransformError.store(null)
            throw error
        }

        when (flag.load()) {
            SignalFlag.CLEAN -> {
                // Check source version for non-subscribed reads
                if (getSourceVersion() == lastSourceVersion.load()) {
                    return cachedValue.load()
                }
                // Source changed, fall through to recompute
            }
            SignalFlag.MAYBE_DIRTY -> {
                if (getSourceVersion() != lastSourceVersion.load()) {
                    flag.store(SignalFlag.DIRTY)
                } else {
                    flag.store(SignalFlag.CLEAN)
                    return cachedValue.load()
                }
            }
            SignalFlag.DIRTY -> {}
        }

        val sv = source.value

        // Wrap forward transform in try-catch
        val newValue = try {
            forward(sv)
        } catch (e: Throwable) {
            // Store error for later detection and rethrow
            // Caller (listenerEffect.execute or subscribe) will handle notification
            lastTransformError.store(e)
            throw e
        }

        val oldValue = cachedValue.load()

        lastSourceVersion.store(getSourceVersion())

        if (!hasInitialValue.exchange(true) || oldValue != newValue) {
            cachedValue.store(newValue)
            _version.incrementAndFetch()
        }

        // Clear any previous error on successful computation
        lastTransformError.store(null)
        flag.store(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.load()

    override var value: R
        get() = validateAndGetTyped()
        set(new) {
            if (isClosed) return
            try {
                source.value = reverse(new)
            } catch (e: Throwable) {
                // Store error and notify listeners directly for write errors
                // (no listenerEffect will catch this, so we must notify here)
                lastTransformError.store(e)
                notifyAllError(listeners, e)
                throw e
            }
        }

    override fun update(transform: (R) -> R) {
        if (isClosed) return
        try {
            source.update { s ->
                val mapped = forward(s)
                val transformed = transform(mapped)
                reverse(transformed)
            }
        } catch (e: Throwable) {
            // Store error for propagation and notify listeners
            lastTransformError.store(e)
            notifyAllError(listeners, e)
            throw e
        }
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

    override fun addTarget(target: DirtyMarkable) { targets += target; ensureSubscribed() }
    override fun removeTarget(target: DirtyMarkable) { targets -= target; maybeUnsubscribe() }

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        // Deliver initial value, handling potential transform errors
        try {
            listener(Result.success(value))
        } catch (e: Throwable) {
            listener(Result.failure(e))
        }
        lastNotifiedVersion.store(_version.load())
        listeners += listener
        return { listeners -= listener; maybeUnsubscribe() }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            targets.clear()
            if (subscribed.compareAndSet(true, false)) {
                unregisterAsTarget(source)
                unsubscribeSource.exchange {}.invoke()
            }
        }
    }

    override fun toString(): String = "BimappedSignal(value=$value, isClosed=$isClosed)"
}
