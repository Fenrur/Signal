package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * A [io.github.fenrur.signal.Signal] that flattens a nested Signal<Signal<T>> to Signal<T> with glitch-free semantics.
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
    private val source: io.github.fenrur.signal.Signal<io.github.fenrur.signal.Signal<T>>
) : io.github.fenrur.signal.Signal<T>, io.github.fenrur.signal.impl.ComputedSignalNode {

    private val cachedValue = AtomicReference<T>(null as T)
    private val hasInitialValue = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<io.github.fenrur.signal.SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val innerUnsubscribe = AtomicReference<io.github.fenrur.signal.UnSubscriber> {}
    private val unsubscribeOuter = AtomicReference<io.github.fenrur.signal.UnSubscriber> {}
    private val currentInner = AtomicReference<io.github.fenrur.signal.Signal<T>?>(null)

    // Glitch-free infrastructure
    private val flag = AtomicReference(io.github.fenrur.signal.impl.SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.load()
    private val targets = CopyOnWriteArrayList<io.github.fenrur.signal.impl.DirtyMarkable>()
    private val lastOuterVersion = AtomicLong(-1L)
    private val lastInnerVersion = AtomicLong(-1L)
    private val lastNotifiedVersion = AtomicLong(-1L)

    // Exception handling - stores last computation error for rethrow on subsequent reads
    private val lastComputeError = AtomicReference<Throwable?>(null)

    private val listenerEffect = object : io.github.fenrur.signal.impl.EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                try {
                    val currentValue = this@FlattenSignal.value
                    val currentVersion = _version.load()
                    if (lastNotifiedVersion.exchange(currentVersion) != currentVersion) {
                        io.github.fenrur.signal.impl.notifyAllValue(listeners, currentValue)
                    }
                } catch (e: Throwable) {
                    io.github.fenrur.signal.impl.notifyAllError(listeners, e)
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
                result.onFailure { ex -> io.github.fenrur.signal.impl.notifyAllError(listeners, ex) }
            })

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load()) {
                unregisterAsTarget(source)
                unsubscribeOuter.exchange {}.invoke()
            }
        }
    }

    private fun registerAsTarget(source: io.github.fenrur.signal.Signal<*>) {
        when (source) {
            is io.github.fenrur.signal.impl.SourceSignalNode -> source.addTarget(this)
            is io.github.fenrur.signal.impl.ComputedSignalNode -> source.addTarget(this)
        }
    }

    private fun unregisterAsTarget(source: io.github.fenrur.signal.Signal<*>) {
        when (source) {
            is io.github.fenrur.signal.impl.SourceSignalNode -> source.removeTarget(this)
            is io.github.fenrur.signal.impl.ComputedSignalNode -> source.removeTarget(this)
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

    private fun getVersion(s: io.github.fenrur.signal.Signal<*>): Long = when (s) {
        is io.github.fenrur.signal.impl.SourceSignalNode -> s.version
        is io.github.fenrur.signal.impl.ComputedSignalNode -> { s.validateAndGet(); s.version }
        else -> io.github.fenrur.signal.impl.SignalGraph.globalVersion.load()
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
            io.github.fenrur.signal.impl.SignalFlag.CLEAN -> {
                // Check source versions for non-subscribed reads
                if (!hasSourcesChanged()) {
                    return cachedValue.load()
                }
                // Source changed, fall through to recompute
            }
            io.github.fenrur.signal.impl.SignalFlag.MAYBE_DIRTY -> {
                if (hasSourcesChanged()) {
                    flag.store(io.github.fenrur.signal.impl.SignalFlag.DIRTY)
                } else {
                    flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
                    return cachedValue.load()
                }
            }
            io.github.fenrur.signal.impl.SignalFlag.DIRTY -> {}
        }

        // Get current inner signal
        val newInner = try {
            source.value
        } catch (e: Throwable) {
            lastComputeError.store(e)
            flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
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
                result.onFailure { ex -> io.github.fenrur.signal.impl.notifyAllError(listeners, ex) }
            })
        }

        val newValue = try {
            newInner.value
        } catch (e: Throwable) {
            lastComputeError.store(e)
            lastOuterVersion.store(getVersion(source))
            lastInnerVersion.store(getVersion(newInner))
            flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
            throw e
        }
        val oldValue = cachedValue.load()

        lastOuterVersion.store(getVersion(source))
        lastInnerVersion.store(getVersion(newInner))

        if (!hasInitialValue.exchange(true) || oldValue != newValue) {
            cachedValue.store(newValue)
            _version.incrementAndFetch()
        }

        flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.load()
    override val value: T get() = validateAndGetTyped()
    override fun validateAndGet(): Any? = validateAndGetTyped()

    override fun markDirty() {
        if (flag.exchange(io.github.fenrur.signal.impl.SignalFlag.DIRTY) == io.github.fenrur.signal.impl.SignalFlag.CLEAN) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) io.github.fenrur.signal.impl.SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun markMaybeDirty() {
        // Use CAS to atomically transition CLEAN -> MAYBE_DIRTY
        if (flag.compareAndSet(io.github.fenrur.signal.impl.SignalFlag.CLEAN, io.github.fenrur.signal.impl.SignalFlag.MAYBE_DIRTY)) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) io.github.fenrur.signal.impl.SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun addTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) { targets += target; ensureSubscribed() }
    override fun removeTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) { targets -= target; maybeUnsubscribe() }

    override fun subscribe(listener: io.github.fenrur.signal.SubscribeListener<T>): io.github.fenrur.signal.UnSubscriber {
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
