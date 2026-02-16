package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

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
    private val currentInner = AtomicReference<Signal<T>?>(null)

    // Glitch-free infrastructure
    private val flag = AtomicReference(SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.load()
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val lastOuterVersion = AtomicLong(-1L)
    private val lastInnerVersion = AtomicLong(-1L)
    private val lastNotifiedVersion = AtomicLong(-1L)

    // Exception handling - stores last computation error for rethrow on subsequent reads
    private val lastComputeError = AtomicReference<Throwable?>(null)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                try {
                    val currentValue = this@FlattenSignal.value
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
            // Register as target of outer signal
            registerAsTarget(source)

            // Subscribe to outer signal for error propagation
            unsubscribeOuter.store(source.subscribe { result ->
                if (closed.load()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners, ex) }
            })

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load()) {
                unregisterAsTarget(source)
                unsubscribeOuter.exchange {}.invoke()
            }
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
            currentInner.load()?.let { unregisterAsTarget(it) }
            innerUnsubscribe.exchange {}.invoke()
            unsubscribeOuter.exchange {}.invoke()

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.load()) {
                ensureSubscribed()
            }
        }
    }

    private fun getVersion(s: Signal<*>): Long = when (s) {
        is SourceSignalNode -> s.version
        is ComputedSignalNode -> { s.validateAndGet(); s.version }
        else -> SignalGraph.globalVersion.load()
    }

    private fun hasSourcesChanged(): Boolean {
        val outerVer = getVersion(source)
        val inner = currentInner.load()
        val innerVer = inner?.let { getVersion(it) } ?: -1L
        return outerVer != lastOuterVersion.load() || innerVer != lastInnerVersion.load()
    }

    private fun validateAndGetTyped(): T {
        // Check for stored error and rethrow if sources haven't changed
        lastComputeError.load()?.let { error ->
            if (!hasSourcesChanged()) {
                throw error
            }
            // Sources changed, clear error and allow retry
            lastComputeError.store(null)
        }

        when (flag.load()) {
            SignalFlag.CLEAN -> {
                // Check source versions for non-subscribed reads
                if (!hasSourcesChanged()) {
                    return cachedValue.load()
                }
                // Source changed, fall through to recompute
            }
            SignalFlag.MAYBE_DIRTY -> {
                if (hasSourcesChanged()) {
                    flag.store(SignalFlag.DIRTY)
                } else {
                    flag.store(SignalFlag.CLEAN)
                    return cachedValue.load()
                }
            }
            SignalFlag.DIRTY -> {}
        }

        // Get current inner signal
        val newInner = try {
            source.value
        } catch (e: Throwable) {
            lastComputeError.store(e)
            flag.store(SignalFlag.CLEAN)
            throw e
        }
        val oldInner = currentInner.load()

        // If inner signal changed, update registration
        if (newInner !== oldInner) {
            oldInner?.let { unregisterAsTarget(it) }
            currentInner.store(newInner)
            registerAsTarget(newInner)

            // Update inner subscription for errors
            innerUnsubscribe.exchange {}.invoke()
            innerUnsubscribe.store(newInner.subscribe { result ->
                if (closed.load()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners, ex) }
            })
        }

        val newValue = try {
            newInner.value
        } catch (e: Throwable) {
            lastComputeError.store(e)
            lastOuterVersion.store(getVersion(source))
            lastInnerVersion.store(getVersion(newInner))
            flag.store(SignalFlag.CLEAN)
            throw e
        }
        val oldValue = cachedValue.load()

        lastOuterVersion.store(getVersion(source))
        lastInnerVersion.store(getVersion(newInner))

        if (!hasInitialValue.exchange(true) || oldValue != newValue) {
            cachedValue.store(newValue)
            _version.incrementAndFetch()
        }

        flag.store(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.load()
    override val value: T get() = validateAndGetTyped()
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

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                currentInner.load()?.let { unregisterAsTarget(it) }
                innerUnsubscribe.exchange {}.invoke()
                unsubscribeOuter.exchange {}.invoke()
            }
        }
    }

    override fun toString(): String = "FlattenSignal(value=$value, isClosed=$isClosed)"
}
