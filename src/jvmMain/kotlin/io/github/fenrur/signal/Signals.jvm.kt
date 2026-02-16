@file:JvmName("SignalsJvm")

package io.github.fenrur.signal

import io.github.fenrur.signal.impl.*
import org.reactivestreams.Publisher

// =============================================================================
// JDK FLOW INTEGRATION (Java 9+)
// =============================================================================

/**
 * Creates a [io.github.fenrur.signal.impl.FlowSignal] from a Java [java.util.concurrent.Flow.Publisher] (JDK 9+).
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
