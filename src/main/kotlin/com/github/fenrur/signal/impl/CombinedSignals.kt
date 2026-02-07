package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A read-only [Signal] that combines two source signals.
 * Uses lazy subscription to prevent memory leaks.
 */
class CombinedSignal2<A, B, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val transform: (A, B) -> R
) : Signal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubA = AtomicReference<UnSubscriber> {}
    private val unsubB = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubA.set(sa.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { va -> notifyUpdate(va, sb.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubB.set(sb.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vb -> notifyUpdate(sa.value, vb) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubA.getAndSet {}.invoke()
            unsubB.getAndSet {}.invoke()
        }
    }

    private fun notifyUpdate(va: A, vb: B) {
        val new = transform(va, vb)
        notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = transform(sa.value, sb.value)
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                unsubA.getAndSet {}.invoke()
                unsubB.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "CombinedSignal2(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines three source signals.
 * Uses lazy subscription to prevent memory leaks.
 */
class CombinedSignal3<A, B, C, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val transform: (A, B, C) -> R
) : Signal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubA = AtomicReference<UnSubscriber> {}
    private val unsubB = AtomicReference<UnSubscriber> {}
    private val unsubC = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubA.set(sa.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { va -> notifyUpdate(va, sb.value, sc.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubB.set(sb.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vb -> notifyUpdate(sa.value, vb, sc.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubC.set(sc.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vc -> notifyUpdate(sa.value, sb.value, vc) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubA.getAndSet {}.invoke()
            unsubB.getAndSet {}.invoke()
            unsubC.getAndSet {}.invoke()
        }
    }

    private fun notifyUpdate(va: A, vb: B, vc: C) {
        val new = transform(va, vb, vc)
        notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = transform(sa.value, sb.value, sc.value)
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                unsubA.getAndSet {}.invoke()
                unsubB.getAndSet {}.invoke()
                unsubC.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "CombinedSignal3(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines four source signals.
 * Uses lazy subscription to prevent memory leaks.
 */
class CombinedSignal4<A, B, C, D, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val transform: (A, B, C, D) -> R
) : Signal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubA = AtomicReference<UnSubscriber> {}
    private val unsubB = AtomicReference<UnSubscriber> {}
    private val unsubC = AtomicReference<UnSubscriber> {}
    private val unsubD = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubA.set(sa.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { va -> notifyUpdate(va, sb.value, sc.value, sd.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubB.set(sb.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vb -> notifyUpdate(sa.value, vb, sc.value, sd.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubC.set(sc.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vc -> notifyUpdate(sa.value, sb.value, vc, sd.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubD.set(sd.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vd -> notifyUpdate(sa.value, sb.value, sc.value, vd) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubA.getAndSet {}.invoke()
            unsubB.getAndSet {}.invoke()
            unsubC.getAndSet {}.invoke()
            unsubD.getAndSet {}.invoke()
        }
    }

    private fun notifyUpdate(va: A, vb: B, vc: C, vd: D) {
        val new = transform(va, vb, vc, vd)
        notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = transform(sa.value, sb.value, sc.value, sd.value)
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                unsubA.getAndSet {}.invoke()
                unsubB.getAndSet {}.invoke()
                unsubC.getAndSet {}.invoke()
                unsubD.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "CombinedSignal4(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines five source signals.
 * Uses lazy subscription to prevent memory leaks.
 */
class CombinedSignal5<A, B, C, D, E, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val se: Signal<E>,
    private val transform: (A, B, C, D, E) -> R
) : Signal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubA = AtomicReference<UnSubscriber> {}
    private val unsubB = AtomicReference<UnSubscriber> {}
    private val unsubC = AtomicReference<UnSubscriber> {}
    private val unsubD = AtomicReference<UnSubscriber> {}
    private val unsubE = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubA.set(sa.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { va -> notifyUpdate(va, sb.value, sc.value, sd.value, se.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubB.set(sb.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vb -> notifyUpdate(sa.value, vb, sc.value, sd.value, se.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubC.set(sc.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vc -> notifyUpdate(sa.value, sb.value, vc, sd.value, se.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubD.set(sd.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vd -> notifyUpdate(sa.value, sb.value, sc.value, vd, se.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubE.set(se.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { ve -> notifyUpdate(sa.value, sb.value, sc.value, sd.value, ve) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubA.getAndSet {}.invoke()
            unsubB.getAndSet {}.invoke()
            unsubC.getAndSet {}.invoke()
            unsubD.getAndSet {}.invoke()
            unsubE.getAndSet {}.invoke()
        }
    }

    private fun notifyUpdate(va: A, vb: B, vc: C, vd: D, ve: E) {
        val new = transform(va, vb, vc, vd, ve)
        notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = transform(sa.value, sb.value, sc.value, sd.value, se.value)
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                unsubA.getAndSet {}.invoke()
                unsubB.getAndSet {}.invoke()
                unsubC.getAndSet {}.invoke()
                unsubD.getAndSet {}.invoke()
                unsubE.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "CombinedSignal5(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines six source signals.
 * Uses lazy subscription to prevent memory leaks.
 */
class CombinedSignal6<A, B, C, D, E, F, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val se: Signal<E>,
    private val sf: Signal<F>,
    private val transform: (A, B, C, D, E, F) -> R
) : Signal<R> {

    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val unsubA = AtomicReference<UnSubscriber> {}
    private val unsubB = AtomicReference<UnSubscriber> {}
    private val unsubC = AtomicReference<UnSubscriber> {}
    private val unsubD = AtomicReference<UnSubscriber> {}
    private val unsubE = AtomicReference<UnSubscriber> {}
    private val unsubF = AtomicReference<UnSubscriber> {}

    private fun ensureSubscribed() {
        if (subscribed.compareAndSet(false, true)) {
            unsubA.set(sa.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { va -> notifyUpdate(va, sb.value, sc.value, sd.value, se.value, sf.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubB.set(sb.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vb -> notifyUpdate(sa.value, vb, sc.value, sd.value, se.value, sf.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubC.set(sc.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vc -> notifyUpdate(sa.value, sb.value, vc, sd.value, se.value, sf.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubD.set(sd.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vd -> notifyUpdate(sa.value, sb.value, sc.value, vd, se.value, sf.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubE.set(se.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { ve -> notifyUpdate(sa.value, sb.value, sc.value, sd.value, ve, sf.value) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
            unsubF.set(sf.subscribe { result ->
                if (closed.get()) return@subscribe
                result.fold(
                    onSuccess = { vf -> notifyUpdate(sa.value, sb.value, sc.value, sd.value, se.value, vf) },
                    onFailure = { ex -> notifyAllError(listeners.toList(), ex) }
                )
            })
        }
    }

    private fun maybeUnsubscribe() {
        if (listeners.isEmpty() && subscribed.compareAndSet(true, false)) {
            unsubA.getAndSet {}.invoke()
            unsubB.getAndSet {}.invoke()
            unsubC.getAndSet {}.invoke()
            unsubD.getAndSet {}.invoke()
            unsubE.getAndSet {}.invoke()
            unsubF.getAndSet {}.invoke()
        }
    }

    private fun notifyUpdate(va: A, vb: B, vc: C, vd: D, ve: E, vf: F) {
        val new = transform(va, vb, vc, vd, ve, vf)
        notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = transform(sa.value, sb.value, sc.value, sd.value, se.value, sf.value)
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        ensureSubscribed()
        listener(Result.success(value))
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
                unsubA.getAndSet {}.invoke()
                unsubB.getAndSet {}.invoke()
                unsubC.getAndSet {}.invoke()
                unsubD.getAndSet {}.invoke()
                unsubE.getAndSet {}.invoke()
                unsubF.getAndSet {}.invoke()
            }
        }
    }

    override fun toString(): String = "CombinedSignal6(value=$value, isClosed=$isClosed)"
}
