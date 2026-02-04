package com.github.fenrur.signal.impl

import com.github.fenrur.signal.BindableSignal
import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Default implementation of [BindableSignal].
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding or closing
 */
class DefaultBindableSignal<T>(
    initial: Signal<T>? = null,
    private val takeOwnership: Boolean = false
) : BindableSignal<T> {

    private data class Data<T>(val signal: Signal<T>, val unSubscriber: UnSubscriber)

    private val listeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val closed = AtomicBoolean(false)
    private val data = AtomicReference<Data<T>?>(null)

    init {
        if (initial != null) {
            bindTo(initial)
        }
    }

    override val isClosed: Boolean
        get() = closed.get()

    override val value: T
        get() = currentSignalOrThrow().value

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            data.getAndSet(null)?.let { d ->
                try {
                    d.unSubscriber.invoke()
                } catch (_: Throwable) {
                }
                if (takeOwnership) {
                    try {
                        d.signal.close()
                    } catch (_: Throwable) {
                    }
                }
            }
            listeners.clear()
        }
    }

    override fun bindTo(newSignal: Signal<T>) {
        if (isClosed) return

        if (BindableSignal.wouldCreateCycle(this, newSignal)) {
            throw IllegalStateException("Circular binding detected: binding would create a cycle")
        }

        val unSub = newSignal.subscribe { either ->
            if (isClosed) return@subscribe
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { v -> notifyAllValue(listeners.toList(), v) }
            )
        }

        val old = data.getAndSet(Data(newSignal, unSub))
        old?.let { d ->
            try {
                d.unSubscriber.invoke()
            } catch (_: Throwable) {
            }
            if (takeOwnership && d.signal !== newSignal) {
                try {
                    d.signal.close()
                } catch (_: Throwable) {
                }
            }
        }

        if (!isClosed) {
            notifyAllValue(listeners.toList(), newSignal.value)
        }
    }

    override fun currentSignal(): Signal<T>? = data.get()?.signal

    override fun isBound(): Boolean = data.get() != null

    private fun currentSignalOrThrow(): Signal<T> =
        data.get()?.signal ?: throw IllegalStateException("BindableSignal is not bound")

    override fun toString(): String {
        val boundValue = try {
            value.toString()
        } catch (_: IllegalStateException) {
            "<not bound>"
        }
        return "DefaultBindableSignal(value=$boundValue, isClosed=$isClosed, isBound=${isBound()})"
    }
}
