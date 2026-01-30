@file:JvmName("EitherArrowExtensions")

package com.github.fenrur.signal

import arrow.core.Either as ArrowEither

/**
 * Converts this [Either] to Arrow's [ArrowEither].
 *
 * **Note:** This extension requires Arrow-kt to be in the classpath.
 * Add the following dependency to use this function:
 * ```kotlin
 * implementation("io.arrow-kt:arrow-core:x.x.x")
 * ```
 *
 * @return Arrow's Either with the same Left/Right values
 */
fun <L, R> Either<L, R>.asArrow(): ArrowEither<L, R> = when (this) {
    is Either.Left -> ArrowEither.Left(value)
    is Either.Right -> ArrowEither.Right(value)
}

/**
 * Converts Arrow's [ArrowEither] to this library's [Either].
 *
 * **Note:** This extension requires Arrow-kt to be in the classpath.
 * Add the following dependency to use this function:
 * ```kotlin
 * implementation("io.arrow-kt:arrow-core:x.x.x")
 * ```
 *
 * @return Signal's Either with the same Left/Right values
 */
fun <L, R> ArrowEither<L, R>.asSignal(): Either<L, R> = when (this) {
    is ArrowEither.Left -> Either.Left(value)
    is ArrowEither.Right -> Either.Right(value)
}
