package io.github.fenrur.signal

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A read-only reactive signal that holds a value and notifies subscribers when it changes.
 *
 * Signals are the core primitive for reactive state management. They can be:
 * - Read via [value] property
 * - Subscribed to via [subscribe] method
 * - Used as Kotlin property delegates
 * - Closed when no longer needed
 *
 * ## Thread-Safety Guarantees
 *
 * All signal implementations in this library are fully thread-safe:
 *
 * - **Atomic reads**: Reading [value] is always atomic and returns a consistent value
 * - **Concurrent subscriptions**: Multiple threads can subscribe/unsubscribe simultaneously
 * - **Lock-free**: No blocking synchronization is used, avoiding deadlocks
 * - **Listener isolation**: An exception in one listener does not affect other listeners
 *
 * ## Glitch-Free Semantics
 *
 * This library implements a push-pull model that guarantees glitch-free behavior:
 *
 * - Derived signals (e.g., from `map`, `combine`) never observe inconsistent intermediate states
 * - In diamond dependency patterns, derived signals receive exactly one notification per source update
 * - Batch updates (via [io.github.fenrur.signal.batch]) group multiple source changes into a single consistent update
 *
 * ## Exception Handling
 *
 * - Exceptions thrown by listeners during [subscribe] propagate to the caller
 * - Exceptions thrown by listeners during subsequent notifications are caught and ignored
 * - Computed signals catch transformation exceptions and propagate them via `Result.failure()`
 *
 * ## Close Semantics
 *
 * The [close] method provides **best-effort** cleanup under concurrent access. Because the
 * library uses lock-free algorithms for performance, `close()` provides eventual cleanup
 * rather than instant synchronization. Specifically:
 *
 * - **Listeners**: Listeners added concurrently with `close()` may still receive one notification
 * - **Mutable values**: Values set concurrently with `close()` on [MutableSignal][io.github.fenrur.signal.MutableSignal] may still propagate
 * - **Targets**: Targets added concurrently with `close()` may still receive dirty marks
 * - **Reads**: Value/version reads may briefly observe a stale pairing during concurrent close
 *
 * This is a deliberate design choice: the library prioritizes lock-free performance,
 * accepting that `close()` guarantees all resources are eventually released but does
 * not provide an instantaneous synchronization barrier.
 *
 * @param T the type of value held by the signal
 */
interface Signal<out T> : AutoCloseable, ReadOnlyProperty<Any?, T> {

    /**
     * The current value of the signal.
     */
    val value: T

    /**
     * Subscribes to changes in this signal.
     *
     * The listener will be called immediately with the current value,
     * and then whenever the value changes.
     *
     * @param listener the callback to invoke on value changes or errors
     * @return a function to unsubscribe from this signal
     */
    fun subscribe(listener: io.github.fenrur.signal.SubscribeListener<T>): io.github.fenrur.signal.UnSubscriber

    /**
     * Returns true if this signal has been closed.
     */
    val isClosed: Boolean

    /**
     * Allows using this signal as a Kotlin property delegate.
     *
     * Example:
     * ```kotlin
     * val count by mySignal
     * println(count) // reads the current value
     * ```
     */
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}