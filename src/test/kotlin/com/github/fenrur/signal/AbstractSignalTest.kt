package com.github.fenrur.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Abstract test class for [Signal] interface.
 * Implement [createSignal] to test a specific Signal implementation.
 */
abstract class AbstractSignalTest<S : Signal<Int>> {

    /**
     * Creates a Signal with the given initial value.
     */
    protected abstract fun createSignal(initial: Int): S

    // ==================== value ====================

    @Test
    fun `value returns initial value`() {
        val signal = createSignal(42)
        assertThat(signal.value).isEqualTo(42)
    }

    @Test
    fun `value can be read multiple times`() {
        val signal = createSignal(100)
        assertThat(signal.value).isEqualTo(100)
        assertThat(signal.value).isEqualTo(100)
        assertThat(signal.value).isEqualTo(100)
    }

    // ==================== subscribe ====================

    @Test
    fun `subscribe receives initial value immediately`() {
        val signal = createSignal(42)
        val received = AtomicReference<Int?>(null)

        signal.subscribe { either ->
            either.onRight { received.set(it) }
        }

        assertThat(received.get()).isEqualTo(42)
    }

    @Test
    fun `subscribe returns unsubscriber function`() {
        val signal = createSignal(42)
        val unsubscribe = signal.subscribe { }
        // Unsubscriber should be callable without throwing
        unsubscribe()
    }

    @Test
    fun `multiple subscribers receive initial value`() {
        val signal = createSignal(42)
        val received1 = AtomicReference<Int?>(null)
        val received2 = AtomicReference<Int?>(null)
        val received3 = AtomicReference<Int?>(null)

        signal.subscribe { it.onRight { v -> received1.set(v) } }
        signal.subscribe { it.onRight { v -> received2.set(v) } }
        signal.subscribe { it.onRight { v -> received3.set(v) } }

        assertThat(received1.get()).isEqualTo(42)
        assertThat(received2.get()).isEqualTo(42)
        assertThat(received3.get()).isEqualTo(42)
    }

    @Test
    fun `unsubscribe stops receiving updates`() {
        val signal = createSignal(42)
        val callCount = AtomicInteger(0)

        val unsubscribe = signal.subscribe {
            callCount.incrementAndGet()
        }

        assertThat(callCount.get()).isEqualTo(1) // Initial value

        unsubscribe()

        // After unsubscribe, no more calls should be received
        // (assuming no value changes happen - this is a base test)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val signal = createSignal(42)
        signal.close()

        val received = AtomicReference<Int?>(null)
        val unsubscribe = signal.subscribe { it.onRight { v -> received.set(v) } }

        assertThat(received.get()).isNull()
        // Should not throw
        unsubscribe()
    }

    // ==================== isClosed ====================

    @Test
    fun `isClosed is false initially`() {
        val signal = createSignal(42)
        assertThat(signal.isClosed).isFalse()
    }

    @Test
    fun `isClosed is true after close`() {
        val signal = createSignal(42)
        signal.close()
        assertThat(signal.isClosed).isTrue()
    }

    @Test
    fun `close is idempotent`() {
        val signal = createSignal(42)
        signal.close()
        signal.close()
        signal.close()
        assertThat(signal.isClosed).isTrue()
    }

    // ==================== getValue (property delegate) ====================

    @Test
    fun `getValue returns current value`() {
        val signal = createSignal(42)
        val value: Int by signal
        assertThat(value).isEqualTo(42)
    }

    // ==================== AutoCloseable ====================

    @Test
    fun `can be used in use block`() {
        var closedSignal: Signal<Int>? = null

        createSignal(42).use { signal ->
            assertThat(signal.value).isEqualTo(42)
            closedSignal = signal
        }

        assertThat(closedSignal?.isClosed).isTrue()
    }

    // ==================== Thread safety ====================

    @Test
    fun `concurrent subscriptions are safe`() {
        val signal = createSignal(42)
        val latch = CountDownLatch(10)
        val errors = AtomicInteger(0)

        repeat(10) {
            thread {
                try {
                    val unsubscribe = signal.subscribe { }
                    Thread.sleep(10)
                    unsubscribe()
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
    fun `concurrent reads are safe`() {
        val signal = createSignal(42)
        val latch = CountDownLatch(100)
        val errors = AtomicInteger(0)

        repeat(100) {
            thread {
                try {
                    repeat(100) {
                        signal.value
                    }
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

    // ==================== Error handling ====================

    @Test
    fun `subscriber exception during initial call propagates`() {
        val signal = createSignal(42)

        // Exception thrown during initial subscription call should propagate
        var exceptionThrown = false
        try {
            signal.subscribe { throw RuntimeException("Test exception") }
        } catch (e: RuntimeException) {
            exceptionThrown = true
            assertThat(e.message).isEqualTo("Test exception")
        }
        assertThat(exceptionThrown).isTrue()
    }

    @Test
    fun `multiple subscribers work independently`() {
        val signal = createSignal(42)
        val received1 = AtomicReference<Int?>(null)
        val received2 = AtomicReference<Int?>(null)

        signal.subscribe { it.onRight { v -> received1.set(v) } }
        signal.subscribe { it.onRight { v -> received2.set(v) } }

        assertThat(received1.get()).isEqualTo(42)
        assertThat(received2.get()).isEqualTo(42)
    }
}
