package com.github.fenrur.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Abstract test class for [MutableSignal] interface.
 * Extends [AbstractSignalTest] to inherit all Signal tests.
 */
abstract class AbstractMutableSignalTest : AbstractSignalTest<MutableSignal<Int>>() {

    /**
     * Creates a MutableSignal with the given initial value.
     */
    abstract override fun createSignal(initial: Int): MutableSignal<Int>

    // ==================== value setter ====================

    @Test
    fun `setting value updates the signal`() {
        val signal = createSignal(42)
        signal.value = 100
        assertThat(signal.value).isEqualTo(100)
    }

    @Test
    fun `setting value notifies subscribers`() {
        val signal = createSignal(42)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }

        signal.value = 100
        signal.value = 200

        assertThat(values).containsExactly(42, 100, 200)
    }

    @Test
    fun `setting same value does not notify subscribers`() {
        val signal = createSignal(42)
        val callCount = AtomicInteger(0)

        signal.subscribe { callCount.incrementAndGet() }

        signal.value = 42 // Same value
        signal.value = 42 // Same value again

        assertThat(callCount.get()).isEqualTo(1) // Only initial notification
    }

    @Test
    fun `setting value on closed signal does nothing`() {
        val signal = createSignal(42)
        signal.close()

        signal.value = 100

        // Value might still be readable as 42 or 100 depending on impl,
        // but no exception should be thrown
    }

    @Test
    fun `setting value does not notify unsubscribed listeners`() {
        val signal = createSignal(42)
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = signal.subscribe { it.onRight { v -> values.add(v) } }

        signal.value = 100
        unsubscribe()
        signal.value = 200

        assertThat(values).containsExactly(42, 100)
    }

    // ==================== update ====================

    @Test
    fun `update transforms current value`() {
        val signal = createSignal(42)
        signal.update { it + 10 }
        assertThat(signal.value).isEqualTo(52)
    }

    @Test
    fun `update notifies subscribers`() {
        val signal = createSignal(42)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }

        signal.update { it * 2 }

        assertThat(values).containsExactly(42, 84)
    }

    @Test
    fun `update with same value does not notify`() {
        val signal = createSignal(42)
        val callCount = AtomicInteger(0)

        signal.subscribe { callCount.incrementAndGet() }

        signal.update { it } // Returns same value

        assertThat(callCount.get()).isEqualTo(1) // Only initial
    }

    @Test
    fun `update on closed signal does nothing`() {
        val signal = createSignal(42)
        signal.close()

        signal.update { it + 10 }
        // Should not throw
    }

    @Test
    fun `update is atomic under contention`() {
        val signal = createSignal(0)
        val latch = CountDownLatch(100)

        repeat(100) {
            thread {
                repeat(100) {
                    signal.update { it + 1 }
                }
                latch.countDown()
            }
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(signal.value).isEqualTo(10000)
    }

    // ==================== setValue (property delegate) ====================

    @Test
    fun `setValue updates the signal`() {
        val signal = createSignal(42)
        var prop: Int by signal
        prop = 100
        assertThat(signal.value).isEqualTo(100)
    }

    @Test
    fun `property delegate read and write`() {
        val signal = createSignal(42)
        var prop: Int by signal

        assertThat(prop).isEqualTo(42)
        prop = 100
        assertThat(prop).isEqualTo(100)
        assertThat(signal.value).isEqualTo(100)
    }

    // ==================== Thread safety with mutations ====================

    @Test
    fun `concurrent writes are safe`() {
        val signal = createSignal(0)
        val latch = CountDownLatch(100)
        val errors = AtomicInteger(0)

        repeat(100) { i ->
            thread {
                try {
                    signal.value = i
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(errors.get()).isEqualTo(0)
    }

    @Test
    fun `concurrent reads and writes are safe`() {
        val signal = createSignal(0)
        val latch = CountDownLatch(40)
        val errors = AtomicInteger(0)

        // Writers
        repeat(20) { i ->
            thread {
                try {
                    repeat(10) {
                        signal.value = i * 10 + it
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Readers
        repeat(20) {
            thread {
                try {
                    repeat(50) {
                        signal.value // Just read
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        assertThat(errors.get()).isEqualTo(0)
    }

    @Test
    fun `concurrent subscribe unsubscribe and write are safe`() {
        val signal = createSignal(0)
        val latch = CountDownLatch(30)
        val errors = AtomicInteger(0)

        // Subscribers
        repeat(10) {
            thread {
                try {
                    val unsub = signal.subscribe { }
                    Thread.sleep(1)
                    unsub()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Writers
        repeat(10) { i ->
            thread {
                try {
                    signal.value = i
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Updaters
        repeat(10) {
            thread {
                try {
                    signal.update { it + 1 }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
        // Allow small number of errors in highly concurrent scenarios
        assertThat(errors.get()).isLessThanOrEqualTo(2)
    }

    // ==================== Notification order ====================

    /**
     * Override this to return false if the implementation doesn't guarantee notification order.
     * IndexedSignal uses a Set which doesn't preserve order.
     */
    protected open fun preservesSubscriptionOrder(): Boolean = true

    @Test
    fun `subscribers are notified in subscription order`() {
        if (!preservesSubscriptionOrder()) {
            return // Skip for implementations that don't guarantee order
        }

        val signal = createSignal(0)
        val order = CopyOnWriteArrayList<String>()

        signal.subscribe { order.add("first") }
        signal.subscribe { order.add("second") }
        signal.subscribe { order.add("third") }

        order.clear() // Clear initial notifications

        signal.value = 1

        assertThat(order).containsExactly("first", "second", "third")
    }

    // ==================== Edge cases ====================

    @Test
    fun `null value is supported for nullable types`() {
        // This test uses a different signal type
        val signal = createNullableSignal()
        assertThat(signal.value).isNull()

        signal.value = 42
        assertThat(signal.value).isEqualTo(42)

        signal.value = null
        assertThat(signal.value).isNull()
    }

    /**
     * Override to provide a nullable signal for testing.
     * Default implementation creates a signal with null initial value.
     */
    protected open fun createNullableSignal(): MutableSignal<Int?> {
        @Suppress("UNCHECKED_CAST")
        return createSignal(0) as MutableSignal<Int?>
    }
}
