package com.github.fenrur.signal.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class MapNotNullSignalTest {

    @Test
    fun `mapNotNull signal returns transformed initial value`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }

        assertThat(mapped.value).isEqualTo(20)
    }

    @Test
    fun `mapNotNull signal throws if initial value transforms to null`() {
        val source = DefaultMutableSignal(3)

        assertThatThrownBy {
            MapNotNullSignal(source) { if (it > 5) it * 2 else null }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("mapNotNull requires initial value to transform to non-null")
    }

    @Test
    fun `mapNotNull signal retains last non-null value when transform returns null`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { if (it > 5) it * 2 else null }

        assertThat(mapped.value).isEqualTo(20)

        source.value = 3 // Transform returns null

        assertThat(mapped.value).isEqualTo(20) // Retains previous value
    }

    @Test
    fun `mapNotNull signal updates when transform returns non-null`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { if (it > 5) it * 2 else null }

        source.value = 15

        assertThat(mapped.value).isEqualTo(30)
    }

    @Test
    fun `mapNotNull signal notifies only for non-null values`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { if (it > 5) it * 2 else null }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20 // Non-null (40)
        source.value = 3  // Null - should not notify
        source.value = 15 // Non-null (30)

        assertThat(values).containsExactly(40, 30)
    }

    @Test
    fun `mapNotNull signal with type transformation`() {
        val source = DefaultMutableSignal("42")
        val mapped = MapNotNullSignal(source) { it.toIntOrNull() }

        assertThat(mapped.value).isEqualTo(42)

        source.value = "invalid" // toIntOrNull returns null
        assertThat(mapped.value).isEqualTo(42) // Retains previous

        source.value = "100"
        assertThat(mapped.value).isEqualTo(100)
    }

    @Test
    fun `mapNotNull signal closes properly`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }

        assertThat(mapped.isClosed).isFalse()

        mapped.close()

        assertThat(mapped.isClosed).isTrue()
    }

    @Test
    fun `mapNotNull signal stops receiving after close`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        mapped.close()
        values.clear()

        source.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertThat(values).containsExactly(40)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }
        mapped.close()

        val values = CopyOnWriteArrayList<Int>()
        val unsubscribe = mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        assertThat(values).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onSuccess { v -> values1.add(v) } }
        mapped.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertThat(values1).containsExactly(40)
        assertThat(values2).containsExactly(40)
    }

    @Test
    fun `mapNotNull with nullable source type`() {
        val source = DefaultMutableSignal<Int?>(10)
        val mapped = MapNotNullSignal(source) { it?.let { v -> v * 2 } }

        assertThat(mapped.value).isEqualTo(20)

        source.value = null // Transform returns null
        assertThat(mapped.value).isEqualTo(20) // Retains previous

        source.value = 15
        assertThat(mapped.value).isEqualTo(30)
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(10)
        val mapped = MapNotNullSignal(source) { it * 2 }

        assertThat(mapped.toString()).contains("20")
        assertThat(mapped.toString()).contains("MapNotNullSignal")
    }
}
