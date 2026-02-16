package io.github.fenrur.signal.impl

import io.github.fenrur.signal.operators.scan
import kotlin.test.*

/**
 * Tests for ScanSignal.
 */
class ScanSignalTest {

    @Test
    fun `scan signal applies accumulator to initial value`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val scan = source.scan(0) { acc, value -> acc + value }

        // Initial: accumulator(0, 10) = 10
        assertEquals(10, scan.value)
    }

    @Test
    fun `scan signal accumulates values`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(0) { acc, value -> acc + value }

        // Subscribe to enable reactive updates
        scan.subscribe { }

        assertEquals(1, scan.value) // 0 + 1

        source.value = 2
        assertEquals(3, scan.value) // 1 + 2

        source.value = 3
        assertEquals(6, scan.value) // 3 + 3

        source.value = 4
        assertEquals(10, scan.value) // 6 + 4
    }

    @Test
    fun `scan signal notifies subscribers with accumulated values`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(0) { acc, value -> acc + value }
        val values = mutableListOf<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 2
        source.value = 3

        assertEquals(listOf(1, 3, 6), values)
    }

    @Test
    fun `scan signal does not emit for same value`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val scan = source.scan(0) { acc, value -> acc + value }
        val values = mutableListOf<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 10 // Same value, should not emit

        assertTrue(values.isEmpty())
    }

    @Test
    fun `scan signal can change type`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan("") { acc, value -> "$acc$value" }

        assertEquals("1", scan.value)

        scan.subscribe { }

        source.value = 2
        assertEquals("12", scan.value)

        source.value = 3
        assertEquals("123", scan.value)
    }

    @Test
    fun `scan signal with multiplication`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val scan = source.scan(1) { acc, value -> acc * value }

        scan.subscribe { }

        assertEquals(2, scan.value) // 1 * 2

        source.value = 3
        assertEquals(6, scan.value) // 2 * 3

        source.value = 4
        assertEquals(24, scan.value) // 6 * 4
    }

    @Test
    fun `scan signal with list accumulation`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(emptyList<Int>()) { acc, value -> acc + value }

        scan.subscribe { }

        assertEquals(listOf(1), scan.value)

        source.value = 2
        assertEquals(listOf(1, 2), scan.value)

        source.value = 3
        assertEquals(listOf(1, 2, 3), scan.value)
    }

    @Test
    fun `scan signal stops receiving after close`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(0) { acc, value -> acc + value }
        val values = mutableListOf<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }
        scan.close()
        values.clear()

        source.value = 2

        assertTrue(values.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(0) { acc, value -> acc + value }
        val values = mutableListOf<Int>()

        val unsubscribe = scan.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 2
        unsubscribe()
        source.value = 3

        assertEquals(listOf(3), values)
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val scan = source.scan(0) { acc, value -> acc + value }
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        scan.subscribe { it.onSuccess { v -> values1.add(v) } }
        scan.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 2

        assertEquals(listOf(3), values1)
        assertEquals(listOf(3), values2)
    }

    @Test
    fun `scan with max accumulator`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val scan = source.scan(Int.MIN_VALUE) { acc, value -> maxOf(acc, value) }

        scan.subscribe { }

        assertEquals(5, scan.value)

        source.value = 3
        assertEquals(5, scan.value) // Max is still 5

        source.value = 10
        assertEquals(10, scan.value)

        source.value = 7
        assertEquals(10, scan.value) // Max is still 10
    }

    @Test
    fun `toString shows value and state`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val scan = source.scan(0) { acc, value -> acc + value }

        assertTrue(scan.toString().contains("10"))
        assertTrue(scan.toString().contains("ScanSignal"))
    }
}
