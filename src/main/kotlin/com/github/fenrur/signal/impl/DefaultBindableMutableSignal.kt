package com.github.fenrur.signal.impl

import com.github.fenrur.signal.BindableMutableSignal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Default implementation of [BindableMutableSignal].
 *
 * Thread-safety: All operations are thread-safe.
 *
 * @param T the type of value held by the signal
 * @param initial optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding or closing
 */
class DefaultBindableMutableSignal<T>(
    initial: MutableSignal<T>? = null,
    private val takeOwnership: Boolean = false
) : BindableMutableSignal<T> {

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
        listener(Result.success(value))
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

    override fun bindTo(newSignal: MutableSignal<T>) {
        if (isClosed) return

        if (BindableMutableSignal.wouldCreateCycle(this, newSignal)) {
            throw IllegalStateException("Circular binding detected: binding would create a cycle")
        }

        val unSub = newSignal.subscribe { result ->
            if (isClosed) return@subscribe
            result.fold(
                onSuccess = { v -> notifyAllValue(listeners.toList(), v) },
                onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
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

    override fun currentSignal(): MutableSignal<T>? = data.get()?.signal

    override fun isBound(): Boolean = data.get() != null

    private fun currentSignalOrThrow(): MutableSignal<T> =
        data.get()?.signal ?: throw IllegalStateException("BindableMutableSignal is not bound")

    override fun toString(): String {
        val boundValue = try {
            value.toString()
        } catch (_: IllegalStateException) {
            "<not bound>"
        }
        return "DefaultBindableMutableSignal(value=$boundValue, isClosed=$isClosed, isBound=${isBound()})"
    }
}
