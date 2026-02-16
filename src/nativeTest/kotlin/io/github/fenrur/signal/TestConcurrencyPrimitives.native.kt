package io.github.fenrur.signal

import kotlin.concurrent.atomics.*
import kotlin.time.TimeSource
import kotlinx.coroutines.*

actual class TestCountDownLatch actual constructor(count: Int) {
    private val remaining = AtomicInt(count)

    actual fun countDown() {
        remaining.decrementAndFetch()
    }

    actual fun await(timeoutMs: Long): Boolean {
        val mark = TimeSource.Monotonic.markNow()
        while (remaining.load() > 0) {
            if (mark.elapsedNow().inWholeMilliseconds >= timeoutMs) return false
        }
        return true
    }
}

actual fun testSleep(ms: Long) {
    val mark = TimeSource.Monotonic.markNow()
    while (mark.elapsedNow().inWholeMilliseconds < ms) {
        // spin
    }
}

@OptIn(DelicateCoroutinesApi::class)
actual fun testThread(start: Boolean, block: () -> Unit): TestThread {
    val startGate = if (start) null else AtomicBoolean(false)
    val done = AtomicBoolean(false)
    val job = GlobalScope.launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
        if (startGate != null) {
            // Wait for start() to be called
            while (!startGate.load()) {
                yield()
            }
        }
        block()
        done.store(true)
    }
    return TestThread(job, done, startGate)
}

actual class TestThread(
    private val job: Job,
    private val done: AtomicBoolean,
    private val startGate: AtomicBoolean?
) {
    actual fun start() {
        startGate?.store(true)
    }

    actual fun join(timeoutMs: Long) {
        runBlocking {
            withTimeout(timeoutMs) {
                job.join()
            }
        }
    }
}
