@file:JvmName("Signals")

package com.github.fenrur.signal

import com.github.fenrur.signal.impl.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.reactivestreams.Publisher

/**
 * Creates a read-only [Signal] with the given initial value.
 *
 * The returned signal is immutable and will always return the same value.
 *
 * @param initial the value of the signal
 * @return a read-only signal
 */
fun <T> signalOf(initial: T): Signal<T> = DefaultMutableSignal(initial)

/**
 * Creates a [MutableSignal] with the given initial value.
 *
 * Uses the default [DefaultMutableSignal] implementation which is optimized for read-intensive scenarios.
 *
 * @param initial the initial value of the signal
 * @return a mutable signal
 */
fun <T> mutableSignalOf(initial: T): MutableSignal<T> = DefaultMutableSignal(initial)

/**
 * Creates a [BindableSignal] optionally bound to an initial signal.
 *
 * @param initialSignal optional initial signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableSignal
 */
fun <T> bindableSignalOf(
    initialSignal: Signal<T>? = null,
    takeOwnership: Boolean = false
): BindableSignal<T> = DefaultBindableSignal(initialSignal, takeOwnership)

/**
 * Creates a [BindableSignal] with an initial value.
 *
 * This is a convenience overload that creates an internal [Signal] from the provided value.
 *
 * @param initialValue the initial value for the signal
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableSignal initialized with the given value
 */
fun <T> bindableSignalOf(
    initialValue: T,
    takeOwnership: Boolean = false
): BindableSignal<T> = DefaultBindableSignal(signalOf(initialValue), takeOwnership)

/**
 * Creates a [BindableMutableSignal] optionally bound to an initial signal.
 *
 * @param initialSignal optional initialSignal signal to bind to
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableMutableSignal
 */
fun <T> bindableMutableSignalOf(
    initialSignal: MutableSignal<T>? = null,
    takeOwnership: Boolean = false
): BindableMutableSignal<T> = DefaultBindableMutableSignal(initialSignal, takeOwnership)

/**
 * Creates a [BindableMutableSignal] with an initial value.
 *
 * This is a convenience overload that creates an internal [MutableSignal] from the provided value.
 *
 * @param initialValue the initial value for the signal
 * @param takeOwnership if true, closes bound signals when unbinding
 * @return a BindableMutableSignal initialized with the given value
 */
fun <T> bindableMutableSignalOf(
    initialValue: T,
    takeOwnership: Boolean = false
): BindableMutableSignal<T> = DefaultBindableMutableSignal(mutableSignalOf(initialValue), takeOwnership)

// =============================================================================
// JDK FLOW INTEGRATION (Java 9+)
// =============================================================================

/**
 * Creates a [FlowSignal] from a Java [java.util.concurrent.Flow.Publisher] (JDK 9+).
 *
 * @param request number of items to request from the publisher
 * @return a signal backed by the publisher
 */
fun <T> java.util.concurrent.Flow.Publisher<T>.asJdkPublisher(request: Long = Long.MAX_VALUE): FlowSignal<T> =
    FlowSignal.withoutInitial(this, request)

/**
 * Creates a [FlowSignal] from a Java [java.util.concurrent.Flow.Publisher] (JDK 9+) with an initial value.
 *
 * @param initial the initial value before the publisher emits
 * @param request number of items to request from the publisher
 * @return a signal backed by the publisher
 */
fun <T> java.util.concurrent.Flow.Publisher<T>.asJdkPublisher(initial: T, request: Long = Long.MAX_VALUE): FlowSignal<T> =
    FlowSignal.withInitial(this, initial, request)

// =============================================================================
// REACTIVE STREAMS INTEGRATION
// =============================================================================

/**
 * Converts this [Signal] to a Reactive Streams [Publisher].
 *
 * The publisher will emit the current value immediately upon subscription,
 * and then emit new values whenever the signal changes.
 *
 * @return a Publisher that emits signal values
 */
fun <T> Signal<T>.asReactiveStreamsPublisher(): Publisher<T> = SignalPublisher(this)

/**
 * Creates a [ReactiveStreamsSignal] from a Reactive Streams [Publisher].
 *
 * @param request number of items to request from the publisher
 * @return a signal backed by the publisher
 */
fun <T> Publisher<T>.asSignal(request: Long = Long.MAX_VALUE): ReactiveStreamsSignal<T> =
    ReactiveStreamsSignal.withoutInitial(this, request)

/**
 * Creates a [ReactiveStreamsSignal] from a Reactive Streams [Publisher] with an initial value.
 *
 * @param initial the initial value before the publisher emits
 * @param request number of items to request from the publisher
 * @return a signal backed by the publisher
 */
fun <T> Publisher<T>.asSignal(initial: T, request: Long = Long.MAX_VALUE): ReactiveStreamsSignal<T> =
    ReactiveStreamsSignal.withInitial(this, initial, request)

// =============================================================================
// KOTLIN COROUTINES FLOW INTEGRATION
// =============================================================================

/**
 * Converts this [Signal] to a Kotlin [Flow].
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
 * Converts this [Signal] to a Kotlin [Flow] that emits [Result] values.
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
 * Creates a [StateFlowSignal] from a Kotlin [StateFlow].
 *
 * The signal observes the StateFlow and notifies subscribers whenever
 * the StateFlow's value changes (from any source).
 *
 * @param scope the CoroutineScope used to collect from the StateFlow
 * @return a read-only Signal backed by the StateFlow
 */
fun <T> StateFlow<T>.asSignal(scope: CoroutineScope): StateFlowSignal<T> = StateFlowSignal(this, scope)

/**
 * Creates a [MutableStateFlowSignal] from a Kotlin [MutableStateFlow].
 *
 * This signal provides bidirectional synchronization:
 * - Writing to the signal updates the StateFlow and notifies subscribers
 * - External updates to the StateFlow are detected and notify signal subscribers
 *
 * @param scope the CoroutineScope used to collect from the StateFlow
 * @return a MutableSignal backed by the MutableStateFlow
 */
fun <T> MutableStateFlow<T>.asSignal(scope: CoroutineScope): MutableStateFlowSignal<T> = MutableStateFlowSignal(this, scope)

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
