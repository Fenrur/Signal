package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A [MutableSignal] that binds to another MutableSignal.
 *
 * This signal acts as a proxy to another signal, allowing you to:
 * - Switch the underlying signal at runtime
 * - Optionally take ownership of bound signals (closing them when this signal closes)
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding or closing
 */
class BindedSignal<T>(
    initial: MutableSignal<T>? = null,
    private val takeOwnership: Boolean = false
) : MutableSignal<T> {

    private data class Data<T>(val signal: MutableSignal<T>, val unSubscriber: UnSubscriber)

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

    override var value: T
        get() = currentSignalOrThrow().value
        set(newValue) {
            if (isClosed) return
            currentSignalOrThrow().value = newValue
        }

    override fun update(transform: (T) -> T) {
        if (isClosed) return
        currentSignalOrThrow().update(transform)
    }

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

    /**
     * Binds this signal to a new underlying signal.
     *
     * - Unsubscribes from the previous signal
     * - If [takeOwnership] is true, closes the previous signal
     * - Subscribes to the new signal and notifies listeners
     *
     * @param newSignal the new signal to bind to
     */
    fun bindTo(newSignal: MutableSignal<T>) {
        if (isClosed) return

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

    /**
     * Returns the currently bound signal.
     *
     * @return the bound signal, or null if not bound
     */
    fun currentSignal(): MutableSignal<T>? = data.get()?.signal

    /**
     * Returns true if this signal is currently bound to another signal.
     */
    fun isBound(): Boolean = data.get() != null

    private fun currentSignalOrThrow(): MutableSignal<T> =
        data.get()?.signal ?: throw IllegalStateException("BindedSignal is not bound")

    override fun toString(): String {
        val boundValue = try {
            value.toString()
        } catch (_: IllegalStateException) {
            "<not bound>"
        }
        return "BindedSignal(value=$boundValue, isClosed=$isClosed, isBound=${isBound()})"
    }
}
