package io.github.fenrur.signal.impl

import io.github.fenrur.signal.operators.withLatestFrom
import kotlin.test.*

class WithLatestFromSignalTest {

    @Test
    fun `withLatestFrom signal returns combined initial value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertEquals(110, combined.value)
    }

    @Test
    fun `withLatestFrom signal emits when source changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20

        assertEquals(listOf(120), values) // 20 + 100
    }

    @Test
    fun `withLatestFrom signal does not emit when only other changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        other.value = 200 // Should NOT trigger emission

        assertTrue(values.isEmpty())
    }

    @Test
    fun `withLatestFrom signal samples latest from other on source change`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        other.value = 200 // Change other first
        source.value = 20 // Now source triggers with latest from other

        assertEquals(listOf(220), values) // 20 + 200
    }

    @Test
    fun `withLatestFrom signal value reflects current state`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertEquals(110, combined.value)

        other.value = 200
        assertEquals(210, combined.value) // Reading value samples current state

        source.value = 20
        assertEquals(220, combined.value)
    }

    @Test
    fun `withLatestFrom signal with pair combiner`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val combined = source.withLatestFrom(other) { a, b -> a to b }

        assertEquals(10 to "hello", combined.value)

        source.value = 20
        assertEquals(20 to "hello", combined.value)

        other.value = "world"
        source.value = 30
        assertEquals(30 to "world", combined.value)
    }

    @Test
    fun `withLatestFrom signal multiple source changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val combined = source.withLatestFrom(other) { a, b -> a * b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 2
        source.value = 3
        source.value = 4

        assertEquals(listOf(10, 20, 30, 40), values) // Initial and updates
    }

    @Test
    fun `withLatestFrom signal closes properly`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertFalse(combined.isClosed)

        combined.close()

        assertTrue(combined.isClosed)
    }

    @Test
    fun `withLatestFrom signal stops receiving after close`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        combined.close()
        values.clear()

        source.value = 20

        assertTrue(values.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        val unsubscribe = combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertEquals(listOf(120), values)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        combined.close()

        val values = mutableListOf<Int>()
        val unsubscribe = combined.subscribe { it.onSuccess { v -> values.add(v) } }

        assertTrue(values.isEmpty())
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values1.add(v) } }
        combined.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertEquals(listOf(120), values1)
        assertEquals(listOf(120), values2)
    }

    @Test
    fun `withLatestFrom with different types`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf("a", "b", "c"))
        val combined = source.withLatestFrom(other) { index, list ->
            list.getOrNull(index) ?: "N/A"
        }

        combined.subscribe { }

        assertEquals("N/A", combined.value) // index 5 out of bounds

        source.value = 1
        assertEquals("b", combined.value)

        other.value = listOf("x", "y", "z")
        source.value = 2
        assertEquals("z", combined.value)
    }

    @Test
    fun `toString shows value and state`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertTrue(combined.toString().contains("110"))
        assertTrue(combined.toString().contains("WithLatestFromSignal"))
    }
}
