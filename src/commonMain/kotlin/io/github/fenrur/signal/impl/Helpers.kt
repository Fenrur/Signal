package io.github.fenrur.signal.impl

import io.github.fenrur.signal.SubscribeListener

/**
 * Internal helper to notify all listeners with a value.
 */
internal fun <T> notifyAllValue(listeners: Iterable<io.github.fenrur.signal.SubscribeListener<T>>, value: T) {
    for (l in listeners) {
        try {
            l(Result.success(value))
        } catch (_: Throwable) {
            // Ignore listener exceptions
        }
    }
}

/**
 * Internal helper to notify all listeners with an error.
 */
internal fun <T> notifyAllError(listeners: Iterable<io.github.fenrur.signal.SubscribeListener<T>>, ex: Throwable) {
    for (l in listeners) {
        try {
            l(Result.failure(ex))
        } catch (_: Throwable) {
            // Ignore listener exceptions
        }
    }
}
