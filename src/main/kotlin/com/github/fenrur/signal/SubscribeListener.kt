package com.github.fenrur.signal

/**
 * Listener type for signal subscriptions.
 * Receives either a success ([Result.success]) or a failure ([Result.failure]).
 */
typealias SubscribeListener<T> = (Result<T>) -> Unit
