package io.github.fenrur.signal.impl

import io.github.fenrur.signal.operators.pairwise
import kotlin.test.*

/**
 * Tests for PairwiseSignal.
 * Note: PairwiseSignal returns Pair<T, T> so it cannot extend AbstractSignalTest<Signal<Int>>.
 */
class PairwiseSignalTest {

    // ==================== PairwiseSignal-specific tests ====================

    @Test
    fun `pairwise signal returns initial pair of same value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertEquals(10 to 10, pairwise.value)
    }

    @Test
    fun `pairwise signal emits pairs of consecutive values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairwise = source.pairwise()

        // Subscribe to enable reactive tracking
        pairwise.subscribe { }

        source.value = 2
        assertEquals(1 to 2, pairwise.value)

        source.value = 3
        assertEquals(2 to 3, pairwise.value)

        source.value = 4
        assertEquals(3 to 4, pairwise.value)
    }

    @Test
    fun `pairwise signal notifies subscribers with pairs`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = mutableListOf<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }

        source.value = 2
        source.value = 3

        assertEquals(listOf(1 to 1, 1 to 2, 2 to 3), pairs)
    }

    @Test
    fun `pairwise signal does not emit for same value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val pairwise = source.pairwise()
        val pairs = mutableListOf<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairs.clear()

        source.value = 10 // Same value, should not emit

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `pairwise signal closes properly`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertFalse(pairwise.isClosed)

        pairwise.close()

        assertTrue(pairwise.isClosed)
    }

    @Test
    fun `pairwise signal stops receiving after close`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = mutableListOf<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairwise.close()
        pairs.clear()

        source.value = 2

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs = mutableListOf<Pair<Int, Int>>()

        val unsubscribe = pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }
        pairs.clear()

        source.value = 2
        unsubscribe()
        source.value = 3

        assertEquals(listOf(1 to 2), pairs)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val pairwise = source.pairwise()
        pairwise.close()

        val pairs = mutableListOf<Pair<Int, Int>>()
        val unsubscribe = pairwise.subscribe { it.onSuccess { v -> pairs.add(v) } }

        assertTrue(pairs.isEmpty())
        unsubscribe() // Should not throw
    }

    @Test
    fun `pairwise with string values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("a")
        val pairwise = source.pairwise()

        pairwise.subscribe { }

        source.value = "b"
        assertEquals("a" to "b", pairwise.value)

        source.value = "c"
        assertEquals("b" to "c", pairwise.value)
    }

    @Test
    fun `pairwise with complex objects`() {
        data class Point(val x: Int, val y: Int)

        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(Point(0, 0))
        val pairwise = source.pairwise()

        pairwise.subscribe { }

        source.value = Point(1, 1)
        assertEquals(Point(0, 0) to Point(1, 1), pairwise.value)

        source.value = Point(2, 2)
        assertEquals(Point(1, 1) to Point(2, 2), pairwise.value)
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairwise = source.pairwise()
        val pairs1 = mutableListOf<Pair<Int, Int>>()
        val pairs2 = mutableListOf<Pair<Int, Int>>()

        pairwise.subscribe { it.onSuccess { v -> pairs1.add(v) } }
        pairwise.subscribe { it.onSuccess { v -> pairs2.add(v) } }
        pairs1.clear()
        pairs2.clear()

        source.value = 2

        assertEquals(listOf(1 to 2), pairs1)
        assertEquals(listOf(1 to 2), pairs2)
    }

    @Test
    fun `toString shows value and state`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val pairwise = source.pairwise()

        assertTrue(pairwise.toString().contains("10"))
        assertTrue(pairwise.toString().contains("PairwiseSignal"))
    }
}
