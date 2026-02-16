package com.github.fenrur.signal

import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Abstract threading tests for [Signal] interface.
 * Runs on JVM + Native (multi-threaded platforms).
 */
abstract class AbstractSignalThreadingTest<S : Signal<Int>> {

    protected abstract fun createSignal(initial: Int): S

    @Test
    fun `concurrent subscriptions are safe`() {
        val signal = createSignal(42)
        val latch = TestCountDownLatch(10)
        val errors = AtomicInt(0)

        repeat(10) {
            testThread {
                try {
                    val unsubscribe = signal.subscribe { }
                    testSleep(10)
                    unsubscribe()
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5000))
        assertEquals(0, errors.load())
    }

    @Test
    fun `concurrent reads are safe`() {
        val signal = createSignal(42)
        val latch = TestCountDownLatch(100)
        val errors = AtomicInt(0)

        repeat(100) {
            testThread {
                try {
                    repeat(100) {
                        signal.value
                    }
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5000))
        assertEquals(0, errors.load())
    }
}
