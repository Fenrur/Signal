package io.github.fenrur.signal

import kotlin.test.*

/**
 * Abstract test class for [io.github.fenrur.signal.Signal] interface.
 * Contains non-threading tests that can run on all platforms.
 * Implement [createSignal] to test a specific Signal implementation.
 */
abstract class AbstractSignalTest<S : io.github.fenrur.signal.Signal<Int>> {

    /**
     * Creates a Signal with the given initial value.
     */
    protected abstract fun createSignal(initial: Int): S

    // ==================== value ====================

    @Test
    fun `value returns initial value`() {
        val signal = createSignal(42)
        assertEquals(42, signal.value)
    }

    @Test
    fun `value can be read multiple times`() {
        val signal = createSignal(100)
        assertEquals(100, signal.value)
        assertEquals(100, signal.value)
        assertEquals(100, signal.value)
    }

    // ==================== subscribe ====================

    @Test
    fun `subscribe receives initial value immediately`() {
        val signal = createSignal(42)
        var received: Int? = null

        signal.subscribe { result ->
            result.onSuccess { received = it }
        }

        assertEquals(42, received)
    }

    @Test
    fun `subscribe returns unsubscriber function`() {
        val signal = createSignal(42)
        val unsubscribe = signal.subscribe { }
        unsubscribe()
    }

    @Test
    fun `multiple subscribers receive initial value`() {
        val signal = createSignal(42)
        var received1: Int? = null
        var received2: Int? = null
        var received3: Int? = null

        signal.subscribe { it.onSuccess { v -> received1 = v } }
        signal.subscribe { it.onSuccess { v -> received2 = v } }
        signal.subscribe { it.onSuccess { v -> received3 = v } }

        assertEquals(42, received1)
        assertEquals(42, received2)
        assertEquals(42, received3)
    }

    @Test
    fun `unsubscribe stops receiving updates`() {
        val signal = createSignal(42)
        var callCount = 0

        val unsubscribe = signal.subscribe {
            callCount++
        }

        assertEquals(1, callCount)

        unsubscribe()
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val signal = createSignal(42)
        signal.close()

        var received: Int? = null
        val unsubscribe = signal.subscribe { it.onSuccess { v -> received = v } }

        assertNull(received)
        unsubscribe()
    }

    // ==================== isClosed ====================

    @Test
    fun `isClosed is false initially`() {
        val signal = createSignal(42)
        assertFalse(signal.isClosed)
    }

    @Test
    fun `isClosed is true after close`() {
        val signal = createSignal(42)
        signal.close()
        assertTrue(signal.isClosed)
    }

    @Test
    fun `close is idempotent`() {
        val signal = createSignal(42)
        signal.close()
        signal.close()
        signal.close()
        assertTrue(signal.isClosed)
    }

    // ==================== getValue (property delegate) ====================

    @Test
    fun `getValue returns current value`() {
        val signal = createSignal(42)
        val value: Int by signal
        assertEquals(42, value)
    }

    // ==================== AutoCloseable ====================

    @Test
    fun `can be used in use block`() {
        var closedSignal: io.github.fenrur.signal.Signal<Int>? = null

        createSignal(42).use { signal ->
            assertEquals(42, signal.value)
            closedSignal = signal
        }

        assertTrue(closedSignal?.isClosed == true)
    }

    // ==================== Error handling ====================

    @Test
    fun `subscriber exception during initial call propagates`() {
        val signal = createSignal(42)

        val exception = assertFailsWith<RuntimeException> {
            signal.subscribe { throw RuntimeException("Test exception") }
        }
        assertEquals("Test exception", exception.message)
    }

    @Test
    fun `multiple subscribers work independently`() {
        val signal = createSignal(42)
        var received1: Int? = null
        var received2: Int? = null

        signal.subscribe { it.onSuccess { v -> received1 = v } }
        signal.subscribe { it.onSuccess { v -> received2 = v } }

        assertEquals(42, received1)
        assertEquals(42, received2)
    }
}
