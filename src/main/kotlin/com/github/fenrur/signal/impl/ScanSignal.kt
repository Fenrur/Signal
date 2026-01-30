package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that accumulates values using an accumulator function.
 *
 * Similar to Kotlin's `scan` or `runningFold`, this signal maintains
 * an accumulated state that updates whenever the source emits.
 *
 * @param T the type of source values
 * @param R the type of accumulated value
 * @param source the source signal
 * @param initial the initial accumulator value
 * @param accumulator function to combine accumulator with new value
 */
class ScanSignal<T, R>(
    source: Signal<T>,
    initial: R,
    private val accumulator: (acc: R, value: T) -> R
) : Signal<R> {

    private val ref = AtomicReference(accumulator(initial, source.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubscribeSource: UnSubscriber

    init {
        unsubscribeSource = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { newValue ->
                    val newAcc = accumulator(ref.get(), newValue)
                    val oldAcc = ref.getAndSet(newAcc)
                    if (oldAcc != newAcc) {
                        notifyAllValue(listeners.toList(), newAcc)
                    }
                }
            )
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: R get() = ref.get()

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

    override fun toString(): String = "ScanSignal(value=$value, isClosed=$isClosed)"
}
