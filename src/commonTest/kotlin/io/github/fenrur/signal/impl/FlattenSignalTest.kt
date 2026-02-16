package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.Signal
import kotlin.test.*

class FlattenSignalTest : io.github.fenrur.signal.AbstractSignalTest<io.github.fenrur.signal.Signal<Int>>() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.Signal<Int> {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(initial)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        return io.github.fenrur.signal.impl.FlattenSignal(outer)
    }

    // ==================== FlattenSignal-specific tests ====================

    @Test
    fun `flatten signal returns inner signal value`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)

        assertEquals(42, flattened.value)
    }

    @Test
    fun `flatten signal updates when inner signal changes`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)

        inner.value = 20

        assertEquals(20, flattened.value)
    }

    @Test
    fun `flatten signal switches to new inner signal`() {
        val inner1 = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val inner2 = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner1 as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)

        assertEquals(10, flattened.value)

        outer.value = inner2

        assertEquals(20, flattened.value)
    }

    @Test
    fun `flatten signal notifies when inner signal value changes`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        inner.value = 20
        inner.value = 30

        assertEquals(listOf(20, 30), values)
    }

    @Test
    fun `flatten signal notifies when switching inner signals`() {
        val inner1 = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val inner2 = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner1 as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        outer.value = inner2

        assertTrue(values.contains(100))
    }

    @Test
    fun `flatten signal stops receiving from old inner after switch`() {
        val inner1 = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val inner2 = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner1 as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }

        outer.value = inner2
        values.clear()

        inner1.value = 999

        assertTrue(values.isEmpty())
        assertEquals(100, flattened.value)
    }

    @Test
    fun `flatten signal receives from new inner after switch`() {
        val inner1 = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val inner2 = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner1 as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }

        outer.value = inner2
        values.clear()

        inner2.value = 200

        assertEquals(listOf(200), values)
    }

    @Test
    fun `flatten signal stops receiving after close`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        flattened.close()
        values.clear()

        inner.value = 20

        assertTrue(values.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values = mutableListOf<Int>()

        val unsubscribe = flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        inner.value = 20
        unsubscribe()
        inner.value = 30

        assertEquals(listOf(20), values)
    }

    @Test
    fun `flatten with nested read-only signals`() {
        val inner1: io.github.fenrur.signal.Signal<Int> =
            io.github.fenrur.signal.impl.DefaultSignal(10)
        val inner2: io.github.fenrur.signal.Signal<Int> =
            io.github.fenrur.signal.impl.DefaultSignal(20)
        val outer = io.github.fenrur.signal.impl.DefaultMutableSignal(inner1)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)

        assertEquals(10, flattened.value)

        outer.value = inner2

        assertEquals(20, flattened.value)
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        flattened.subscribe { it.onSuccess { v -> values1.add(v) } }
        flattened.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        inner.value = 20

        assertEquals(listOf(20), values1)
        assertEquals(listOf(20), values2)
    }

    @Test
    fun `toString shows value and state`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = io.github.fenrur.signal.impl.FlattenSignal(outer)

        assertTrue(flattened.toString().contains("42"))
        assertTrue(flattened.toString().contains("FlattenSignal"))
    }
}
