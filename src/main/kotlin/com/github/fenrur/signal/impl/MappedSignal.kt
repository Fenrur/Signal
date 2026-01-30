package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A derived read-only [Signal] that transforms values from a source signal.
 *
 * This signal applies a transformation function to each value emitted by the source signal.
 * It automatically subscribes to the source and propagates transformed values.
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param S the type of the source signal value
 * @param R the type of the transformed value
 * @param source the source signal to transform
 * @param transform the transformation function
 */
class MappedSignal<S, R>(
    source: Signal<S>,
    private val transform: (S) -> R
) : Signal<R> {

    private val ref = AtomicReference(transform(source.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubscribeSource: UnSubscriber

    init {
        unsubscribeSource = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { sv ->
                    val new = transform(sv)
                    val old = ref.getAndSet(new)
                    if (old != new) notifyAllValue(listeners.toList(), new)
                }
            )
        }
    }

    override val isClosed: Boolean
        get() = closed.get()

    override val value: R
        get() = ref.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (isClosed) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            unsubscribeSource()
        }
    }

    override fun toString(): String = "MappedSignal(value=$value, isClosed=$isClosed)"
}
