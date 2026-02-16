package com.github.fenrur.signal

import java.util.concurrent.TimeUnit

actual class TestCountDownLatch actual constructor(count: Int) {
    private val delegate = java.util.concurrent.CountDownLatch(count)

    actual fun countDown() = delegate.countDown()

    actual fun await(timeoutMs: Long): Boolean = delegate.await(timeoutMs, TimeUnit.MILLISECONDS)
}

actual fun testSleep(ms: Long) = Thread.sleep(ms)

actual fun testThread(start: Boolean, block: () -> Unit): TestThread {
    val t = Thread(block)
    if (start) t.start()
    return TestThread(t)
}

actual class TestThread(private val delegate: Thread) {
    actual fun start() = delegate.start()
    actual fun join(timeoutMs: Long) = delegate.join(timeoutMs)
}
