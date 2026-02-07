@file:JvmName("SignalExtensions")

package com.github.fenrur.signal.operators

import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.impl.*

// =============================================================================
// TRANSFORMATION OPERATORS
// =============================================================================

/**
 * Transforms this signal's values using the given function.
 *
 * @param transform the transformation function
 * @return a new signal with transformed values
 */
fun <S, R> Signal<S>.map(transform: (S) -> R): Signal<R> = MappedSignal(this, transform)

/**
 * Creates a bidirectionally-mapped [MutableSignal] with forward and reverse transforms.
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
fun <S, R> MutableSignal<S>.bimap(forward: (S) -> R, reverse: (R) -> S): MutableSignal<R> =
    BimappedSignal(this, forward, reverse)

/**
 * Maps this signal's values to their string representation.
 */
fun <S> Signal<S>.mapToString(): Signal<String> = map { it.toString() }

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
fun <S, R : Any> Signal<S>.mapNotNull(transform: (S) -> R?): Signal<R> =
    MapNotNullSignal(this, transform)

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
fun <T, R> Signal<T>.scan(initial: R, accumulator: (acc: R, value: T) -> R): Signal<R> =
    ScanSignal(this, initial, accumulator)

/**
 * Accumulates values starting from the signal's current value.
 *
 * @param accumulator function combining previous result with new value
 * @return signal of accumulated values
 */
fun <T> Signal<T>.runningReduce(accumulator: (acc: T, value: T) -> T): Signal<T> =
    ScanSignal(this, value, accumulator)

/**
 * Emits pairs of consecutive values (previous, current).
 *
 * Useful for detecting changes or computing deltas.
 *
 * @return signal of consecutive value pairs
 */
fun <T> Signal<T>.pairwise(): Signal<Pair<T, T>> = PairwiseSignal(this)

/**
 * Flattens a nested Signal<Signal<T>> to Signal<T>.
 *
 * When the outer signal changes, switches to the new inner signal.
 * Similar to RxJS switchMap or Kotlin's flatMapLatest.
 *
 * @return flattened signal
 */
fun <T> Signal<Signal<T>>.flatten(): Signal<T> = FlattenSignal(this)

/**
 * Maps to an inner signal and flattens.
 *
 * Combines map and flatten. When source changes, maps to new signal
 * and switches to it, unsubscribing from the previous.
 *
 * @param transform function returning a signal for each source value
 * @return flattened mapped signal
 */
fun <S, R> Signal<S>.flatMap(transform: (S) -> Signal<R>): Signal<R> =
    map(transform).flatten()

/**
 * Alias for flatMap - switches to new signal on each emission.
 */
fun <S, R> Signal<S>.switchMap(transform: (S) -> Signal<R>): Signal<R> = flatMap(transform)

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
fun <T> Signal<T>.filter(predicate: (T) -> Boolean): Signal<T> =
    FilteredSignal(this, predicate)

/**
 * Filters out null values.
 *
 * @return signal of non-null values
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Signal<T?>.filterNotNull(): Signal<T> = filter { it != null } as Signal<T>

/**
 * Filters values by instance type.
 *
 * @return signal of values matching the type
 */
inline fun <reified R> Signal<*>.filterIsInstance(): Signal<R> {
    @Suppress("UNCHECKED_CAST")
    return filter { it is R } as Signal<R>
}

/**
 * Only emits when the selected key changes.
 *
 * @param keySelector function to extract comparison key
 * @return signal that only updates when key changes
 */
fun <T, K> Signal<T>.distinctUntilChangedBy(keySelector: (T) -> K): Signal<T> =
    DistinctBySignal(this, keySelector)

/**
 * Signals already implement distinctUntilChanged, so this is a no-op.
 */
fun <T> Signal<T>.distinctUntilChanged(): Signal<T> = this

// =============================================================================
// COMBINATION OPERATORS
// =============================================================================

/**
 * Combines two signals using a transform function.
 */
fun <A, B, R> combine(
    sa: Signal<A>,
    sb: Signal<B>,
    transform: (A, B) -> R
): Signal<R> = CombinedSignal2(sa, sb, transform)

/**
 * Combines three signals.
 */
fun <A, B, C, R> combine(
    sa: Signal<A>,
    sb: Signal<B>,
    sc: Signal<C>,
    transform: (A, B, C) -> R
): Signal<R> = CombinedSignal3(sa, sb, sc, transform)

/**
 * Combines four signals.
 */
fun <A, B, C, D, R> combine(
    sa: Signal<A>,
    sb: Signal<B>,
    sc: Signal<C>,
    sd: Signal<D>,
    transform: (A, B, C, D) -> R
): Signal<R> = CombinedSignal4(sa, sb, sc, sd, transform)

/**
 * Combines five signals.
 */
fun <A, B, C, D, E, R> combine(
    sa: Signal<A>,
    sb: Signal<B>,
    sc: Signal<C>,
    sd: Signal<D>,
    se: Signal<E>,
    transform: (A, B, C, D, E) -> R
): Signal<R> = CombinedSignal5(sa, sb, sc, sd, se, transform)

/**
 * Combines six signals.
 */
fun <A, B, C, D, E, F, R> combine(
    sa: Signal<A>,
    sb: Signal<B>,
    sc: Signal<C>,
    sd: Signal<D>,
    se: Signal<E>,
    sf: Signal<F>,
    transform: (A, B, C, D, E, F) -> R
): Signal<R> = CombinedSignal6(sa, sb, sc, sd, se, sf, transform)

/**
 * Combines with another signal into a Pair.
 */
fun <A, B> Signal<A>.zip(other: Signal<B>): Signal<Pair<A, B>> =
    combine(this, other) { a, b -> a to b }

/**
 * Combines with two other signals into a Triple.
 */
fun <A, B, C> Signal<A>.zip(second: Signal<B>, third: Signal<C>): Signal<Triple<A, B, C>> =
    combine(this, second, third) { a, b, c -> Triple(a, b, c) }

/**
 * Combines with latest value from another signal.
 *
 * Only emits when THIS signal changes, sampling the latest from other.
 *
 * @param other signal to sample from
 * @param combiner function to combine values
 * @return combined signal
 */
fun <A, B, R> Signal<A>.withLatestFrom(other: Signal<B>, combiner: (A, B) -> R): Signal<R> =
    WithLatestFromSignal(this, other, combiner)

/**
 * Combines with latest value from another signal into a Pair.
 */
fun <A, B> Signal<A>.withLatestFrom(other: Signal<B>): Signal<Pair<A, B>> =
    withLatestFrom(other) { a, b -> a to b }

/**
 * Combines multiple signals into a list signal.
 */
fun <T> combineAll(vararg signals: Signal<T>): Signal<List<T>> {
    if (signals.isEmpty()) return MappedSignal(CowMutableSignal(Unit)) { emptyList() }
    return signals.drop(1).fold(signals[0].map { listOf(it) }) { acc, signal ->
        combine(acc, signal) { list, value -> list + value }
    }
}

/**
 * Combines a list of signals into a signal of list.
 */
fun <T> List<Signal<T>>.combineAll(): Signal<List<T>> = combineAll(*toTypedArray())

// =============================================================================
// BOOLEAN OPERATORS
// =============================================================================

/**
 * Negates a boolean signal.
 */
fun Signal<Boolean>.not(): Signal<Boolean> = map { !it }

/**
 * Logical AND of two boolean signals.
 */
fun Signal<Boolean>.and(other: Signal<Boolean>): Signal<Boolean> =
    combine(this, other) { a, b -> a && b }

/**
 * Logical OR of two boolean signals.
 */
fun Signal<Boolean>.or(other: Signal<Boolean>): Signal<Boolean> =
    combine(this, other) { a, b -> a || b }

/**
 * Logical XOR of two boolean signals.
 */
fun Signal<Boolean>.xor(other: Signal<Boolean>): Signal<Boolean> =
    combine(this, other) { a, b -> a xor b }

/**
 * Returns true if all signals are true.
 */
fun allOf(vararg signals: Signal<Boolean>): Signal<Boolean> =
    combineAll(*signals).map { it.all { v -> v } }

/**
 * Returns true if any signal is true.
 */
fun anyOf(vararg signals: Signal<Boolean>): Signal<Boolean> =
    combineAll(*signals).map { it.any { v -> v } }

/**
 * Returns true if no signal is true.
 */
fun noneOf(vararg signals: Signal<Boolean>): Signal<Boolean> =
    combineAll(*signals).map { it.none { v -> v } }

// =============================================================================
// NUMERIC OPERATORS
// =============================================================================

/**
 * Adds two numeric signals.
 */
@JvmName("plusInt")
operator fun Signal<Int>.plus(other: Signal<Int>): Signal<Int> =
    combine(this, other) { a, b -> a + b }

@JvmName("plusLong")
operator fun Signal<Long>.plus(other: Signal<Long>): Signal<Long> =
    combine(this, other) { a, b -> a + b }

@JvmName("plusDouble")
operator fun Signal<Double>.plus(other: Signal<Double>): Signal<Double> =
    combine(this, other) { a, b -> a + b }

@JvmName("plusFloat")
operator fun Signal<Float>.plus(other: Signal<Float>): Signal<Float> =
    combine(this, other) { a, b -> a + b }

/**
 * Subtracts two numeric signals.
 */
@JvmName("minusInt")
operator fun Signal<Int>.minus(other: Signal<Int>): Signal<Int> =
    combine(this, other) { a, b -> a - b }

@JvmName("minusLong")
operator fun Signal<Long>.minus(other: Signal<Long>): Signal<Long> =
    combine(this, other) { a, b -> a - b }

@JvmName("minusDouble")
operator fun Signal<Double>.minus(other: Signal<Double>): Signal<Double> =
    combine(this, other) { a, b -> a - b }

@JvmName("minusFloat")
operator fun Signal<Float>.minus(other: Signal<Float>): Signal<Float> =
    combine(this, other) { a, b -> a - b }

/**
 * Multiplies two numeric signals.
 */
@JvmName("timesInt")
operator fun Signal<Int>.times(other: Signal<Int>): Signal<Int> =
    combine(this, other) { a, b -> a * b }

@JvmName("timesLong")
operator fun Signal<Long>.times(other: Signal<Long>): Signal<Long> =
    combine(this, other) { a, b -> a * b }

@JvmName("timesDouble")
operator fun Signal<Double>.times(other: Signal<Double>): Signal<Double> =
    combine(this, other) { a, b -> a * b }

@JvmName("timesFloat")
operator fun Signal<Float>.times(other: Signal<Float>): Signal<Float> =
    combine(this, other) { a, b -> a * b }

/**
 * Divides two numeric signals.
 */
@JvmName("divInt")
operator fun Signal<Int>.div(other: Signal<Int>): Signal<Int> =
    combine(this, other) { a, b -> a / b }

@JvmName("divLong")
operator fun Signal<Long>.div(other: Signal<Long>): Signal<Long> =
    combine(this, other) { a, b -> a / b }

@JvmName("divDouble")
operator fun Signal<Double>.div(other: Signal<Double>): Signal<Double> =
    combine(this, other) { a, b -> a / b }

@JvmName("divFloat")
operator fun Signal<Float>.div(other: Signal<Float>): Signal<Float> =
    combine(this, other) { a, b -> a / b }

/**
 * Remainder of two numeric signals.
 */
@JvmName("remInt")
operator fun Signal<Int>.rem(other: Signal<Int>): Signal<Int> =
    combine(this, other) { a, b -> a % b }

@JvmName("remLong")
operator fun Signal<Long>.rem(other: Signal<Long>): Signal<Long> =
    combine(this, other) { a, b -> a % b }

/**
 * Coerces value to be within a range.
 */
@JvmName("coerceInInt")
fun Signal<Int>.coerceIn(min: Signal<Int>, max: Signal<Int>): Signal<Int> =
    combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

@JvmName("coerceInLong")
fun Signal<Long>.coerceIn(min: Signal<Long>, max: Signal<Long>): Signal<Long> =
    combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

@JvmName("coerceInDouble")
fun Signal<Double>.coerceIn(min: Signal<Double>, max: Signal<Double>): Signal<Double> =
    combine(this, min, max) { v, lo, hi -> v.coerceIn(lo, hi) }

/**
 * Coerces value to be at least a minimum.
 */
@JvmName("coerceAtLeastInt")
fun Signal<Int>.coerceAtLeast(min: Signal<Int>): Signal<Int> =
    combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

@JvmName("coerceAtLeastInt2")
fun Signal<Int>.coerceAtLeast(min: Int): Signal<Int> = map { it.coerceAtLeast(min) }

@JvmName("coerceAtLeastLong")
fun Signal<Long>.coerceAtLeast(min: Signal<Long>): Signal<Long> =
    combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

@JvmName("coerceAtLeastDouble")
fun Signal<Double>.coerceAtLeast(min: Signal<Double>): Signal<Double> =
    combine(this, min) { v, lo -> v.coerceAtLeast(lo) }

/**
 * Coerces value to be at most a maximum.
 */
@JvmName("coerceAtMostInt")
fun Signal<Int>.coerceAtMost(max: Signal<Int>): Signal<Int> =
    combine(this, max) { v, hi -> v.coerceAtMost(hi) }

@JvmName("coerceAtMostInt2")
fun Signal<Int>.coerceAtMost(max: Int): Signal<Int> = map { it.coerceAtMost(max) }

@JvmName("coerceAtMostLong")
fun Signal<Long>.coerceAtMost(max: Signal<Long>): Signal<Long> =
    combine(this, max) { v, hi -> v.coerceAtMost(hi) }

@JvmName("coerceAtMostDouble")
fun Signal<Double>.coerceAtMost(max: Signal<Double>): Signal<Double> =
    combine(this, max) { v, hi -> v.coerceAtMost(hi) }

// =============================================================================
// COMPARISON OPERATORS
// =============================================================================

/**
 * Returns true if this signal's value is greater than other's.
 */
@JvmName("gtInt")
infix fun Signal<Int>.gt(other: Signal<Int>): Signal<Boolean> =
    combine(this, other) { a, b -> a > b }

@JvmName("gtLong")
infix fun Signal<Long>.gt(other: Signal<Long>): Signal<Boolean> =
    combine(this, other) { a, b -> a > b }

@JvmName("gtDouble")
infix fun Signal<Double>.gt(other: Signal<Double>): Signal<Boolean> =
    combine(this, other) { a, b -> a > b }

/**
 * Returns true if this signal's value is less than other's.
 */
@JvmName("ltInt")
infix fun Signal<Int>.lt(other: Signal<Int>): Signal<Boolean> =
    combine(this, other) { a, b -> a < b }

@JvmName("ltLong")
infix fun Signal<Long>.lt(other: Signal<Long>): Signal<Boolean> =
    combine(this, other) { a, b -> a < b }

@JvmName("ltDouble")
infix fun Signal<Double>.lt(other: Signal<Double>): Signal<Boolean> =
    combine(this, other) { a, b -> a < b }

/**
 * Returns true if this signal's value equals other's.
 */
infix fun <T> Signal<T>.eq(other: Signal<T>): Signal<Boolean> =
    combine(this, other) { a, b -> a == b }

/**
 * Returns true if this signal's value does not equal other's.
 */
infix fun <T> Signal<T>.neq(other: Signal<T>): Signal<Boolean> =
    combine(this, other) { a, b -> a != b }

// =============================================================================
// STRING OPERATORS
// =============================================================================

/**
 * Concatenates two string signals.
 */
operator fun Signal<String>.plus(other: Signal<String>): Signal<String> =
    combine(this, other) { a, b -> a + b }

/**
 * Returns true if the string is empty.
 */
@JvmName("isEmptyString")
fun Signal<String>.isEmpty(): Signal<Boolean> = map { it.isEmpty() }

/**
 * Returns true if the string is not empty.
 */
@JvmName("isNotEmptyString")
fun Signal<String>.isNotEmpty(): Signal<Boolean> = map { it.isNotEmpty() }

/**
 * Returns true if the string is blank (empty or whitespace only).
 */
fun Signal<String>.isBlank(): Signal<Boolean> = map { it.isBlank() }

/**
 * Returns true if the string is not blank.
 */
fun Signal<String>.isNotBlank(): Signal<Boolean> = map { it.isNotBlank() }

/**
 * Returns the length of the string.
 */
fun Signal<String>.length(): Signal<Int> = map { it.length }

/**
 * Trims the string.
 */
fun Signal<String>.trim(): Signal<String> = map { it.trim() }

/**
 * Converts to uppercase.
 */
fun Signal<String>.uppercase(): Signal<String> = map { it.uppercase() }

/**
 * Converts to lowercase.
 */
fun Signal<String>.lowercase(): Signal<String> = map { it.lowercase() }

// =============================================================================
// COLLECTION OPERATORS (for Signal<List<T>>)
// =============================================================================

/**
 * Returns the size of the list.
 */
fun <T> Signal<List<T>>.size(): Signal<Int> = map { it.size }

/**
 * Returns true if the list is empty.
 */
@JvmName("isEmptyList")
fun <T> Signal<List<T>>.isEmpty(): Signal<Boolean> = map { it.isEmpty() }

/**
 * Returns true if the list is not empty.
 */
@JvmName("isNotEmptyList")
fun <T> Signal<List<T>>.isNotEmpty(): Signal<Boolean> = map { it.isNotEmpty() }

/**
 * Returns the first element or null.
 */
fun <T> Signal<List<T>>.firstOrNull(): Signal<T?> = map { it.firstOrNull() }

/**
 * Returns the last element or null.
 */
fun <T> Signal<List<T>>.lastOrNull(): Signal<T?> = map { it.lastOrNull() }

/**
 * Returns element at index or null.
 */
fun <T> Signal<List<T>>.getOrNull(index: Int): Signal<T?> = map { it.getOrNull(index) }

/**
 * Returns element at index from a signal.
 */
fun <T> Signal<List<T>>.getOrNull(index: Signal<Int>): Signal<T?> =
    combine(this, index) { list, i -> list.getOrNull(i) }

/**
 * Returns true if list contains the element.
 */
fun <T> Signal<List<T>>.contains(element: T): Signal<Boolean> = map { element in it }

/**
 * Returns true if list contains the element from another signal.
 */
fun <T> Signal<List<T>>.contains(element: Signal<T>): Signal<Boolean> =
    combine(this, element) { list, e -> e in list }

/**
 * Filters the list.
 */
fun <T> Signal<List<T>>.filterList(predicate: (T) -> Boolean): Signal<List<T>> =
    map { it.filter(predicate) }

/**
 * Maps the list elements.
 */
fun <T, R> Signal<List<T>>.mapList(transform: (T) -> R): Signal<List<R>> =
    map { it.map(transform) }

/**
 * FlatMaps the list elements.
 */
fun <T, R> Signal<List<T>>.flatMapList(transform: (T) -> Iterable<R>): Signal<List<R>> =
    map { it.flatMap(transform) }

/**
 * Sorts the list.
 */
fun <T : Comparable<T>> Signal<List<T>>.sorted(): Signal<List<T>> = map { it.sorted() }

/**
 * Sorts the list descending.
 */
fun <T : Comparable<T>> Signal<List<T>>.sortedDescending(): Signal<List<T>> =
    map { it.sortedDescending() }

/**
 * Sorts by a selector.
 */
fun <T, R : Comparable<R>> Signal<List<T>>.sortedBy(selector: (T) -> R): Signal<List<T>> =
    map { it.sortedBy(selector) }

/**
 * Reverses the list.
 */
fun <T> Signal<List<T>>.reversed(): Signal<List<T>> = map { it.reversed() }

/**
 * Takes first n elements.
 */
fun <T> Signal<List<T>>.take(n: Int): Signal<List<T>> = map { it.take(n) }

/**
 * Drops first n elements.
 */
fun <T> Signal<List<T>>.drop(n: Int): Signal<List<T>> = map { it.drop(n) }

/**
 * Returns distinct elements.
 */
fun <T> Signal<List<T>>.distinct(): Signal<List<T>> = map { it.distinct() }

/**
 * Joins elements to string.
 */
fun <T> Signal<List<T>>.joinToString(
    separator: String = ", ",
    prefix: String = "",
    postfix: String = ""
): Signal<String> = map { it.joinToString(separator, prefix, postfix) }

// =============================================================================
// UTILITY OPERATORS
// =============================================================================

/**
 * Provides a default value for nullable signals.
 */
fun <T : Any> Signal<T?>.orDefault(default: T): Signal<T> = map { it ?: default }

/**
 * Provides a default value from another signal for nullable signals.
 */
fun <T : Any> Signal<T?>.orDefault(default: Signal<T>): Signal<T> =
    combine(this, default) { value, def -> value ?: def }

/**
 * Uses value from fallback signal if this signal's value is null.
 */
fun <T : Any> Signal<T?>.orElse(fallback: Signal<T>): Signal<T> = orDefault(fallback)

/**
 * Executes a side effect for each value without modifying it.
 *
 * Useful for logging, debugging, or triggering external actions.
 *
 * @param action side effect to execute
 * @return the same signal value unchanged
 */
fun <T> Signal<T>.onEach(action: (T) -> Unit): Signal<T> = map {
    action(it)
    it
}

/**
 * Alias for onEach - taps into the signal for side effects.
 */
fun <T> Signal<T>.tap(action: (T) -> Unit): Signal<T> = onEach(action)

/**
 * Logs each value to console (for debugging).
 */
fun <T> Signal<T>.log(prefix: String = "Signal"): Signal<T> = onEach {
    println("$prefix: $it")
}

/**
 * Converts a nullable signal to an optional-style signal.
 * Returns true if value is present (non-null).
 */
fun <T : Any> Signal<T?>.isPresent(): Signal<Boolean> = map { it != null }

/**
 * Returns true if value is null.
 */
fun <T : Any> Signal<T?>.isAbsent(): Signal<Boolean> = map { it == null }

// =============================================================================
// MUTABLE SIGNAL OPERATORS
// =============================================================================

/**
 * Toggles a boolean signal.
 */
fun MutableSignal<Boolean>.toggle() {
    update { !it }
}

/**
 * Increments an integer signal.
 */
@JvmName("incrementInt")
fun MutableSignal<Int>.increment(by: Int = 1) {
    update { it + by }
}

/**
 * Decrements an integer signal.
 */
@JvmName("decrementInt")
fun MutableSignal<Int>.decrement(by: Int = 1) {
    update { it - by }
}

/**
 * Increments a long signal.
 */
@JvmName("incrementLong")
fun MutableSignal<Long>.increment(by: Long = 1L) {
    update { it + by }
}

/**
 * Decrements a long signal.
 */
@JvmName("decrementLong")
fun MutableSignal<Long>.decrement(by: Long = 1L) {
    update { it - by }
}

/**
 * Increments a double signal.
 */
@JvmName("incrementDouble")
fun MutableSignal<Double>.increment(by: Double = 1.0) {
    update { it + by }
}

/**
 * Decrements a double signal.
 */
@JvmName("decrementDouble")
fun MutableSignal<Double>.decrement(by: Double = 1.0) {
    update { it - by }
}

/**
 * Appends to a string signal.
 */
fun MutableSignal<String>.append(suffix: String) {
    update { it + suffix }
}

/**
 * Prepends to a string signal.
 */
fun MutableSignal<String>.prepend(prefix: String) {
    update { prefix + it }
}

/**
 * Clears a string signal.
 */
fun MutableSignal<String>.clear() {
    value = ""
}

/**
 * Adds element to a list signal.
 */
@JvmName("addToList")
fun <T> MutableSignal<List<T>>.add(element: T) {
    update { it + element }
}

/**
 * Adds all elements to a list signal.
 */
fun <T> MutableSignal<List<T>>.addAll(elements: Collection<T>) {
    update { it + elements }
}

/**
 * Removes element from a list signal.
 */
@JvmName("removeFromList")
fun <T> MutableSignal<List<T>>.remove(element: T) {
    update { it - element }
}

/**
 * Removes element at index from a list signal.
 */
fun <T> MutableSignal<List<T>>.removeAt(index: Int) {
    update { it.filterIndexed { i, _ -> i != index } }
}

/**
 * Clears a list signal.
 */
fun <T> MutableSignal<List<T>>.clearList() {
    value = emptyList()
}

/**
 * Adds element to a set signal.
 */
@JvmName("addToSet")
fun <T> MutableSignal<Set<T>>.add(element: T) {
    update { it + element }
}

/**
 * Removes element from a set signal.
 */
@JvmName("removeFromSet")
fun <T> MutableSignal<Set<T>>.remove(element: T) {
    update { it - element }
}

/**
 * Clears a set signal.
 */
fun <T> MutableSignal<Set<T>>.clearSet() {
    value = emptySet()
}

/**
 * Puts entry in a map signal.
 */
fun <K, V> MutableSignal<Map<K, V>>.put(key: K, value: V) {
    update { it + (key to value) }
}

/**
 * Removes key from a map signal.
 */
@JvmName("removeFromMap")
fun <K, V> MutableSignal<Map<K, V>>.remove(key: K) {
    update { it - key }
}

/**
 * Clears a map signal.
 */
fun <K, V> MutableSignal<Map<K, V>>.clearMap() {
    value = emptyMap()
}
