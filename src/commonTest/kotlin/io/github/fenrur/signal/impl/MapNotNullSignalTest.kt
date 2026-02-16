package io.github.fenrur.signal.impl

import io.github.fenrur.signal.operators.mapNotNull
import kotlin.test.*

class MapNotNullSignalTest {

    @Test
    fun `mapNotNull signal returns transformed initial value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }

        assertEquals(20, mapped.value)
    }

    @Test
    fun `mapNotNull signal throws if initial value transforms to null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(3)

        assertFailsWith<IllegalStateException> {
            source.mapNotNull { if (it > 5) it * 2 else null }
        }
    }

    @Test
    fun `mapNotNull signal retains last non-null value when transform returns null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        assertEquals(20, mapped.value)

        source.value = 3 // Transform returns null

        assertEquals(20, mapped.value) // Retains previous value
    }

    @Test
    fun `mapNotNull signal updates when transform returns non-null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        source.value = 15

        assertEquals(30, mapped.value)
    }

    @Test
    fun `mapNotNull signal notifies only for non-null values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20 // Non-null (40)
        source.value = 3  // Null - should not notify
        source.value = 15 // Non-null (30)

        assertEquals(listOf(40, 30), values)
    }

    @Test
    fun `mapNotNull signal with type transformation`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("42")
        val mapped = source.mapNotNull { it.toIntOrNull() }

        assertEquals(42, mapped.value)

        source.value = "invalid" // toIntOrNull returns null
        assertEquals(42, mapped.value) // Retains previous

        source.value = "100"
        assertEquals(100, mapped.value)
    }

    @Test
    fun `mapNotNull signal closes properly`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }

        assertFalse(mapped.isClosed)

        mapped.close()

        assertTrue(mapped.isClosed)
    }

    @Test
    fun `mapNotNull signal stops receiving after close`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        mapped.close()
        values.clear()

        source.value = 20

        assertTrue(values.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }
        val values = mutableListOf<Int>()

        val unsubscribe = mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertEquals(listOf(40), values)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }
        mapped.close()

        val values = mutableListOf<Int>()
        val unsubscribe = mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        assertTrue(values.isEmpty())
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values1.add(v) } }
        mapped.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertEquals(listOf(40), values1)
        assertEquals(listOf(40), values2)
    }

    @Test
    fun `mapNotNull with nullable source type`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(10)
        val mapped = source.mapNotNull { it?.let { v -> v * 2 } }

        assertEquals(20, mapped.value)

        source.value = null // Transform returns null
        assertEquals(20, mapped.value) // Retains previous

        source.value = 15
        assertEquals(30, mapped.value)
    }

    @Test
    fun `toString shows value and state`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { it * 2 }

        assertTrue(mapped.toString().contains("20"))
        assertTrue(mapped.toString().contains("MapNotNullSignal"))
    }
}
