package com.github.fenrur.signal.impl

import com.github.fenrur.signal.operators.pairwise
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for PairwiseSignal.
 * Note: PairwiseSignal returns Pair<T, T> so it cannot extend AbstractSignalTest<Signal<Int>>.
 */
class PairwiseSignalTest {

    // ==================== PairwiseSignal-specific tests ====================

    @Test
    fun `pairwise signal returns initial pair of same value`() {
        val source = DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertThat(pairwise.value).isEqualTo(10 to 10)
    }

    @Test
    fun `pairwise signal emits pairs of consecutive values`() {
        val source = DefaultMutableSignal(1)
        val pairwise = source.pairwise()

        // Subscribe to enable reactive tracking
        pairwise.subscribe { }

        source.value = 2
        assertThat(pairwise.value).isEqualTo(1 to 2)

        source.value = 3
        assertThat(pairwise.value).isEqualTo(2 to 3)

        source.value = 4
        assertThat(pairwise.value).isEqualTo(3 to 4)
    }

    @Test
    fun `pairwise signal notifies subscribers with pairs`() {
        val source = DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = CopyOnWriteArrayList<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }

        source.value = 2
        source.value = 3

        assertThat(pairs).containsExactly(1 to 1, 1 to 2, 2 to 3)
    }

    @Test
    fun `pairwise signal does not emit for same value`() {
        val source = DefaultMutableSignal(10)
        val pairwise = source.pairwise()
        val pairs = CopyOnWriteArrayList<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairs.clear()

        source.value = 10 // Same value, should not emit

        assertThat(pairs).isEmpty()
    }

    @Test
    fun `pairwise signal closes properly`() {
        val source = DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertThat(pairwise.isClosed).isFalse()

        pairwise.close()

        assertThat(pairwise.isClosed).isTrue()
    }

    @Test
    fun `pairwise signal stops receiving after close`() {
        val source = DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = CopyOnWriteArrayList<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairwise.close()
        pairs.clear()

        source.value = 2

        assertThat(pairs).isEmpty()
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = CopyOnWriteArrayList<Pair<Int, Int>>()

        val unsubscribe = pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairs.clear()

        source.value = 2
        unsubscribe()
        source.value = 3

        assertThat(pairs).containsExactly(1 to 2)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(10)
        val pairwise = source.pairwise()
        pairwise.close()

        val pairs = CopyOnWriteArrayList<Pair<Int, Int>>()
        val unsubscribe = pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }

        assertThat(pairs).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `pairwise with string values`() {
        val source = DefaultMutableSignal("a")
        val pairwise = source.pairwise()

        pairwise.subscribe { }

        source.value = "b"
        assertThat(pairwise.value).isEqualTo("a" to "b")

        source.value = "c"
        assertThat(pairwise.value).isEqualTo("b" to "c")
    }

    @Test
    fun `pairwise with complex objects`() {
        data class Point(val x: Int, val y: Int)

        val source = DefaultMutableSignal(Point(0, 0))
        val pairwise = source.pairwise()

        pairwise.subscribe { }

        source.value = Point(1, 1)
        assertThat(pairwise.value).isEqualTo(Point(0, 0) to Point(1, 1))

        source.value = Point(2, 2)
        assertThat(pairwise.value).isEqualTo(Point(1, 1) to Point(2, 2))
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs1 = CopyOnWriteArrayList<Pair<Int, Int>>()
        val pairs2 = CopyOnWriteArrayList<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs1.add(v) } }
        pairwise.subscribe { it.onSuccess { v -> pairs2.add(v) } }
        pairs1.clear()
        pairs2.clear()

        source.value = 2

        assertThat(pairs1).containsExactly(1 to 2)
        assertThat(pairs2).containsExactly(1 to 2)
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertThat(pairwise.toString()).contains("10")
        assertThat(pairwise.toString()).contains("PairwiseSignal")
    }
}
