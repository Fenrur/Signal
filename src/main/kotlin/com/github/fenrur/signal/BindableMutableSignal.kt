package com.github.fenrur.signal

/**
 * A [MutableSignal] that can bind to another MutableSignal.
 *
 * This signal acts as a proxy to another signal, allowing you to:
 * - Switch the underlying signal at runtime
 * - Optionally take ownership of bound signals (closing them when this signal closes)
 *
 * @param T the type of value held by the signal
 */
interface BindableMutableSignal<T> : MutableSignal<T> {

    /**
     * Binds this signal to a new underlying signal.
     *
     * - Unsubscribes from the previous signal
     * - If takeOwnership is enabled, closes the previous signal
     * - Subscribes to the new signal and notifies listeners
     *
     * @param newSignal the new signal to bind to
     */
    fun bindTo(newSignal: MutableSignal<T>)

    /**
     * Returns the currently bound signal.
     *
     * @return the bound signal, or null if not bound
     */
    fun currentSignal(): MutableSignal<T>?

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
        fun <T> wouldCreateCycle(source: BindableMutableSignal<T>, target: MutableSignal<T>): Boolean {
            val visited = mutableSetOf<MutableSignal<*>>()
            visited.add(source)

            var current: MutableSignal<*>? = target
            while (current != null) {
                if (current in visited) {
                    return true
                }
                visited.add(current)
                current = (current as? BindableMutableSignal<*>)?.currentSignal()
            }
            return false
        }
    }
}
