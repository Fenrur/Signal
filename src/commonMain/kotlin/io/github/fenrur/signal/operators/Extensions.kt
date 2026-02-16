@file:JvmName("SignalExtensions")

package io.github.fenrur.signal.operators

import kotlin.jvm.JvmName
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.impl.*
import io.github.fenrur.signal.operators.filter
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.flatten
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.onEach
import io.github.fenrur.signal.operators.orDefault
import io.github.fenrur.signal.operators.withLatestFrom

// =============================================================================
// TUPLE DATA CLASSES (for zip with 4+ signals)
// =============================================================================

/**
 * Represents a tuple of four values.
 */
data class Tuple4<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Represents a tuple of five values.
 */
data class Tuple5<out A, out B, out C, out D, out E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * Represents a tuple of six values.
 */
data class Tuple6<out A, out B, out C, out D, out E, out F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

// =============================================================================
// TRANSFORMATION OPERATORS
// =============================================================================

/**
 * Transforms this signal's values using the given function.
 *
 * @param transform the transformation function
 * @return a new signal with transformed values
 */
fun <S, R> io.github.fenrur.signal.Signal<S>.map(transform: (S) -> R): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.MappedSignal(this, transform)

/**
 * Creates a bidirectionally-mapped [io.github.fenrur.signal.MutableSignal] with forward and reverse transforms.
 *
 * Reading applies [forward] to the source value, writing applies [reverse] before
 * setting the source. This allows creating a "lens" into a signal with a different type.
 *
 * Example:
 * ```kotlin
 * val stringSignal = mutableSignalOf("42")
 * val intSignal = stringSignal.bimap(
 *     forward = { it.toInt() },
 *     reverse = { it.toString() }
 * )
 * println(intSignal.value) // 42
 * intSignal.value = 100
 * println(stringSignal.value) // "100"
 * ```
 *
 * @param forward transforms source values to mapped values (read direction)
 * @param reverse transforms mapped values back to source values (write direction)
 * @return a new MutableSignal with bidirectional transformation
 */
fun <S, R> io.github.fenrur.signal.MutableSignal<S>.bimap(forward: (S) -> R, reverse: (R) -> S): io.github.fenrur.signal.MutableSignal<R> =
    io.github.fenrur.signal.impl.BimappedSignal(this, forward, reverse)

/**
 * Maps this signal's values to their string representation.
 */
fun <S> io.github.fenrur.signal.Signal<S>.mapToString(): io.github.fenrur.signal.Signal<String> = map { it.toString() }

/**
 * Maps values and filters out nulls.
 *
 * Note: Signals always have a value, so if the transform returns null,
 * the last non-null transformed value is retained.
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param transform transformation that may return null
 * @return signal of non-null transformed values
 */
fun <S, R : Any> io.github.fenrur.signal.Signal<S>.mapNotNull(transform: (S) -> R?): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.MapNotNullSignal(this, transform)

/**
 * Accumulates values over time using an accumulator function.
 *
 * Similar to Kotlin's `scan` or `runningFold`. Each emission includes
 * the accumulated result of applying the accumulator to all previous values.
 *
 * @param initial the initial accumulator value
 * @param accumulator function combining accumulator with each new value
 * @return signal of accumulated values
 */
fun <T, R> io.github.fenrur.signal.Signal<T>.scan(initial: R, accumulator: (acc: R, value: T) -> R): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.ScanSignal(this, initial, accumulator)

/**
 * Accumulates values starting from the signal's current value.
 *
 * @param accumulator function combining previous result with new value
 * @return signal of accumulated values
 */
fun <T> io.github.fenrur.signal.Signal<T>.runningReduce(accumulator: (acc: T, value: T) -> T): io.github.fenrur.signal.Signal<T> =
    io.github.fenrur.signal.impl.ScanSignal(this, value, accumulator)

/**
 * Emits pairs of consecutive values (previous, current).
 *
 * Useful for detecting changes or computing deltas.
 *
 * @return signal of consecutive value pairs
 */
fun <T> io.github.fenrur.signal.Signal<T>.pairwise(): io.github.fenrur.signal.Signal<Pair<T, T>> =
    io.github.fenrur.signal.impl.PairwiseSignal(this)

/**
 * Flattens a nested Signal<Signal<T>> to Signal<T>.
 *
 * When the outer signal changes, switches to the new inner signal.
 * Similar to RxJS switchMap or Kotlin's flatMapLatest.
 *
 * @return flattened signal
 */
fun <T> io.github.fenrur.signal.Signal<io.github.fenrur.signal.Signal<T>>.flatten(): io.github.fenrur.signal.Signal<T> =
    io.github.fenrur.signal.impl.FlattenSignal(this)

/**
 * Maps to an inner signal and flattens.
 *
 * Combines map and flatten. When source changes, maps to new signal
 * and switches to it, unsubscribing from the previous.
 *
 * @param transform function returning a signal for each source value
 * @return flattened mapped signal
 */
fun <S, R> io.github.fenrur.signal.Signal<S>.flatMap(transform: (S) -> io.github.fenrur.signal.Signal<R>): io.github.fenrur.signal.Signal<R> =
    map(transform).flatten()

/**
 * Alias for flatMap - switches to new signal on each emission.
 */
fun <S, R> io.github.fenrur.signal.Signal<S>.switchMap(transform: (S) -> io.github.fenrur.signal.Signal<R>): io.github.fenrur.signal.Signal<R> = flatMap(transform)

// =============================================================================
// FILTERING OPERATORS
// =============================================================================

/**
 * Filters values, only emitting those matching the predicate.
 *
 * Note: Signals always have a value, so if the current value doesn't match,
 * the last matching value is retained.
 * Uses lazy subscription to prevent memory leaks.
 *
 * @param predicate filter condition
 * @return filtered signal
 */
fun <T> io.github.fenrur.signal.Signal<T>.filter(predicate: (T) -> Boolean): io.github.fenrur.signal.Signal<T> =
    io.github.fenrur.signal.impl.FilteredSignal(this, predicate)

/**
 * Filters out null values.
 *
 * @return signal of non-null values
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> io.github.fenrur.signal.Signal<T?>.filterNotNull(): io.github.fenrur.signal.Signal<T> = filter { it != null } as io.github.fenrur.signal.Signal<T>

/**
 * Filters values by instance type.
 *
 * @return signal of values matching the type
 */
inline fun <reified R> io.github.fenrur.signal.Signal<*>.filterIsInstance(): io.github.fenrur.signal.Signal<R> {
    @Suppress("UNCHECKED_CAST")
    return filter { it is R } as io.github.fenrur.signal.Signal<R>
}

/**
 * Only emits when the selected key changes.
 *
 * @param keySelector function to extract comparison key
 * @return signal that only updates when key changes
 */
fun <T, K> io.github.fenrur.signal.Signal<T>.distinctUntilChangedBy(keySelector: (T) -> K): io.github.fenrur.signal.Signal<T> =
    io.github.fenrur.signal.impl.DistinctBySignal(this, keySelector)

/**
 * Signals already implement distinctUntilChanged, so this is a no-op.
 */
fun <T> io.github.fenrur.signal.Signal<T>.distinctUntilChanged(): io.github.fenrur.signal.Signal<T> = this

// =============================================================================
// COMBINATION OPERATORS
// =============================================================================

/**
 * Combines two signals using a transform function.
 */
fun <A, B, R> combine(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    transform: (A, B) -> R
): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.CombinedSignal2(sa, sb, transform)

/**
 * Combines three signals.
 */
fun <A, B, C, R> combine(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    transform: (A, B, C) -> R
): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.CombinedSignal3(sa, sb, sc, transform)

/**
 * Combines four signals.
 */
fun <A, B, C, D, R> combine(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>,
    transform: (A, B, C, D) -> R
): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.CombinedSignal4(sa, sb, sc, sd, transform)

/**
 * Combines five signals.
 */
fun <A, B, C, D, E, R> combine(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>,
    se: io.github.fenrur.signal.Signal<E>,
    transform: (A, B, C, D, E) -> R
): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.CombinedSignal5(sa, sb, sc, sd, se, transform)

/**
 * Combines six signals.
 */
fun <A, B, C, D, E, F, R> combine(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>,
    se: io.github.fenrur.signal.Signal<E>,
    sf: io.github.fenrur.signal.Signal<F>,
    transform: (A, B, C, D, E, F) -> R
): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.CombinedSignal6(sa, sb, sc, sd, se, sf, transform)

/**
 * Zips two signals into a Pair.
 */
fun <A, B> zip(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>
): io.github.fenrur.signal.Signal<Pair<A, B>> =
    io.github.fenrur.signal.operators.combine(sa, sb) { a, b -> a to b }

/**
 * Zips three signals into a Triple.
 */
fun <A, B, C> zip(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>
): io.github.fenrur.signal.Signal<Triple<A, B, C>> =
    io.github.fenrur.signal.operators.combine(sa, sb, sc) { a, b, c -> Triple(a, b, c) }

/**
 * Zips four signals into a Tuple4.
 */
fun <A, B, C, D> zip(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>
): io.github.fenrur.signal.Signal<io.github.fenrur.signal.operators.Tuple4<A, B, C, D>> =
    io.github.fenrur.signal.operators.combine(
        sa,
        sb,
        sc,
        sd
    ) { a, b, c, d -> io.github.fenrur.signal.operators.Tuple4(a, b, c, d) }

/**
 * Zips five signals into a Tuple5.
 */
fun <A, B, C, D, E> zip(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>,
    se: io.github.fenrur.signal.Signal<E>
): io.github.fenrur.signal.Signal<io.github.fenrur.signal.operators.Tuple5<A, B, C, D, E>> =
    io.github.fenrur.signal.operators.combine(
        sa,
        sb,
        sc,
        sd,
        se
    ) { a, b, c, d, e -> io.github.fenrur.signal.operators.Tuple5(a, b, c, d, e) }

/**
 * Zips six signals into a Tuple6.
 */
fun <A, B, C, D, E, F> zip(
    sa: io.github.fenrur.signal.Signal<A>,
    sb: io.github.fenrur.signal.Signal<B>,
    sc: io.github.fenrur.signal.Signal<C>,
    sd: io.github.fenrur.signal.Signal<D>,
    se: io.github.fenrur.signal.Signal<E>,
    sf: io.github.fenrur.signal.Signal<F>
): io.github.fenrur.signal.Signal<io.github.fenrur.signal.operators.Tuple6<A, B, C, D, E, F>> =
    io.github.fenrur.signal.operators.combine(
        sa,
        sb,
        sc,
        sd,
        se,
        sf
    ) { a, b, c, d, e, f -> io.github.fenrur.signal.operators.Tuple6(a, b, c, d, e, f) }

/**
 * Combines with latest value from another signal.
 *
 * Only emits when THIS signal changes, sampling the latest from other.
 *
 * @param other signal to sample from
 * @param combiner function to combine values
 * @return combined signal
 */
fun <A, B, R> io.github.fenrur.signal.Signal<A>.withLatestFrom(other: io.github.fenrur.signal.Signal<B>, combiner: (A, B) -> R): io.github.fenrur.signal.Signal<R> =
    io.github.fenrur.signal.impl.WithLatestFromSignal(this, other, combiner)

/**
 * Combines with latest value from another signal into a Pair.
 */
fun <A, B> io.github.fenrur.signal.Signal<A>.withLatestFrom(other: io.github.fenrur.signal.Signal<B>): io.github.fenrur.signal.Signal<Pair<A, B>> =
    withLatestFrom(other) { a, b -> a to b }

/**
 * Combines multiple signals into a list signal.
 */
fun <T> combineAll(vararg signals: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<List<T>> {
    if (signals.isEmpty()) return io.github.fenrur.signal.impl.ConstantSignal(emptyList())
    return signals.drop(1).fold(signals[0].map { listOf(it) }) { acc, signal ->
        io.github.fenrur.signal.operators.combine(acc, signal) { list, value -> list + value }
    }
}

/**
 * Combines a list of signals into a signal of list.
 */
fun <T> List<io.github.fenrur.signal.Signal<T>>.combineAll(): io.github.fenrur.signal.Signal<List<T>> =
    io.github.fenrur.signal.operators.combineAll(*toTypedArray())

// =============================================================================
// BOOLEAN OPERATORS
// =============================================================================

/**
 * Negates a boolean signal.
 */
fun io.github.fenrur.signal.Signal<Boolean>.not(): io.github.fenrur.signal.Signal<Boolean> = map { !it }

/**
 * Logical AND of two boolean signals.
 */
fun io.github.fenrur.signal.Signal<Boolean>.and(other: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a && b }

/**
 * Logical OR of two boolean signals.
 */
fun io.github.fenrur.signal.Signal<Boolean>.or(other: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a || b }

/**
 * Logical XOR of two boolean signals.
 */
fun io.github.fenrur.signal.Signal<Boolean>.xor(other: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a xor b }

/**
 * Returns true if all signals are true.
 */
fun allOf(vararg signals: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combineAll(*signals).map { it.all { v -> v } }

/**
 * Returns true if any signal is true.
 */
fun anyOf(vararg signals: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combineAll(*signals).map { it.any { v -> v } }

/**
 * Returns true if no signal is true.
 */
fun noneOf(vararg signals: io.github.fenrur.signal.Signal<Boolean>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combineAll(*signals).map { it.none { v -> v } }

// =============================================================================
// NUMERIC OPERATORS
// =============================================================================

/**
 * Adds two numeric signals.
 */
@JvmName("plusInt")
operator fun io.github.fenrur.signal.Signal<Int>.plus(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a + b }

@JvmName("plusLong")
operator fun io.github.fenrur.signal.Signal<Long>.plus(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a + b }

@JvmName("plusDouble")
operator fun io.github.fenrur.signal.Signal<Double>.plus(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a + b }

@JvmName("plusFloat")
operator fun io.github.fenrur.signal.Signal<Float>.plus(other: io.github.fenrur.signal.Signal<Float>): io.github.fenrur.signal.Signal<Float> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a + b }

/**
 * Subtracts two numeric signals.
 */
@JvmName("minusInt")
operator fun io.github.fenrur.signal.Signal<Int>.minus(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a - b }

@JvmName("minusLong")
operator fun io.github.fenrur.signal.Signal<Long>.minus(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a - b }

@JvmName("minusDouble")
operator fun io.github.fenrur.signal.Signal<Double>.minus(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a - b }

@JvmName("minusFloat")
operator fun io.github.fenrur.signal.Signal<Float>.minus(other: io.github.fenrur.signal.Signal<Float>): io.github.fenrur.signal.Signal<Float> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a - b }

/**
 * Multiplies two numeric signals.
 */
@JvmName("timesInt")
operator fun io.github.fenrur.signal.Signal<Int>.times(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a * b }

@JvmName("timesLong")
operator fun io.github.fenrur.signal.Signal<Long>.times(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a * b }

@JvmName("timesDouble")
operator fun io.github.fenrur.signal.Signal<Double>.times(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a * b }

@JvmName("timesFloat")
operator fun io.github.fenrur.signal.Signal<Float>.times(other: io.github.fenrur.signal.Signal<Float>): io.github.fenrur.signal.Signal<Float> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a * b }

/**
 * Divides two numeric signals.
 */
@JvmName("divInt")
operator fun io.github.fenrur.signal.Signal<Int>.div(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a / b }

@JvmName("divLong")
operator fun io.github.fenrur.signal.Signal<Long>.div(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a / b }

@JvmName("divDouble")
operator fun io.github.fenrur.signal.Signal<Double>.div(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a / b }

@JvmName("divFloat")
operator fun io.github.fenrur.signal.Signal<Float>.div(other: io.github.fenrur.signal.Signal<Float>): io.github.fenrur.signal.Signal<Float> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a / b }

/**
 * Remainder of two numeric signals.
 */
@JvmName("remInt")
operator fun io.github.fenrur.signal.Signal<Int>.rem(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a % b }

@JvmName("remLong")
operator fun io.github.fenrur.signal.Signal<Long>.rem(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a % b }

/**
 * Coerces value to be within a range.
 */
@JvmName("coerceInInt")
fun io.github.fenrur.signal.Signal<Int>.coerceIn(min: io.github.fenrur.signal.Signal<Int>, max: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

@JvmName("coerceInLong")
fun io.github.fenrur.signal.Signal<Long>.coerceIn(min: io.github.fenrur.signal.Signal<Long>, max: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

@JvmName("coerceInDouble")
fun io.github.fenrur.signal.Signal<Double>.coerceIn(min: io.github.fenrur.signal.Signal<Double>, max: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

/**
 * Coerces value to be at least a minimum.
 */
@JvmName("coerceAtLeastInt")
fun io.github.fenrur.signal.Signal<Int>.coerceAtLeast(min: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

@JvmName("coerceAtLeastInt2")
fun io.github.fenrur.signal.Signal<Int>.coerceAtLeast(min: Int): io.github.fenrur.signal.Signal<Int> = map { it.coerceAtLeast(min) }

@JvmName("coerceAtLeastLong")
fun io.github.fenrur.signal.Signal<Long>.coerceAtLeast(min: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

@JvmName("coerceAtLeastDouble")
fun io.github.fenrur.signal.Signal<Double>.coerceAtLeast(min: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

/**
 * Coerces value to be at most a maximum.
 */
@JvmName("coerceAtMostInt")
fun io.github.fenrur.signal.Signal<Int>.coerceAtMost(max: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Int> =
    io.github.fenrur.signal.operators.combine(this, max) { v, hi -> v.coerceAtMost(hi) }

@JvmName("coerceAtMostInt2")
fun io.github.fenrur.signal.Signal<Int>.coerceAtMost(max: Int): io.github.fenrur.signal.Signal<Int> = map { it.coerceAtMost(max) }

@JvmName("coerceAtMostLong")
fun io.github.fenrur.signal.Signal<Long>.coerceAtMost(max: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Long> =
    io.github.fenrur.signal.operators.combine(this, max) { v, hi -> v.coerceAtMost(hi) }

@JvmName("coerceAtMostDouble")
fun io.github.fenrur.signal.Signal<Double>.coerceAtMost(max: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Double> =
    io.github.fenrur.signal.operators.combine(this, max) { v, hi -> v.coerceAtMost(hi) }

// =============================================================================
// COMPARISON OPERATORS
// =============================================================================

/**
 * Returns true if this signal's value is greater than other's.
 */
@JvmName("gtInt")
infix fun io.github.fenrur.signal.Signal<Int>.gt(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a > b }

@JvmName("gtLong")
infix fun io.github.fenrur.signal.Signal<Long>.gt(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a > b }

@JvmName("gtDouble")
infix fun io.github.fenrur.signal.Signal<Double>.gt(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a > b }

/**
 * Returns true if this signal's value is less than other's.
 */
@JvmName("ltInt")
infix fun io.github.fenrur.signal.Signal<Int>.lt(other: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a < b }

@JvmName("ltLong")
infix fun io.github.fenrur.signal.Signal<Long>.lt(other: io.github.fenrur.signal.Signal<Long>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a < b }

@JvmName("ltDouble")
infix fun io.github.fenrur.signal.Signal<Double>.lt(other: io.github.fenrur.signal.Signal<Double>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a < b }

/**
 * Returns true if this signal's value equals other's.
 */
infix fun <T> io.github.fenrur.signal.Signal<T>.eq(other: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a == b }

/**
 * Returns true if this signal's value does not equal other's.
 */
infix fun <T> io.github.fenrur.signal.Signal<T>.neq(other: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a != b }

// =============================================================================
// STRING OPERATORS
// =============================================================================

/**
 * Concatenates two string signals.
 */
operator fun io.github.fenrur.signal.Signal<String>.plus(other: io.github.fenrur.signal.Signal<String>): io.github.fenrur.signal.Signal<String> =
    io.github.fenrur.signal.operators.combine(this, other) { a, b -> a + b }

/**
 * Returns true if the string is empty.
 */
@JvmName("isEmptyString")
fun io.github.fenrur.signal.Signal<String>.isEmpty(): io.github.fenrur.signal.Signal<Boolean> = map { it.isEmpty() }

/**
 * Returns true if the string is not empty.
 */
@JvmName("isNotEmptyString")
fun io.github.fenrur.signal.Signal<String>.isNotEmpty(): io.github.fenrur.signal.Signal<Boolean> = map { it.isNotEmpty() }

/**
 * Returns true if the string is blank (empty or whitespace only).
 */
fun io.github.fenrur.signal.Signal<String>.isBlank(): io.github.fenrur.signal.Signal<Boolean> = map { it.isBlank() }

/**
 * Returns true if the string is not blank.
 */
fun io.github.fenrur.signal.Signal<String>.isNotBlank(): io.github.fenrur.signal.Signal<Boolean> = map { it.isNotBlank() }

/**
 * Returns the length of the string.
 */
fun io.github.fenrur.signal.Signal<String>.length(): io.github.fenrur.signal.Signal<Int> = map { it.length }

/**
 * Trims the string.
 */
fun io.github.fenrur.signal.Signal<String>.trim(): io.github.fenrur.signal.Signal<String> = map { it.trim() }

/**
 * Converts to uppercase.
 */
fun io.github.fenrur.signal.Signal<String>.uppercase(): io.github.fenrur.signal.Signal<String> = map { it.uppercase() }

/**
 * Converts to lowercase.
 */
fun io.github.fenrur.signal.Signal<String>.lowercase(): io.github.fenrur.signal.Signal<String> = map { it.lowercase() }

// =============================================================================
// COLLECTION OPERATORS (for Signal<List<T>>)
// =============================================================================

/**
 * Returns the size of the list.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.size(): io.github.fenrur.signal.Signal<Int> = map { it.size }

/**
 * Returns true if the list is empty.
 */
@JvmName("isEmptyList")
fun <T> io.github.fenrur.signal.Signal<List<T>>.isEmpty(): io.github.fenrur.signal.Signal<Boolean> = map { it.isEmpty() }

/**
 * Returns true if the list is not empty.
 */
@JvmName("isNotEmptyList")
fun <T> io.github.fenrur.signal.Signal<List<T>>.isNotEmpty(): io.github.fenrur.signal.Signal<Boolean> = map { it.isNotEmpty() }

/**
 * Returns the first element or null.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.firstOrNull(): io.github.fenrur.signal.Signal<T?> = map { it.firstOrNull() }

/**
 * Returns the last element or null.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.lastOrNull(): io.github.fenrur.signal.Signal<T?> = map { it.lastOrNull() }

/**
 * Returns element at index or null.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.getOrNull(index: Int): io.github.fenrur.signal.Signal<T?> = map { it.getOrNull(index) }

/**
 * Returns element at index from a signal.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.getOrNull(index: io.github.fenrur.signal.Signal<Int>): io.github.fenrur.signal.Signal<T?> =
    io.github.fenrur.signal.operators.combine(this, index) { list, i -> list.getOrNull(i) }

/**
 * Returns true if list contains the element.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.contains(element: T): io.github.fenrur.signal.Signal<Boolean> = map { element in it }

/**
 * Returns true if list contains the element from another signal.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.contains(element: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<Boolean> =
    io.github.fenrur.signal.operators.combine(this, element) { list, e -> e in list }

/**
 * Filters the list.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.filterList(predicate: (T) -> Boolean): io.github.fenrur.signal.Signal<List<T>> =
    map { it.filter(predicate) }

/**
 * Maps the list elements.
 */
fun <T, R> io.github.fenrur.signal.Signal<List<T>>.mapList(transform: (T) -> R): io.github.fenrur.signal.Signal<List<R>> =
    map { it.map(transform) }

/**
 * FlatMaps the list elements.
 */
fun <T, R> io.github.fenrur.signal.Signal<List<T>>.flatMapList(transform: (T) -> Iterable<R>): io.github.fenrur.signal.Signal<List<R>> =
    map { it.flatMap(transform) }

/**
 * Sorts the list.
 */
fun <T : Comparable<T>> io.github.fenrur.signal.Signal<List<T>>.sorted(): io.github.fenrur.signal.Signal<List<T>> = map { it.sorted() }

/**
 * Sorts the list descending.
 */
fun <T : Comparable<T>> io.github.fenrur.signal.Signal<List<T>>.sortedDescending(): io.github.fenrur.signal.Signal<List<T>> =
    map { it.sortedDescending() }

/**
 * Sorts by a selector.
 */
fun <T, R : Comparable<R>> io.github.fenrur.signal.Signal<List<T>>.sortedBy(selector: (T) -> R): io.github.fenrur.signal.Signal<List<T>> =
    map { it.sortedBy(selector) }

/**
 * Reverses the list.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.reversed(): io.github.fenrur.signal.Signal<List<T>> = map { it.reversed() }

/**
 * Takes first n elements.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.take(n: Int): io.github.fenrur.signal.Signal<List<T>> = map { it.take(n) }

/**
 * Drops first n elements.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.drop(n: Int): io.github.fenrur.signal.Signal<List<T>> = map { it.drop(n) }

/**
 * Returns distinct elements.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.distinct(): io.github.fenrur.signal.Signal<List<T>> = map { it.distinct() }

/**
 * Joins elements to string.
 */
fun <T> io.github.fenrur.signal.Signal<List<T>>.joinToString(
    separator: String = ", ",
    prefix: String = "",
    postfix: String = ""
): io.github.fenrur.signal.Signal<String> = map { it.joinToString(separator, prefix, postfix) }

// =============================================================================
// UTILITY OPERATORS
// =============================================================================

/**
 * Provides a default value for nullable signals.
 */
fun <T : Any> io.github.fenrur.signal.Signal<T?>.orDefault(default: T): io.github.fenrur.signal.Signal<T> = map { it ?: default }

/**
 * Provides a default value from another signal for nullable signals.
 */
fun <T : Any> io.github.fenrur.signal.Signal<T?>.orDefault(default: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<T> =
    io.github.fenrur.signal.operators.combine(this, default) { value, def -> value ?: def }

/**
 * Uses value from fallback signal if this signal's value is null.
 */
fun <T : Any> io.github.fenrur.signal.Signal<T?>.orElse(fallback: io.github.fenrur.signal.Signal<T>): io.github.fenrur.signal.Signal<T> = orDefault(fallback)

/**
 * Executes a side effect for each value without modifying it.
 *
 * Useful for logging, debugging, or triggering external actions.
 *
 * @param action side effect to execute
 * @return the same signal value unchanged
 */
fun <T> io.github.fenrur.signal.Signal<T>.onEach(action: (T) -> Unit): io.github.fenrur.signal.Signal<T> = map {
    action(it)
    it
}

/**
 * Alias for onEach - taps into the signal for side effects.
 */
fun <T> io.github.fenrur.signal.Signal<T>.tap(action: (T) -> Unit): io.github.fenrur.signal.Signal<T> = onEach(action)

/**
 * Logs each value to console (for debugging).
 */
fun <T> io.github.fenrur.signal.Signal<T>.log(prefix: String = "Signal"): io.github.fenrur.signal.Signal<T> = onEach {
    println("$prefix: $it")
}

/**
 * Converts a nullable signal to an optional-style signal.
 * Returns true if value is present (non-null).
 */
fun <T : Any> io.github.fenrur.signal.Signal<T?>.isPresent(): io.github.fenrur.signal.Signal<Boolean> = map { it != null }

/**
 * Returns true if value is null.
 */
fun <T : Any> io.github.fenrur.signal.Signal<T?>.isAbsent(): io.github.fenrur.signal.Signal<Boolean> = map { it == null }

// =============================================================================
// MUTABLE SIGNAL OPERATORS
// =============================================================================

/**
 * Toggles a boolean signal.
 */
fun io.github.fenrur.signal.MutableSignal<Boolean>.toggle() {
    update { !it }
}

/**
 * Increments an integer signal.
 */
@JvmName("incrementInt")
fun io.github.fenrur.signal.MutableSignal<Int>.increment(by: Int = 1) {
    update { it + by }
}

/**
 * Decrements an integer signal.
 */
@JvmName("decrementInt")
fun io.github.fenrur.signal.MutableSignal<Int>.decrement(by: Int = 1) {
    update { it - by }
}

/**
 * Increments a long signal.
 */
@JvmName("incrementLong")
fun io.github.fenrur.signal.MutableSignal<Long>.increment(by: Long = 1L) {
    update { it + by }
}

/**
 * Decrements a long signal.
 */
@JvmName("decrementLong")
fun io.github.fenrur.signal.MutableSignal<Long>.decrement(by: Long = 1L) {
    update { it - by }
}

/**
 * Increments a double signal.
 */
@JvmName("incrementDouble")
fun io.github.fenrur.signal.MutableSignal<Double>.increment(by: Double = 1.0) {
    update { it + by }
}

/**
 * Decrements a double signal.
 */
@JvmName("decrementDouble")
fun io.github.fenrur.signal.MutableSignal<Double>.decrement(by: Double = 1.0) {
    update { it - by }
}

/**
 * Appends to a string signal.
 */
fun io.github.fenrur.signal.MutableSignal<String>.append(suffix: String) {
    update { it + suffix }
}

/**
 * Prepends to a string signal.
 */
fun io.github.fenrur.signal.MutableSignal<String>.prepend(prefix: String) {
    update { prefix + it }
}

/**
 * Clears a string signal.
 */
fun io.github.fenrur.signal.MutableSignal<String>.clear() {
    value = ""
}

/**
 * Adds element to a list signal.
 */
@JvmName("addToList")
fun <T> io.github.fenrur.signal.MutableSignal<List<T>>.add(element: T) {
    update { it + element }
}

/**
 * Adds all elements to a list signal.
 */
fun <T> io.github.fenrur.signal.MutableSignal<List<T>>.addAll(elements: Collection<T>) {
    update { it + elements }
}

/**
 * Removes element from a list signal.
 */
@JvmName("removeFromList")
fun <T> io.github.fenrur.signal.MutableSignal<List<T>>.remove(element: T) {
    update { it - element }
}

/**
 * Removes element at index from a list signal.
 */
fun <T> io.github.fenrur.signal.MutableSignal<List<T>>.removeAt(index: Int) {
    update { it.filterIndexed { i, _ -> i != index } }
}

/**
 * Clears a list signal.
 */
fun <T> io.github.fenrur.signal.MutableSignal<List<T>>.clearList() {
    value = emptyList()
}

/**
 * Adds element to a set signal.
 */
@JvmName("addToSet")
fun <T> io.github.fenrur.signal.MutableSignal<Set<T>>.add(element: T) {
    update { it + element }
}

/**
 * Removes element from a set signal.
 */
@JvmName("removeFromSet")
fun <T> io.github.fenrur.signal.MutableSignal<Set<T>>.remove(element: T) {
    update { it - element }
}

/**
 * Clears a set signal.
 */
fun <T> io.github.fenrur.signal.MutableSignal<Set<T>>.clearSet() {
    value = emptySet()
}

/**
 * Puts entry in a map signal.
 */
fun <K, V> io.github.fenrur.signal.MutableSignal<Map<K, V>>.put(key: K, value: V) {
    update { it + (key to value) }
}

/**
 * Removes key from a map signal.
 */
@JvmName("removeFromMap")
fun <K, V> io.github.fenrur.signal.MutableSignal<Map<K, V>>.remove(key: K) {
    update { it - key }
}

/**
 * Clears a map signal.
 */
fun <K, V> io.github.fenrur.signal.MutableSignal<Map<K, V>>.clearMap() {
    value = emptyMap()
}
