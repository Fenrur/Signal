package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.SubscribeListener
import com.github.fenrur.signal.UnSubscriber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A read-only [Signal] that combines two source signals.
 */
class CombinedSignal2<A, B, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val transform: (A, B) -> R
) : Signal<R> {

    private val ref = AtomicReference(transform(sa.value, sb.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubA: UnSubscriber
    private val unsubB: UnSubscriber

    init {
        unsubA = sa.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { va -> update(va, sb.value) }
            )
        }
        unsubB = sb.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vb -> update(sa.value, vb) }
            )
        }
    }

    private fun update(va: A, vb: B) {
        if (closed.get()) return
        val new = transform(va, vb)
        val old = ref.getAndSet(new)
        if (old != new) notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = ref.get()
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubA()
            unsubB()
            listeners.clear()
        }
    }

    override fun toString(): String = "CombinedSignal2(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines three source signals.
 */
class CombinedSignal3<A, B, C, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val transform: (A, B, C) -> R
) : Signal<R> {

    private val ref = AtomicReference(transform(sa.value, sb.value, sc.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubA: UnSubscriber
    private val unsubB: UnSubscriber
    private val unsubC: UnSubscriber

    init {
        unsubA = sa.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { va -> update(va, sb.value, sc.value) }
            )
        }
        unsubB = sb.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vb -> update(sa.value, vb, sc.value) }
            )
        }
        unsubC = sc.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vc -> update(sa.value, sb.value, vc) }
            )
        }
    }

    private fun update(va: A, vb: B, vc: C) {
        if (closed.get()) return
        val new = transform(va, vb, vc)
        val old = ref.getAndSet(new)
        if (old != new) notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = ref.get()
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubA()
            unsubB()
            unsubC()
            listeners.clear()
        }
    }

    override fun toString(): String = "CombinedSignal3(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines four source signals.
 */
class CombinedSignal4<A, B, C, D, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val transform: (A, B, C, D) -> R
) : Signal<R> {

    private val ref = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubA: UnSubscriber
    private val unsubB: UnSubscriber
    private val unsubC: UnSubscriber
    private val unsubD: UnSubscriber

    init {
        unsubA = sa.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { va -> update(va, sb.value, sc.value, sd.value) }
            )
        }
        unsubB = sb.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vb -> update(sa.value, vb, sc.value, sd.value) }
            )
        }
        unsubC = sc.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vc -> update(sa.value, sb.value, vc, sd.value) }
            )
        }
        unsubD = sd.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vd -> update(sa.value, sb.value, sc.value, vd) }
            )
        }
    }

    private fun update(va: A, vb: B, vc: C, vd: D) {
        if (closed.get()) return
        val new = transform(va, vb, vc, vd)
        val old = ref.getAndSet(new)
        if (old != new) notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = ref.get()
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubA()
            unsubB()
            unsubC()
            unsubD()
            listeners.clear()
        }
    }

    override fun toString(): String = "CombinedSignal4(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines five source signals.
 */
class CombinedSignal5<A, B, C, D, E, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val se: Signal<E>,
    private val transform: (A, B, C, D, E) -> R
) : Signal<R> {

    private val ref = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value, se.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubA: UnSubscriber
    private val unsubB: UnSubscriber
    private val unsubC: UnSubscriber
    private val unsubD: UnSubscriber
    private val unsubE: UnSubscriber

    init {
        unsubA = sa.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { va -> update(va, sb.value, sc.value, sd.value, se.value) }
            )
        }
        unsubB = sb.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vb -> update(sa.value, vb, sc.value, sd.value, se.value) }
            )
        }
        unsubC = sc.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vc -> update(sa.value, sb.value, vc, sd.value, se.value) }
            )
        }
        unsubD = sd.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vd -> update(sa.value, sb.value, sc.value, vd, se.value) }
            )
        }
        unsubE = se.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { ve -> update(sa.value, sb.value, sc.value, sd.value, ve) }
            )
        }
    }

    private fun update(va: A, vb: B, vc: C, vd: D, ve: E) {
        if (closed.get()) return
        val new = transform(va, vb, vc, vd, ve)
        val old = ref.getAndSet(new)
        if (old != new) notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = ref.get()
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubA()
            unsubB()
            unsubC()
            unsubD()
            unsubE()
            listeners.clear()
        }
    }

    override fun toString(): String = "CombinedSignal5(value=$value, isClosed=$isClosed)"
}

/**
 * A read-only [Signal] that combines six source signals.
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

    private val ref = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value, se.value, sf.value))
    private val listeners = CopyOnWriteArrayList<SubscribeListener<R>>()
    private val closed = AtomicBoolean(false)
    private val unsubA: UnSubscriber
    private val unsubB: UnSubscriber
    private val unsubC: UnSubscriber
    private val unsubD: UnSubscriber
    private val unsubE: UnSubscriber
    private val unsubF: UnSubscriber

    init {
        unsubA = sa.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { va -> update(va, sb.value, sc.value, sd.value, se.value, sf.value) }
            )
        }
        unsubB = sb.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vb -> update(sa.value, vb, sc.value, sd.value, se.value, sf.value) }
            )
        }
        unsubC = sc.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vc -> update(sa.value, sb.value, vc, sd.value, se.value, sf.value) }
            )
        }
        unsubD = sd.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vd -> update(sa.value, sb.value, sc.value, vd, se.value, sf.value) }
            )
        }
        unsubE = se.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { ve -> update(sa.value, sb.value, sc.value, sd.value, ve, sf.value) }
            )
        }
        unsubF = sf.subscribe { either ->
            either.fold(
                { ex -> notifyAllError(listeners.toList(), ex) },
                { vf -> update(sa.value, sb.value, sc.value, sd.value, se.value, vf) }
            )
        }
    }

    private fun update(va: A, vb: B, vc: C, vd: D, ve: E, vf: F) {
        if (closed.get()) return
        val new = transform(va, vb, vc, vd, ve, vf)
        val old = ref.getAndSet(new)
        if (old != new) notifyAllValue(listeners.toList(), new)
    }

    override val value: R get() = ref.get()
    override val isClosed: Boolean get() = closed.get()

    override fun subscribe(listener: SubscribeListener<R>): UnSubscriber {
        if (closed.get()) return {}
        listener(Either.Right(value))
        listeners += listener
        return { listeners -= listener }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            unsubA()
            unsubB()
            unsubC()
            unsubD()
            unsubE()
            unsubF()
            listeners.clear()
        }
    }

    override fun toString(): String = "CombinedSignal6(value=$value, isClosed=$isClosed)"
}
