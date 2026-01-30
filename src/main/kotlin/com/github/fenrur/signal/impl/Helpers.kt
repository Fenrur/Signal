package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Either
import com.github.fenrur.signal.SubscribeListener

/**
 * Internal helper to notify all listeners with a value.
 */
internal fun <T> notifyAllValue(listeners: Iterable<SubscribeListener<T>>, value: T) {
    for (l in listeners) {
        try {
            l(Either.Right(value))
        } catch (_: Throwable) {
            // Ignore listener exceptions
        }
    }
}

/**
 * Internal helper to notify all listeners with an error.
 */
internal fun <T> notifyAllError(listeners: Iterable<SubscribeListener<T>>, ex: Throwable) {
    for (l in listeners) {
        try {
            l(Either.Left(ex))
        } catch (_: Throwable) {
            // Ignore listener exceptions
        }
    }
}
