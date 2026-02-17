@file:JvmName("Signals")

package io.github.fenrur.signal

import io.github.fenrur.signal.impl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.jvm.JvmName

/**
 * Creates a read-only [io.github.fenrur.signal.Signal] with the given initial value.
 *
 * The returned signal is immutable and will always return the same value.
 *
 * @param initial the value of the signal
 * @return a read-only signal
 */
fun <T> signalOf(initial: T): Signal<T> =
    DefaultMutableSignal(initial)

/**
 * Creates a [io.github.fenrur.signal.MutableSignal] with the given initial value.
 *
 * Uses the default [io.github.fenrur.signal.impl.DefaultMutableSignal] implementation which is optimized for read-intensive scenarios.
 *
 * @param initial the initial value of the signal
 * @return a mutable signal
 */
fun <T> mutableSignalOf(initial: T): MutableSignal<T> =
    DefaultMutableSignal(initial)

/**
 * Creates a [io.github.fenrur.signal.BindableSignal] optionally bound to an initial signal.
 *
 * @param initialSignal optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableSignal
 */
fun <T> bindableSignalOf(
    initialSignal: Signal<T>? = null,
    takeOwnership: Boolean = false
): BindableSignal<T> =
    DefaultBindableSignal(initialSignal, takeOwnership)

/**
 * Creates a [io.github.fenrur.signal.BindableSignal] with an initial value.
 *
 * This is a convenience overload that creates an internal [io.github.fenrur.signal.Signal] from the provided value.
 *
 * @param initialValue the initial value for the signal
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableSignal initialized with the given value
 */
fun <T> bindableSignalOf(
    initialValue: T,
    takeOwnership: Boolean = false
): BindableSignal<T> = DefaultBindableSignal(
    signalOf(initialValue), takeOwnership
)

/**
 * Creates a [io.github.fenrur.signal.BindableMutableSignal] optionally bound to an initial signal.
 *
 * @param initialSignal optional initialSignal signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableMutableSignal
 */
fun <T> bindableMutableSignalOf(
    initialSignal: MutableSignal<T>? = null,
    takeOwnership: Boolean = false
): BindableMutableSignal<T> =
    DefaultBindableMutableSignal(initialSignal, takeOwnership)

/**
 * Creates a [io.github.fenrur.signal.BindableMutableSignal] with an initial value.
 *
 * This is a convenience overload that creates an internal [io.github.fenrur.signal.MutableSignal] from the provided value.
 *
 * @param initialValue the initial value for the signal
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableMutableSignal initialized with the given value
 */
fun <T> bindableMutableSignalOf(
    initialValue: T,
    takeOwnership: Boolean = false
): BindableMutableSignal<T> =
    DefaultBindableMutableSignal(
        mutableSignalOf(initialValue), takeOwnership
    )

// =============================================================================
// KOTLIN COROUTINES FLOW INTEGRATION
// =============================================================================

/**
 * Converts this [io.github.fenrur.signal.Signal] to a Kotlin [Flow].
 *
 * The flow will emit the current value immediately upon collection,
 * and then emit new values whenever the signal changes.
 *
 * @return a Flow that emits signal values
 */
fun <T> Signal<T>.asFlow(): Flow<T> = callbackFlow {
    val unsubscribe = subscribe { result ->
        result.fold(
            onSuccess = { value -> trySend(value) },
            onFailure = { error -> close(error) }
        )
    }
    awaitClose { unsubscribe() }
}

/**
 * Converts this [io.github.fenrur.signal.Signal] to a Kotlin [Flow] that emits [Result] values.
 *
 * This allows handling both values and errors in the flow.
 *
 * @return a Flow that emits Result values
 */
fun <T> Signal<T>.asResultFlow(): Flow<Result<T>> = callbackFlow {
    val unsubscribe = subscribe { result ->
        trySend(result)
    }
    awaitClose { unsubscribe() }
}

/**
 * Creates a [io.github.fenrur.signal.impl.StateFlowSignal] from a Kotlin [StateFlow].
 *
 * The signal observes the StateFlow and notifies subscribers whenever
 * the StateFlow's value changes (from any source).
 *
 * @param scope the CoroutineScope used to collect from the StateFlow
 * @return a read-only Signal backed by the StateFlow
 */
fun <T> StateFlow<T>.asSignal(scope: CoroutineScope): StateFlowSignal<T> =
    StateFlowSignal(this, scope)

/**
 * Creates a [io.github.fenrur.signal.impl.MutableStateFlowSignal] from a Kotlin [MutableStateFlow].
 *
 * This signal provides bidirectional synchronization:
 * - Writing to the signal updates the StateFlow and notifies subscribers
 * - External updates to the StateFlow are detected and notify signal subscribers
 *
 * @param scope the CoroutineScope used to collect from the StateFlow
 * @return a MutableSignal backed by the MutableStateFlow
 */
fun <T> MutableStateFlow<T>.asSignal(scope: CoroutineScope): MutableStateFlowSignal<T> =
    MutableStateFlowSignal(this, scope)

// =============================================================================
// BATCHING
// =============================================================================

/**
 * Executes a block within a batch context.
 *
 * Multiple signal mutations within the batch are grouped together.
 * Effects (subscriber notifications) are only executed once at the end of the outermost batch,
 * seeing the final consistent state. This prevents glitches and reduces unnecessary intermediate
 * notifications.
 *
 * Example:
 * ```kotlin
 * val a = mutableSignalOf(1)
 * val b = mutableSignalOf(10)
 * val c = combine(a, b) { x, y -> x + y }
 * val emissions = mutableListOf<Int>()
 * c.subscribe { result -> result.onSuccess { emissions.add(it) } }
 *
 * batch {
 *     a.value = 2
 *     b.value = 20
 * }
 * // emissions contains only [11, 22], not [11, 12, 22]
 * ```
 *
 * @param block the code to execute within the batch
 * @return the result of the block
 */
fun <T> batch(block: () -> T): T = SignalGraph.batch(block)
