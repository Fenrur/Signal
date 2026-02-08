package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for FlattenSignal.
 * Extends AbstractSignalTest for common Signal interface tests.
 */
class FlattenSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val inner = DefaultMutableSignal(initial)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        return FlattenSignal(outer)
    }

    // ==================== FlattenSignal-specific tests ====================

    @Test
    fun `flatten signal returns inner signal value`() {
        val inner = DefaultMutableSignal(42)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)

        assertThat(flattened.value).isEqualTo(42)
    }

    @Test
    fun `flatten signal updates when inner signal changes`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)

        inner.value = 20

        assertThat(flattened.value).isEqualTo(20)
    }

    @Test
    fun `flatten signal switches to new inner signal`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(20)
        val outer = DefaultMutableSignal(inner1 as Signal<Int>)
        val flattened = FlattenSignal(outer)

        assertThat(flattened.value).isEqualTo(10)

        outer.value = inner2

        assertThat(flattened.value).isEqualTo(20)
    }

    @Test
    fun `flatten signal notifies when inner signal value changes`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        inner.value = 20
        inner.value = 30

        assertThat(values).containsExactly(20, 30)
    }

    @Test
    fun `flatten signal notifies when switching inner signals`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(100)
        val outer = DefaultMutableSignal(inner1 as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        outer.value = inner2

        assertThat(values).contains(100)
    }

    @Test
    fun `flatten signal stops receiving from old inner after switch`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(100)
        val outer = DefaultMutableSignal(inner1 as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }

        outer.value = inner2
        values.clear()

        inner1.value = 999 // Should not trigger notification

        assertThat(values).isEmpty()
        assertThat(flattened.value).isEqualTo(100)
    }

    @Test
    fun `flatten signal receives from new inner after switch`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(100)
        val outer = DefaultMutableSignal(inner1 as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }

        outer.value = inner2
        values.clear()

        inner2.value = 200

        assertThat(values).containsExactly(200)
    }

    @Test
    fun `flatten signal stops receiving after close`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        flattened.close()
        values.clear()

        inner.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = flattened.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        inner.value = 20
        unsubscribe()
        inner.value = 30

        assertThat(values).containsExactly(20)
    }

    @Test
    fun `flatten with nested read-only signals`() {
        val inner1: Signal<Int> = DefaultSignal(10)
        val inner2: Signal<Int> = DefaultSignal(20)
        val outer = DefaultMutableSignal(inner1)
        val flattened = FlattenSignal(outer)

        assertThat(flattened.value).isEqualTo(10)

        outer.value = inner2

        assertThat(flattened.value).isEqualTo(20)
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val inner = DefaultMutableSignal(10)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()

        flattened.subscribe { it.onSuccess { v -> values1.add(v) } }
        flattened.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        inner.value = 20

        assertThat(values1).containsExactly(20)
        assertThat(values2).containsExactly(20)
    }

    @Test
    fun `toString shows value and state`() {
        val inner = DefaultMutableSignal(42)
        val outer = DefaultMutableSignal(inner as Signal<Int>)
        val flattened = FlattenSignal(outer)

        assertThat(flattened.toString()).contains("42")
        assertThat(flattened.toString()).contains("FlattenSignal")
    }
}
