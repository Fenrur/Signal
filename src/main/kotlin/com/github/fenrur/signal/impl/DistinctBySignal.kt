package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that only emits when the selected key changes.
 *
 * Unlike the base signal's equality check, this allows custom key extraction
 * for determining distinctness.
 *
 * @param T the type of value held by the signal
 * @param K the type of the key used for comparison
 * @param source the source signal
 * @param keySelector function to extract the comparison key
 */
class DistinctBySignal<T, K>(
    source: Signal<T>,
    private val keySelector: (T) -> K
) : Signal<T> {

    private val ref = AtomicReference(source.value)
    private val lastKey = AtomicReference(keySelector(source.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val unsubscribeSource: UnSubscriber

    init {
        unsubscribeSource = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { newValue ->
                    val newKey = keySelector(newValue)
                    val oldKey = lastKey.get()
                    if (oldKey != newKey) {
                        lastKey.set(newKey)
                        ref.set(newValue)
                        notifyAllValue(listeners.toList(), newValue)
                    }
                }
            )
        }
    }

    override val isClosed: Boolean get() = closed.get()
    override val value: T get() = ref.get()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
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

    override fun toString(): String = "DistinctBySignal(value=$value, isClosed=$isClosed)"
}
