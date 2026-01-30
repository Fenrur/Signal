package com.github.fenrur.signal

/**
 * Represents a value of one of two possible types (a disjoint union).
 * An instance of Either is either a [Left] or a [Right].
 *
 * By convention, [Left] is used for failure and [Right] is used for success.
 *
 * @param L the type of the left value
 * @param R the type of the right value
 */
sealed class Either<out L, out R> {

    /**
     * Returns true if this is a [Left], false otherwise.
     */
    abstract val isLeft: Boolean

    /**
     * Returns true if this is a [Right], false otherwise.
     */
    abstract val isRight: Boolean

    /**
     * The left side of the disjoint union, as opposed to the [Right] side.
     */
    data class Left<out L>(val value: L) : Either<L, Nothing>() {
        override val isLeft: Boolean = true
        override val isRight: Boolean = false

        override fun toString(): String = "Left($value)"
    }

    /**
     * The right side of the disjoint union, as opposed to the [Left] side.
     */
    data class Right<out R>(val value: R) : Either<Nothing, R>() {
        override val isLeft: Boolean = false
        override val isRight: Boolean = true

        override fun toString(): String = "Right($value)"
    }

    companion object {
        /**
         * Creates a [Left] instance.
         */
        fun <L> left(value: L): Either<L, Nothing> = Left(value)

        /**
         * Creates a [Right] instance.
         */
        fun <R> right(value: R): Either<Nothing, R> = Right(value)

        /**
         * Wraps a computation that might throw into an [Either].
         * Returns [Right] if successful, [Left] with the exception otherwise.
         */
        inline fun <R> catch(block: () -> R): Either<Throwable, R> =
            try {
                Right(block())
            } catch (e: Throwable) {
                Left(e)
            }
    }

    /**
     * Applies [ifLeft] if this is a [Left] or [ifRight] if this is a [Right].
     *
     * @param ifLeft the function to apply if this is a [Left]
     * @param ifRight the function to apply if this is a [Right]
     * @return the result of applying the appropriate function
     */
    inline fun <C> fold(ifLeft: (L) -> C, ifRight: (R) -> C): C = when (this) {
        is Left -> ifLeft(value)
        is Right -> ifRight(value)
    }

    /**
     * Returns the right value if this is a [Right], or null otherwise.
     */
    fun getOrNull(): R? = when (this) {
        is Left -> null
        is Right -> value
    }

    /**
     * Returns the left value if this is a [Left], or null otherwise.
     */
    fun leftOrNull(): L? = when (this) {
        is Left -> value
        is Right -> null
    }

    /**
     * Returns the right value if this is a [Right], or the result of [default] otherwise.
     */
    inline fun <R2 : @UnsafeVariance R> getOrElse(default: (L) -> R2): R = when (this) {
        is Left -> default(value)
        is Right -> value
    }

    /**
     * Returns the right value if this is a [Right], or [defaultValue] otherwise.
     */
    fun getOrDefault(defaultValue: @UnsafeVariance R): R = when (this) {
        is Left -> defaultValue
        is Right -> value
    }

    /**
     * Maps the right value if this is a [Right].
     */
    inline fun <C> map(transform: (R) -> C): Either<L, C> = when (this) {
        is Left -> this
        is Right -> Right(transform(value))
    }

    /**
     * Maps the left value if this is a [Left].
     */
    inline fun <C> mapLeft(transform: (L) -> C): Either<C, R> = when (this) {
        is Left -> Left(transform(value))
        is Right -> this
    }

    /**
     * FlatMaps the right value if this is a [Right].
     */
    inline fun <C> flatMap(transform: (R) -> Either<@UnsafeVariance L, C>): Either<L, C> = when (this) {
        is Left -> this
        is Right -> transform(value)
    }

    /**
     * Executes [action] if this is a [Right].
     */
    inline fun onRight(action: (R) -> Unit): Either<L, R> {
        if (this is Right) action(value)
        return this
    }

    /**
     * Executes [action] if this is a [Left].
     */
    inline fun onLeft(action: (L) -> Unit): Either<L, R> {
        if (this is Left) action(value)
        return this
    }

    /**
     * Swaps the [Left] and [Right] types.
     */
    fun swap(): Either<R, L> = when (this) {
        is Left -> Right(value)
        is Right -> Left(value)
    }
}