package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import java.util.concurrent.atomic.AtomicReference

/**
 * Combines 2 signals into one using a transform function.
 */
class CombinedSignal2<A, B, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val transform: (A, B) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(sa, sb)

    private val lastVersions = AtomicReference(listOf(-1L, -1L))

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(sa.value, sb.value))

    init {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb)))
    }

    override fun computeValue(): R = transform(sa.value, sb.value)

    override fun hasSourcesChanged(): Boolean {
        val last = lastVersions.get()
        return getVersion(sa) != last[0] || getVersion(sb) != last[1]
    }

    override fun updateSourceVersions() {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb)))
    }

    override fun toString(): String = "CombinedSignal2(value=$value, isClosed=$isClosed)"
}

/**
 * Combines 3 signals into one using a transform function.
 */
class CombinedSignal3<A, B, C, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val transform: (A, B, C) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(sa, sb, sc)

    private val lastVersions = AtomicReference(listOf(-1L, -1L, -1L))

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(sa.value, sb.value, sc.value))

    init {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc)))
    }

    override fun computeValue(): R = transform(sa.value, sb.value, sc.value)

    override fun hasSourcesChanged(): Boolean {
        val last = lastVersions.get()
        return getVersion(sa) != last[0] || getVersion(sb) != last[1] || getVersion(sc) != last[2]
    }

    override fun updateSourceVersions() {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc)))
    }

    override fun toString(): String = "CombinedSignal3(value=$value, isClosed=$isClosed)"
}

/**
 * Combines 4 signals into one using a transform function.
 */
class CombinedSignal4<A, B, C, D, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val transform: (A, B, C, D) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(sa, sb, sc, sd)

    private val lastVersions = AtomicReference(listOf(-1L, -1L, -1L, -1L))

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value))

    init {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd)))
    }

    override fun computeValue(): R = transform(sa.value, sb.value, sc.value, sd.value)

    override fun hasSourcesChanged(): Boolean {
        val last = lastVersions.get()
        return getVersion(sa) != last[0] || getVersion(sb) != last[1] ||
               getVersion(sc) != last[2] || getVersion(sd) != last[3]
    }

    override fun updateSourceVersions() {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd)))
    }

    override fun toString(): String = "CombinedSignal4(value=$value, isClosed=$isClosed)"
}

/**
 * Combines 5 signals into one using a transform function.
 */
class CombinedSignal5<A, B, C, D, E, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val se: Signal<E>,
    private val transform: (A, B, C, D, E) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(sa, sb, sc, sd, se)

    private val lastVersions = AtomicReference(listOf(-1L, -1L, -1L, -1L, -1L))

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value, se.value))

    init {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd), getVersion(se)))
    }

    override fun computeValue(): R = transform(sa.value, sb.value, sc.value, sd.value, se.value)

    override fun hasSourcesChanged(): Boolean {
        val last = lastVersions.get()
        return getVersion(sa) != last[0] || getVersion(sb) != last[1] ||
               getVersion(sc) != last[2] || getVersion(sd) != last[3] || getVersion(se) != last[4]
    }

    override fun updateSourceVersions() {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd), getVersion(se)))
    }

    override fun toString(): String = "CombinedSignal5(value=$value, isClosed=$isClosed)"
}

/**
 * Combines 6 signals into one using a transform function.
 */
class CombinedSignal6<A, B, C, D, E, F, R>(
    private val sa: Signal<A>,
    private val sb: Signal<B>,
    private val sc: Signal<C>,
    private val sd: Signal<D>,
    private val se: Signal<E>,
    private val sf: Signal<F>,
    private val transform: (A, B, C, D, E, F) -> R
) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = listOf(sa, sb, sc, sd, se, sf)

    private val lastVersions = AtomicReference(listOf(-1L, -1L, -1L, -1L, -1L, -1L))

    override val cachedValue: AtomicReference<R> = AtomicReference(transform(sa.value, sb.value, sc.value, sd.value, se.value, sf.value))

    init {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd), getVersion(se), getVersion(sf)))
    }

    override fun computeValue(): R = transform(sa.value, sb.value, sc.value, sd.value, se.value, sf.value)

    override fun hasSourcesChanged(): Boolean {
        val last = lastVersions.get()
        return getVersion(sa) != last[0] || getVersion(sb) != last[1] ||
               getVersion(sc) != last[2] || getVersion(sd) != last[3] ||
               getVersion(se) != last[4] || getVersion(sf) != last[5]
    }

    override fun updateSourceVersions() {
        lastVersions.set(listOf(getVersion(sa), getVersion(sb), getVersion(sc), getVersion(sd), getVersion(se), getVersion(sf)))
    }

    override fun toString(): String = "CombinedSignal6(value=$value, isClosed=$isClosed)"
}

/**
 * A constant signal that never changes.
 */
class ConstantSignal<R>(value: R) : AbstractComputedSignal<R>() {

    override val sources: List<Signal<*>> = emptyList()

    override val cachedValue: AtomicReference<R> = AtomicReference(value)

    override fun computeValue(): R = cachedValue.get()

    override fun hasSourcesChanged(): Boolean = false

    override fun updateSourceVersions() {}

    override fun toString(): String = "ConstantSignal(value=$value, isClosed=$isClosed)"
}
