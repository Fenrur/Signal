package com.github.fenrur.signal.impl

import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A bidirectionally-mapped [MutableSignal] that transforms values in both directions,
 * with glitch-free semantics.
 *
 * Reading applies [forward] to the source value, writing applies [reverse] before
 * setting the source. Subscribers receive forward-transformed values.
 * Uses lazy subscription and push-pull validation to prevent memory leaks
 * and ensure subscribers never see inconsistent intermediate states.
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

    // Glitch-free infrastructure
    private val flag = AtomicReference(SignalFlag.DIRTY)
    private val _version = AtomicLong(0L)
    override val version: Long get() = _version.get()
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()
    private val lastSourceVersion = AtomicLong(-1L)

    private val lastNotifiedVersion = AtomicLong(-1L)

    private val listenerEffect = object : EffectNode {
        private val pending = AtomicBoolean(false)
        override fun markPending(): Boolean = pending.compareAndSet(false, true)
        override fun execute() {
            pending.set(false)
            if (!closed.get() && listeners.isNotEmpty()) {
                val currentValue = this@BimappedSignal.value
                val currentVersion = _version.get()
                if (lastNotifiedVersion.getAndSet(currentVersion) != currentVersion) {
                    notifyAllValue(listeners.toList(), currentValue)
                }
            }
        }
    }

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            registerAsTarget(source)
            unsubscribeSource.set(source.subscribe { result ->
                if (closed.get()) return@subscribe
                result.onFailure { ex -> notifyAllError(listeners.toList(), ex) }
            })
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
            unsubscribeSource.getAndSet {}.invoke()
        }
    }

    private fun getSourceVersion(): Long {
        return if (source is SourceSignalNode) {
            source.version
        } else if (source is ComputedSignalNode) {
            source.validateAndGet()
            source.version
        } else {
            SignalGraph.globalVersion.get()
        }
    }

    private fun validateAndGetTyped(): R {
        when (flag.get()) {
            SignalFlag.CLEAN -> {
                // Check source version for non-subscribed reads
                if (getSourceVersion() == lastSourceVersion.get()) {
                    return cachedValue.get()
                }
                // Source changed, fall through to recompute
            }
            SignalFlag.MAYBE_DIRTY -> {
                if (getSourceVersion() != lastSourceVersion.get()) {
                    flag.set(SignalFlag.DIRTY)
                } else {
                    flag.set(SignalFlag.CLEAN)
                    return cachedValue.get()
                }
            }
            SignalFlag.DIRTY -> {}
        }

        val sv = source.value
        val newValue = forward(sv)
        val oldValue = cachedValue.get()

        lastSourceVersion.set(getSourceVersion())

        if (!hasInitialValue.getAndSet(true) || oldValue != newValue) {
            cachedValue.set(newValue)
            _version.incrementAndGet()
        }

        flag.set(SignalFlag.CLEAN)
        return newValue
    }

    override val isClosed: Boolean get() = closed.get()

    override var value: R
        get() = validateAndGetTyped()
        set(new) {
            if (isClosed) return
            source.value = reverse(new)
        }

    override fun update(transform: (R) -> R) {
        if (isClosed) return
        source.update { s ->
            val mapped = forward(s)
            val transformed = transform(mapped)
            reverse(transformed)
        }
    }

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
                unregisterAsTarget(source)
                unsubscribeSource.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "BimappedSignal(value=$value, isClosed=$isClosed)"
}
