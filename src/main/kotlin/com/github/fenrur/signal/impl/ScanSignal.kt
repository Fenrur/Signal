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
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param T the type of source values
 * @param R the type of accumulated value
 * @param source the source signal
 * @param initial the initial accumulator value
 * @param accumulator function to combine accumulator with new value
 */
class ScanSignal<T, R>(
    private val source: Signal<T>,
    private val initial: R,
    private val accumulator: (acc: R, value: T) -> R
) : Signal<R> {

    private val ref = AtomicReference(accumulator(initial, source.value))
    private val lastSourceValue = AtomicReference(source.value)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubscribeSource = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubscribeSource.set(source.subscribe { either ->
                if (closed.get()) return@subscribe
                either.fold(
                    { ex -> notifyAllError(listeners.toList(), ex) },
                    { newValue ->
                        // Skip if this is the same value we already processed
                        val lastSeen = lastSourceValue.getAndSet(newValue)
                        if (lastSeen != newValue) {
                            val newAcc = accumulator(ref.get(), newValue)
                            ref.set(newAcc)
                            notifyAllValue(listeners.toList(), newAcc)
                        }
                    }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubscribeSource.getAndSet {}.invoke()
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: R get() = ref.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (isClosed) return {}
        ensureSubscribed()
        listener(Either.Right(value))
        listeners += listener
        return {
            listeners -= listener
            maybeUnsubscribe()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            listeners.clear()
            if (subscribed.compareAndSet(true, false)) {
                unsubscribeSource.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "ScanSignal(value=$value, isClosed=$isClosed)"
}
