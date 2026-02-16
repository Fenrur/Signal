package io.github.fenrur.signal

/**
 * A simple countdown latch for coordinating threads in tests.
 */
expect class TestCountDownLatch(count: Int) {
    fun countDown()
    fun await(timeoutMs: Long): Boolean
}

/**
 * Sleeps the current thread for the given number of milliseconds.
 */
expect fun testSleep(ms: Long)

/**
 * Creates and optionally starts a new thread.
 */
expect fun testThread(start: Boolean = true, block: () -> Unit): TestThread

/**
 * A thread handle for joining.
 */
expect class TestThread {
    fun start()
    fun join(timeoutMs: Long)
}
