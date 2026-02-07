package com.github.fenrur.signal.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class ScanSignalTest {

    @Test
    fun `scan signal applies accumulator to initial value`() {
        val source = DefaultMutableSignal(10)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }

        // Initial: accumulator(0, 10) = 10
        assertThat(scan.value).isEqualTo(10)
    }

    @Test
    fun `scan signal accumulates values`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }

        // Subscribe to enable reactive updates
        scan.subscribe { }

        assertThat(scan.value).isEqualTo(1) // 0 + 1

        source.value = 2
        assertThat(scan.value).isEqualTo(3) // 1 + 2

        source.value = 3
        assertThat(scan.value).isEqualTo(6) // 3 + 3

        source.value = 4
        assertThat(scan.value).isEqualTo(10) // 6 + 4
    }

    @Test
    fun `scan signal notifies subscribers with accumulated values`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        val values = CopyOnWriteArrayList<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 2
        source.value = 3

        assertThat(values).containsExactly(1, 3, 6)
    }

    @Test
    fun `scan signal does not emit for same value`() {
        val source = DefaultMutableSignal(10)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        val values = CopyOnWriteArrayList<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 10 // Same value, should not emit

        assertThat(values).isEmpty()
    }

    @Test
    fun `scan signal can change type`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, "") { acc, value -> "$acc$value" }

        assertThat(scan.value).isEqualTo("1")

        scan.subscribe { }

        source.value = 2
        assertThat(scan.value).isEqualTo("12")

        source.value = 3
        assertThat(scan.value).isEqualTo("123")
    }

    @Test
    fun `scan signal with multiplication`() {
        val source = DefaultMutableSignal(2)
        val scan = ScanSignal(source, 1) { acc, value -> acc * value }

        scan.subscribe { }

        assertThat(scan.value).isEqualTo(2) // 1 * 2

        source.value = 3
        assertThat(scan.value).isEqualTo(6) // 2 * 3

        source.value = 4
        assertThat(scan.value).isEqualTo(24) // 6 * 4
    }

    @Test
    fun `scan signal with list accumulation`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, emptyList<Int>()) { acc, value -> acc + value }

        scan.subscribe { }

        assertThat(scan.value).isEqualTo(listOf(1))

        source.value = 2
        assertThat(scan.value).isEqualTo(listOf(1, 2))

        source.value = 3
        assertThat(scan.value).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun `scan signal closes properly`() {
        val source = DefaultMutableSignal(10)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }

        assertThat(scan.isClosed).isFalse()

        scan.close()

        assertThat(scan.isClosed).isTrue()
    }

    @Test
    fun `scan signal stops receiving after close`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        val values = CopyOnWriteArrayList<Int>()

        scan.subscribe { it.onSuccess { v -> values.add(v) } }
        scan.close()
        values.clear()

        source.value = 2

        assertThat(values).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = scan.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 2
        unsubscribe()
        source.value = 3

        assertThat(values).containsExactly(3)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        scan.close()

        val values = CopyOnWriteArrayList<Int>()
        val unsubscribe = scan.subscribe { it.onSuccess { v -> values.add(v) } }

        assertThat(values).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(1)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()

        scan.subscribe { it.onSuccess { v -> values1.add(v) } }
        scan.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 2

        assertThat(values1).containsExactly(3)
        assertThat(values2).containsExactly(3)
    }

    @Test
    fun `scan with max accumulator`() {
        val source = DefaultMutableSignal(5)
        val scan = ScanSignal(source, Int.MIN_VALUE) { acc, value -> maxOf(acc, value) }

        scan.subscribe { }

        assertThat(scan.value).isEqualTo(5)

        source.value = 3
        assertThat(scan.value).isEqualTo(5) // Max is still 5

        source.value = 10
        assertThat(scan.value).isEqualTo(10)

        source.value = 7
        assertThat(scan.value).isEqualTo(10) // Max is still 10
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(10)
        val scan = ScanSignal(source, 0) { acc, value -> acc + value }

        assertThat(scan.toString()).contains("10")
        assertThat(scan.toString()).contains("ScanSignal")
    }
}
