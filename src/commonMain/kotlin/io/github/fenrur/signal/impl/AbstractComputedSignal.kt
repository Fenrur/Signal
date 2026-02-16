package io.github.fenrur.signal.impl

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import kotlin.concurrent.atomics.*

/**
 * Abstract base class for computed signals with glitch-free semantics.
 *
 * Provides all the common infrastructure:
 * - Push-pull validation model
 * - Lazy subscription management
 * - Version tracking for change detection
 * - Effect scheduling for subscriber notifications
 * - Exception handling for compute functions
 *
 * Subclasses only need to implement [computeValue] and [sources].
 *
 * ## Thread-Safety
 *
 * All operations are thread-safe using atomic operations. No blocking
 * synchronization is used to avoid deadlocks.
 *
 * ## Exception Handling
 *
 * If [computeValue] throws an exception:
 * 1. The exception is stored and rethrown on subsequent value reads
 * 2. Listeners are notified via Result.failure()
 * 3. The cached value is preserved (last known good value)
 * 4. When the source changes, computation is retried
 *
 * @param R the type of value held by this signal
 */
abstract class AbstractComputedSignal<R> : io.github.fenrur.signal.Signal<R>,
    io.github.fenrur.signal.impl.ComputedSignalNode {

    // Signal state
    protected val listeners = CopyOnWriteArrayList<io.github.fenrur.signal.SubscribeListener<R>>()
    protected val closed = AtomicBoolean(false)
    protected val subscribed = AtomicBoolean(false)
    protected val unsubscribers = AtomicReference<List<io.github.fenrur.signal.UnSubscriber>>(emptyList())

    /**
     * Stores the last exception thrown by [computeValue].
     * Cleared when computation succeeds or sources change.
     */
    protected val lastComputeError = AtomicReference<Throwable?>(null)

    // Glitch-free infrastructure
    protected val flag = AtomicReference(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
    protected val _version = AtomicLong(1L)
    override val version: Long get() = _version.load()
    protected val targets = CopyOnWriteArrayList<io.github.fenrur.signal.impl.DirtyMarkable>()
    protected val lastNotifiedVersion = AtomicLong(-1L)

    // Cached value
    protected abstract val cachedValue: AtomicReference<R>

    /**
     * The source signals this computed signal depends on.
     */
    protected abstract val sources: List<io.github.fenrur.signal.Signal<*>>

    /**
     * Computes the current value. Called during validation when dirty.
     * @return the computed value
     */
    protected abstract fun computeValue(): R

    /**
     * Checks if sources have changed since last computation.
     * @return true if any source has changed
     */
    protected abstract fun hasSourcesChanged(): Boolean

    /**
     * Updates the source version tracking after computation.
     */
    protected abstract fun updateSourceVersions()

    private val listenerEffect = object : io.github.fenrur.signal.impl.EffectNode {
        private val pending = AtomicBoolean(false)

        override fun markPending(): Boolean = pending.compareAndSet(false, true)

        override fun execute() {
            pending.store(false)
            if (!closed.load() && listeners.isNotEmpty()) {
                try {
                    val currentValue = this@AbstractComputedSignal.value
                    val currentVersion = _version.load()
                    if (lastNotifiedVersion.exchange(currentVersion) != currentVersion) {
                        io.github.fenrur.signal.impl.notifyAllValue(listeners, currentValue)
                    }
                } catch (e: Throwable) {
                    // Notify listeners of the computation error
                    io.github.fenrur.signal.impl.notifyAllError(listeners, e)
                }
            }
        }
    }

    protected fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            // Register as target of all sources
            sources.forEach { source ->
                when (source) {
                    is io.github.fenrur.signal.impl.SourceSignalNode -> source.addTarget(this)
                    is io.github.fenrur.signal.impl.ComputedSignalNode -> source.addTarget(this)
                }
            }

            // Subscribe to sources for error propagation
            val unsubs = sources.map { source ->
                source.subscribe { result ->
                    if (closed.load()) return@subscribe
                    result.onFailure { ex ->
                        io.github.fenrur.signal.impl.notifyAllError(
                            listeners,
                            ex
                        )
                    }
                }
            }
            unsubscribers.store(unsubs)

            // Race 4 post-check: if close() ran during registration, undo
            if (closed.load()) {
                sources.forEach { source ->
                    when (source) {
                        is io.github.fenrur.signal.impl.SourceSignalNode -> source.removeTarget(this)
                        is io.github.fenrur.signal.impl.ComputedSignalNode -> source.removeTarget(this)
                    }
                }
                unsubscribers.exchange(emptyList()).forEach { it.invoke() }
            }
        }
    }

    protected fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            sources.forEach { source ->
                when (source) {
                    is io.github.fenrur.signal.impl.SourceSignalNode -> source.removeTarget(this)
                    is io.github.fenrur.signal.impl.ComputedSignalNode -> source.removeTarget(this)
                }
            }
            unsubscribers.exchange(emptyList()).forEach { it.invoke() }

            // Race 5 post-check: if listeners/targets were added during cleanup, re-subscribe
            if ((listeners.isNotEmpty() || targets.isNotEmpty()) && !closed.load()) {
                ensureSubscribed()
            }
        }
    }

    protected fun getVersion(signal: io.github.fenrur.signal.Signal<*>): Long = when (signal) {
        is io.github.fenrur.signal.impl.SourceSignalNode -> signal.version
        is io.github.fenrur.signal.impl.ComputedSignalNode -> {
            signal.validateAndGet()
            signal.version
        }
        else -> io.github.fenrur.signal.impl.SignalGraph.globalVersion.load()
    }

    protected open fun validateAndGetTyped(): R {
        // Check for stored error and rethrow if present
        lastComputeError.load()?.let { error ->
            // Only rethrow if we haven't had a source change
            if (!hasSourcesChanged()) {
                throw error
            }
            // Source changed, clear error and retry
            lastComputeError.store(null)
        }

        when (flag.load()) {
            io.github.fenrur.signal.impl.SignalFlag.CLEAN -> {
                if (!hasSourcesChanged()) {
                    return cachedValue.load()
                }
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

        // Recompute with exception handling
        val newValue = try {
            computeValue()
        } catch (e: Throwable) {
            // Store error for subsequent reads
            lastComputeError.store(e)
            // Update source versions so we don't keep retrying with same input
            updateSourceVersions()
            flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
            // Rethrow - listeners are notified through the effect mechanism
            throw e
        }

        val oldValue = cachedValue.load()
        updateSourceVersions()

        if (oldValue != newValue) {
            cachedValue.store(newValue)
            _version.incrementAndFetch()
        }

        flag.store(io.github.fenrur.signal.impl.SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.load()

    override val value: R get() = validateAndGetTyped()

    override fun validateAndGet(): Any? = validateAndGetTyped()

    override fun markDirty() {
        if (flag.exchange(io.github.fenrur.signal.impl.SignalFlag.DIRTY) == io.github.fenrur.signal.impl.SignalFlag.CLEAN) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) io.github.fenrur.signal.impl.SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun markMaybeDirty() {
        // Use CAS to atomically transition CLEAN -> MAYBE_DIRTY
        // This prevents redundant propagation if multiple threads call concurrently
        if (flag.compareAndSet(io.github.fenrur.signal.impl.SignalFlag.CLEAN, io.github.fenrur.signal.impl.SignalFlag.MAYBE_DIRTY)) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) io.github.fenrur.signal.impl.SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun addTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) {
        targets += target
        ensureSubscribed()
    }

    override fun removeTarget(target: io.github.fenrur.signal.impl.DirtyMarkable) {
        targets -= target
        maybeUnsubscribe()
    }

    override fun subscribe(listener: io.github.fenrur.signal.SubscribeListener<R>): io.github.fenrur.signal.UnSubscriber {
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
                sources.forEach { source ->
                    when (source) {
                        is io.github.fenrur.signal.impl.SourceSignalNode -> source.removeTarget(this)
                        is io.github.fenrur.signal.impl.ComputedSignalNode -> source.removeTarget(this)
                    }
                }
                unsubscribers.exchange(emptyList()).forEach { it.invoke() }
            }
        }
    }
}
