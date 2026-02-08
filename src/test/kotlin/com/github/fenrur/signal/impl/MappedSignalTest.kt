package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for MappedSignal.
 * Extends AbstractSignalTest for common Signal interface tests.
 */
class MappedSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = DefaultMutableSignal(initial)
        return MappedSignal(source) { it } // Identity mapping for abstract test compatibility
    }

    // ==================== MappedSignal-specific tests ====================

    @Test
    fun `mapped signal transforms value`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }

        assertThat(mapped.value).isEqualTo(20)
    }

    @Test
    fun `mapped signal updates when source changes`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }

        source.value = 20

        assertThat(mapped.value).isEqualTo(40)
    }

    @Test
    fun `mapped signal notifies subscribers`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 20
        source.value = 30

        assertThat(values).containsExactly(20, 40, 60)
    }

    @Test
    fun `mapped signal does not notify if transformed value is same`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it / 10 } // 10 -> 1, 15 -> 1
        val callCount = AtomicInteger(0)

        mapped.subscribe { callCount.incrementAndGet() }

        source.value = 15 // Still maps to 1

        assertThat(callCount.get()).isEqualTo(1) // Only initial
    }

    @Test
    fun `mapped signal can chain transformations`() {
        val source = DefaultMutableSignal(10)
        val doubled = MappedSignal(source) { it * 2 }
        val stringified = MappedSignal(doubled) { "Value: $it" }

        assertThat(stringified.value).isEqualTo("Value: 20")

        source.value = 5
        assertThat(stringified.value).isEqualTo("Value: 10")
    }

    @Test
    fun `mapped signal stops receiving after close`() {
        val source = DefaultMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        mapped.close()
        values.clear()

        source.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `mapped signal can transform to different type`() {
        val source = DefaultMutableSignal(42)
        val mapped: Signal<String> = MappedSignal(source) { "Number: $it" }

        assertThat(mapped.value).isEqualTo("Number: 42")
    }
}
