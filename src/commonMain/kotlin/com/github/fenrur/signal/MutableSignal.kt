package com.github.fenrur.signal

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A mutable reactive signal that can be both read and written.
 *
 * In addition to [Signal] capabilities, a MutableSignal can:
 * - Have its value set directly
 * - Be updated atomically via [update]
 * - Be used as a read-write Kotlin property delegate
 *
 * ## Thread-Safety Guarantees
 *
 * All write operations are fully thread-safe:
 *
 * - **Atomic writes**: Setting [value] is atomic and uses compare-and-set internally
 * - **Atomic updates**: [update] uses a CAS loop to ensure thread-safe read-modify-write
 * - **Concurrent writes**: Multiple threads can write simultaneously without corruption
 * - **No lost updates**: The [update] function guarantees no updates are lost under contention
 *
 * ## Notification Semantics
 *
 * - Setting the same value does not trigger notifications (equality check)
 * - Subscribers receive the final consistent state after each write
 * - Derived signals are updated atomically in the dependency graph
 *
 * ## Example
 *
 * ```kotlin
 * val counter = mutableSignalOf(0)
 *
 * // Thread-safe increment from multiple threads
 * repeat(1000) {
 *     launch { counter.update { it + 1 } }
 * }
 *
 * // counter.value is guaranteed to be 1000
 * ```
 *
 * @param T the type of value held by the signal
 */
interface MutableSignal<T> : Signal<T>, ReadWriteProperty<Any?, T> {

    /**
     * The current value of the signal. Setting this will notify all subscribers.
     */
    override var value: T

    /**
     * Atomically updates the value using the given transform function.
     *
     * This is useful for thread-safe updates where the new value depends on the current value.
     *
     * @param transform a function that takes the current value and returns the new value
     */
    fun update(transform: (T) -> T)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this@MutableSignal.value = value
    }
}