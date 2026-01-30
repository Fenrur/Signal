package com.github.fenrur.signal

import com.github.fenrur.signal.SubscribeListener
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
    fun subscribe(listener: SubscribeListener<T>): UnSubscriber

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