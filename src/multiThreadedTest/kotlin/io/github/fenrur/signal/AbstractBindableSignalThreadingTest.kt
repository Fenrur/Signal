package io.github.fenrur.signal

import io.github.fenrur.signal.impl.DefaultMutableSignal
import kotlin.test.*

/**
 * Abstract threading tests for BindableSignal implementations.
 * Runs on JVM + Native (multi-threaded platforms).
 */
abstract class AbstractBindableSignalThreadingTest<S : Signal<Int>> {

    protected abstract fun createUnboundSignal(): S

    protected abstract fun createSignal(source: Signal<Int>): S

    protected fun createMutableSource(initial: Int): DefaultMutableSignal<Int> =
        DefaultMutableSignal(initial)

    protected abstract fun bindTo(signal: S, source: Signal<Int>)

    @Test
    fun `concurrent rebinding is thread-safe`() {
        val signals = (1..10).map { createMutableSource(it * 10) }
        val bindable = createSignal(signals[0])
        val latch = TestCountDownLatch(1)
        val threads = mutableListOf<TestThread>()

        bindable.subscribe { }

        // Multiple threads rebinding
        repeat(5) { threadId ->
            threads += testThread(start = false) {
                latch.await(5000)
                repeat(20) { i ->
                    val idx = (threadId * 20 + i) % signals.size
                    bindTo(bindable, signals[idx])
                }
            }
        }

        // Thread updating signals
        threads += testThread(start = false) {
            latch.await(5000)
            repeat(100) { i ->
                signals.forEach { it.value = i * 100 }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }

        // Bindable is still functional
        val finalSignal = createMutableSource(999)
        bindTo(bindable, finalSignal)
        assertEquals(999, bindable.value)
    }
}
