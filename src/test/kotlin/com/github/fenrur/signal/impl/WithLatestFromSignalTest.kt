package com.github.fenrur.signal.impl

import com.github.fenrur.signal.operators.withLatestFrom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class WithLatestFromSignalTest {

    @Test
    fun `withLatestFrom signal returns combined initial value`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertThat(combined.value).isEqualTo(110)
    }

    @Test
    fun `withLatestFrom signal emits when source changes`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20

        assertThat(values).containsExactly(120) // 20 + 100
    }

    @Test
    fun `withLatestFrom signal does not emit when only other changes`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        other.value = 200 // Should NOT trigger emission

        assertThat(values).isEmpty()
    }

    @Test
    fun `withLatestFrom signal samples latest from other on source change`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        other.value = 200 // Change other first
        source.value = 20 // Now source triggers with latest from other

        assertThat(values).containsExactly(220) // 20 + 200
    }

    @Test
    fun `withLatestFrom signal value reflects current state`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertThat(combined.value).isEqualTo(110)

        other.value = 200
        assertThat(combined.value).isEqualTo(210) // Reading value samples current state

        source.value = 20
        assertThat(combined.value).isEqualTo(220)
    }

    @Test
    fun `withLatestFrom signal with pair combiner`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal("hello")
        val combined = source.withLatestFrom(other) { a, b -> a to b }

        assertThat(combined.value).isEqualTo(10 to "hello")

        source.value = 20
        assertThat(combined.value).isEqualTo(20 to "hello")

        other.value = "world"
        source.value = 30
        assertThat(combined.value).isEqualTo(30 to "world")
    }

    @Test
    fun `withLatestFrom signal multiple source changes`() {
        val source = DefaultMutableSignal(1)
        val other = DefaultMutableSignal(10)
        val combined = source.withLatestFrom(other) { a, b -> a * b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 2
        source.value = 3
        source.value = 4

        assertThat(values).containsExactly(10, 20, 30, 40) // Initial and updates
    }

    @Test
    fun `withLatestFrom signal closes properly`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertThat(combined.isClosed).isFalse()

        combined.close()

        assertThat(combined.isClosed).isTrue()
    }

    @Test
    fun `withLatestFrom signal stops receiving after close`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        combined.close()
        values.clear()

        source.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertThat(values).containsExactly(120)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        combined.close()

        val values = CopyOnWriteArrayList<Int>()
        val unsubscribe = combined.subscribe { it.onSuccess { v -> values.add(v) } }

        assertThat(values).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onSuccess { v -> values1.add(v) } }
        combined.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertThat(values1).containsExactly(120)
        assertThat(values2).containsExactly(120)
    }

    @Test
    fun `withLatestFrom with different types`() {
        val source = DefaultMutableSignal(5)
        val other = DefaultMutableSignal(listOf("a", "b", "c"))
        val combined = source.withLatestFrom(other) { index, list ->
            list.getOrNull(index) ?: "N/A"
        }

        combined.subscribe { }

        assertThat(combined.value).isEqualTo("N/A") // index 5 out of bounds

        source.value = 1
        assertThat(combined.value).isEqualTo("b")

        other.value = listOf("x", "y", "z")
        source.value = 2
        assertThat(combined.value).isEqualTo("z")
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(10)
        val other = DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertThat(combined.toString()).contains("110")
        assertThat(combined.toString()).contains("WithLatestFromSignal")
    }
}
