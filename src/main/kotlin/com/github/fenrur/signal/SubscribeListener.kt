package com.github.fenrur.signal

import com.github.fenrur.signal.Either

/**
 * Listener type for signal subscriptions.
 * Receives either an error ([Either.Left]) or a value ([Either.Right]).
 */
typealias SubscribeListener<T> = (Either<Throwable, T>) -> Unit