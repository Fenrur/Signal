package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [Signal] that flattens a nested Signal<Signal<T>> to Signal<T>.
 *
 * When the outer signal changes, this signal switches to the new inner signal.
 * Similar to RxJS switchMap or Kotlin's flatMapLatest.
 *
 * @param T the type of the inner signal value
 * @param source the outer signal containing inner signals
 */
class FlattenSignal<T>(
    source: Signal<Signal<T>>
) : Signal<T> {

    private val ref = AtomicReference(source.value.value)
    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val innerUnsubscribe = AtomicReference<UnSubscriber> {}
    private val unsubscribeOuter: UnSubscriber

    init {
        // Subscribe to inner signal initially
        subscribeToInner(source.value)

        // Subscribe to outer signal to switch inner subscriptions
        unsubscribeOuter = source.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { innerSignal ->
                    // Unsubscribe from previous inner
                    innerUnsubscribe.get().invoke()
                    // Subscribe to new inner
                    subscribeToInner(innerSignal)
                }
            )
        }
    }

    private fun subscribeToInner(inner: Signal<T>) {
        val unsub = inner.subscribe { either ->
            if (closed.get()) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { value ->
                    val old = ref.getAndSet(value)
                    if (old != value) {
                        notifyAllValue(listeners.toList(), value)
                    }
                }
            )
        }
        innerUnsubscribe.set(unsub)
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
            innerUnsubscribe.get().invoke()
            unsubscribeOuter()
            listeners.clear()
        }
    }

    override fun toString(): String = "FlattenSignal(value=$value, isClosed=$isClosed)"
}
