package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Abstract base class for computed signals with glitch-free semantics.
 *
 * Provides all the common infrastructure:
 * - Push-pull validation model
 * - Lazy subscription management
 * - Version tracking for change detection
 * - Effect scheduling for subscriber notifications
 *
 * Subclasses only need to implement [computeValue] and [sources].
 *
 * Thread-safety: All operations are thread-safe using atomic operations.
 *
 * @param R the type of value held by this signal
 */
abstract class AbstractComputedSignal<R> : Signal<R>, ComputedSignalNode {

    // Signal state
    protected val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    protected val closed = AtomicBoolean(false)
    protected val subscribed = AtomicBoolean(false)
    protected val unsubscribers = AtomicReference<List<UnSubscriber>>(emptyList())

    // Glitch-free infrastructure
    protected val flag = AtomicReference(SignalFlag.CLEAN)
    protected val _version = AtomicLong(1L)
    override val version: Long get() = _version.get()
    protected val targets = CopyOnWriteArrayList<DirtyMarkable>()
    protected val lastNotifiedVersion = AtomicLong(-1L)

    // Cached value
    protected abstract val cachedValue: AtomicReference<R>

    /**
     * The source signals this computed signal depends on.
     */
    protected abstract val sources: List<Signal<*>>

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

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)

        override fun markPending(): Boolean = pending.compareAndSet(false, true)

        override fun execute() {
            pending.set(false)
            if (!closed.get() && listeners.isNotEmpty()) {
                val currentValue = this@AbstractComputedSignal.value
                val currentVersion = _version.get()
                if (lastNotifiedVersion.getAndSet(currentVersion) != currentVersion) {
                    notifyAllValue(listeners.toList(), currentValue)
                }
            }
        }
    }

    protected fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            // Register as target of all sources
            sources.forEach { source ->
                when (source) {
                    is SourceSignalNode -> source.addTarget(this)
                    is ComputedSignalNode -> source.addTarget(this)
                }
            }

            // Subscribe to sources for error propagation
            val unsubs = sources.map { source ->
                source.subscribe { result ->
                    if (closed.get()) return@subscribe
                    result.onFailure { ex -> notifyAllError(listeners.toList(), ex) }
                }
            }
            unsubscribers.set(unsubs)
        }
    }

    protected fun maybeUnsubscribe() {
        if (listeners.isEmpty() && targets.isEmpty() && subscribed.compareAndSet(true, false)) {
            sources.forEach { source ->
                when (source) {
                    is SourceSignalNode -> source.removeTarget(this)
                    is ComputedSignalNode -> source.removeTarget(this)
                }
            }
            unsubscribers.getAndSet(emptyList()).forEach { it.invoke() }
        }
    }

    protected fun getVersion(signal: Signal<*>): Long = when (signal) {
        is SourceSignalNode -> signal.version
        is ComputedSignalNode -> {
            signal.validateAndGet()
            signal.version
        }
        else -> SignalGraph.globalVersion.get()
    }

    protected open fun validateAndGetTyped(): R {
        when (flag.get()) {
            SignalFlag.CLEAN -> {
                if (!hasSourcesChanged()) {
                    return cachedValue.get()
                }
            }
            SignalFlag.MAYBE_DIRTY -> {
                if (hasSourcesChanged()) {
                    flag.set(SignalFlag.DIRTY)
                } else {
                    flag.set(SignalFlag.CLEAN)
                    return cachedValue.get()
                }
            }
            SignalFlag.DIRTY -> {}
        }

        // Recompute
        val newValue = computeValue()
        val oldValue = cachedValue.get()

        updateSourceVersions()

        if (oldValue != newValue) {
            cachedValue.set(newValue)
            _version.incrementAndGet()
        }

        flag.set(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.get()

    override val value: R get() = validateAndGetTyped()

    override fun validateAndGet(): Any? = validateAndGetTyped()

    override fun markDirty() {
        if (flag.getAndSet(SignalFlag.DIRTY) == SignalFlag.CLEAN) {
            targets.forEach { it.markMaybeDirty() }
            if (listeners.isNotEmpty()) SignalGraph.scheduleEffect(listenerEffect)
        }
    }

    override fun markMaybeDirty() {
        // Use CAS to atomically transition CLEAN -> MAYBE_DIRTY
        // This prevents redundant propagation if multiple threads call concurrently
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

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
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
                sources.forEach { source ->
                    when (source) {
                        is SourceSignalNode -> source.removeTarget(this)
                        is ComputedSignalNode -> source.removeTarget(this)
                    }
                }
                unsubscribers.getAndSet(emptyList()).forEach { it.invoke() }
            }
        }
    }
}
