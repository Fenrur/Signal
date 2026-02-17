package io.github.fenrur.signal

/**
 * A [io.github.fenrur.signal.Signal] that can bind to another Signal.
 *
 * This signal acts as a read-only proxy to another signal, allowing you to:
 * - Switch the underlying signal at runtime
 * - Optionally take ownership of bound signals (closing them when this signal closes)
 *
 * @param T the type of value held by the signal
 */
interface BindableSignal<out T> : Signal<T> {

    /**
     * Binds this signal to a new underlying signal.
     *
     * - Unsubscribes from the previous signal
     * - If takeOwnership is enabled, closes the previous signal
     * - Subscribes to the new signal and notifies listeners
     *
     * @param newSignal the new signal to bind to
     */
    fun bindTo(newSignal: Signal<@UnsafeVariance T>)

    /**
     * Returns the currently bound signal.
     *
     * @return the bound signal, or null if not bound
     */
    fun currentSignal(): Signal<T>?

    /**
     * Returns true if this signal is currently bound to another signal.
     */
    fun isBound(): Boolean

    companion object {
        /**
         * Checks if binding [source] to [target] would create a circular reference.
         *
         * A circular reference occurs when [target] is already bound (directly or indirectly) to [source].
         *
         * @param source the signal that would be bound to [target]
         * @param target the signal that [source] would bind to
         * @return true if binding would create a cycle, false otherwise
         */
        fun <T> wouldCreateCycle(source: BindableSignal<T>, target: Signal<T>): Boolean {
            val visited = mutableSetOf<Signal<*>>()
            visited.add(source)

            var current: Signal<*>? = target
            while (current != null) {
                if (current in visited) {
                    return true
                }
                visited.add(current)
                current = (current as? BindableSignal<*>)?.currentSignal()
            }
            return false
        }
    }
}
