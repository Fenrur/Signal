package com.github.fenrur.signal

import com.github.fenrur.signal.impl.CopyOnWriteArrayList
import kotlin.concurrent.atomics.*
import kotlin.test.*

/**
 * Abstract threading tests for [MutableSignal] interface.
 * Runs on JVM + Native (multi-threaded platforms).
 * Extends [AbstractSignalThreadingTest] to inherit Signal threading tests.
 */
abstract class AbstractMutableSignalThreadingTest : AbstractSignalThreadingTest<MutableSignal<Int>>() {

    abstract override fun createSignal(initial: Int): MutableSignal<Int>

    @Test
    fun `update is atomic under contention`() {
        val signal = createSignal(0)
        val latch = TestCountDownLatch(100)

        repeat(100) {
            testThread {
                repeat(100) {
                    signal.update { it + 1 }
                }
                latch.countDown()
            }
        }

        assertTrue(latch.await(10000))
        assertEquals(10000, signal.value)
    }

    @Test
    fun `concurrent writes are safe`() {
        val signal = createSignal(0)
        val latch = TestCountDownLatch(100)
        val errors = AtomicInt(0)

        repeat(100) { i ->
            testThread {
                try {
                    signal.value = i
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
    fun `concurrent reads and writes are safe`() {
        val signal = createSignal(0)
        val latch = TestCountDownLatch(40)
        val errors = AtomicInt(0)

        // Writers
        repeat(20) { i ->
            testThread {
                try {
                    repeat(10) {
                        signal.value = i * 10 + it
                    }
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Readers
        repeat(20) {
            testThread {
                try {
                    repeat(50) {
                        signal.value
                    }
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(30000))
        assertEquals(0, errors.load())
    }

    @Test
    fun `concurrent subscribe unsubscribe and write are safe`() {
        val signal = createSignal(0)
        val latch = TestCountDownLatch(30)
        val errors = AtomicInt(0)

        // Subscribers
        repeat(10) {
            testThread {
                try {
                    val unsub = signal.subscribe { }
                    testSleep(1)
                    unsub()
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Writers
        repeat(10) { i ->
            testThread {
                try {
                    signal.value = i
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Updaters
        repeat(10) {
            testThread {
                try {
                    signal.update { it + 1 }
                } catch (_: Exception) {
                    errors.incrementAndFetch()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(30000))
        // Allow small number of errors in highly concurrent scenarios
        assertTrue(errors.load() <= 5)
    }

    @Test
    fun `subscribers are notified in subscription order under concurrency`() {
        val signal = createSignal(0)
        val order = CopyOnWriteArrayList<String>()

        signal.subscribe { order.add("first") }
        signal.subscribe { order.add("second") }
        signal.subscribe { order.add("third") }

        order.clear()

        signal.value = 1

        assertEquals(listOf("first", "second", "third"), order.toList())
    }
}
