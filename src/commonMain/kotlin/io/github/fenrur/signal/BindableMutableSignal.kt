package io.github.fenrur.signal

/**
 * A [io.github.fenrur.signal.MutableSignal] that can bind to another MutableSignal.
 *
 * This signal acts as a proxy to another signal, allowing you to:
 * - Switch the underlying signal at runtime
 * - Optionally take ownership of bound signals (closing them when this signal closes)
 *
 * Extends [io.github.fenrur.signal.BindableSignal] with mutable capabilities, requiring a [io.github.fenrur.signal.MutableSignal] as the binding target.
 *
 * @param T the type of value held by the signal
 */
interface BindableMutableSignal<T> : io.github.fenrur.signal.BindableSignal<T>,
    io.github.fenrur.signal.MutableSignal<T> {

    /**
     * Binds this signal to a new underlying mutable signal.
     *
     * - Unsubscribes from the previous signal
     * - If takeOwnership is enabled, closes the previous signal
     * - Subscribes to the new signal and notifies listeners
     *
     * @param newSignal the new mutable signal to bind to
     */
    fun bindTo(newSignal: io.github.fenrur.signal.MutableSignal<T>)

    /**
     * Binds this signal to a new underlying signal.
     *
     * The provided signal must be a [io.github.fenrur.signal.MutableSignal], otherwise an [IllegalArgumentException] is thrown.
     *
     * @param newSignal the new signal to bind to (must be a MutableSignal)
     * @throws IllegalArgumentException if the signal is not a MutableSignal
     */
    override fun bindTo(newSignal: io.github.fenrur.signal.Signal<T>) {
        if (newSignal is io.github.fenrur.signal.MutableSignal<T>) {
            bindTo(newSignal)
        } else {
            throw IllegalArgumentException("BindableMutableSignal requires a MutableSignal, got ${newSignal::class.simpleName}")
        }
    }

    /**
     * Returns the currently bound mutable signal.
     *
     * @return the bound mutable signal, or null if not bound
     */
    override fun currentSignal(): io.github.fenrur.signal.MutableSignal<T>?

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
        fun <T> wouldCreateCycle(source: BindableMutableSignal<T>, target: io.github.fenrur.signal.MutableSignal<T>): Boolean {
            return io.github.fenrur.signal.BindableSignal.wouldCreateCycle(source, target)
        }
    }
}
