package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for FilteredSignal.
 * Extends AbstractSignalTest for common Signal interface tests.
 */
class FilteredSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = DefaultMutableSignal(initial)
        return FilteredSignal(source) { it > 0 } // Filter passes positive values
    }

    // ==================== FilteredSignal-specific tests ====================

    @Test
    fun `filtered signal returns initial value when predicate matches`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }

        assertThat(filtered.value).isEqualTo(10)
    }

    @Test
    fun `filtered signal retains last matching value when predicate fails`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }

        source.value = 3 // Does not match predicate

        assertThat(filtered.value).isEqualTo(10) // Retains previous matching value
    }

    @Test
    fun `filtered signal updates when new value matches predicate`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }

        source.value = 20

        assertThat(filtered.value).isEqualTo(20)
    }

    @Test
    fun `filtered signal notifies subscribers only for matching values`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }
        val values = CopyOnWriteArrayList<Int>()

        filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20 // Matches
        source.value = 3  // Does not match
        source.value = 15 // Matches

        assertThat(values).containsExactly(20, 15)
    }

    @Test
    fun `filtered signal does not notify for non-matching initial value changes`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }
        val callCount = AtomicInteger(0)

        filtered.subscribe { callCount.incrementAndGet() }

        source.value = 3 // Does not match

        assertThat(callCount.get()).isEqualTo(1) // Only initial notification
    }

    @Test
    fun `filtered signal stops receiving after close`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }
        val values = CopyOnWriteArrayList<Int>()

        filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        filtered.close()
        values.clear()

        source.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertThat(values).containsExactly(20)
    }

    @Test
    fun `multiple subscribers receive notifications independently`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()

        filtered.subscribe { it.onSuccess { v -> values1.add(v) } }
        filtered.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertThat(values1).containsExactly(20)
        assertThat(values2).containsExactly(20)
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { it > 5 }

        assertThat(filtered.toString()).contains("10")
        assertThat(filtered.toString()).contains("FilteredSignal")
    }

    @Test
    fun `filtered signal with complex predicate`() {
        val source = DefaultMutableSignal("hello")
        val filtered = FilteredSignal(source) { it.length > 3 }

        assertThat(filtered.value).isEqualTo("hello")

        source.value = "hi" // Does not match (length 2)
        assertThat(filtered.value).isEqualTo("hello")

        source.value = "world" // Matches (length 5)
        assertThat(filtered.value).isEqualTo("world")
    }
}
