package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.Signal
import kotlin.test.*

class FilteredSignalTest : io.github.fenrur.signal.AbstractSignalTest<io.github.fenrur.signal.Signal<Int>>() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.Signal<Int> {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(initial)
        return _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 0 }
    }

    // ==================== FilteredSignal-specific tests ====================

    @Test
    fun `filtered signal returns initial value when predicate matches`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }

        assertEquals(10, filtered.value)
    }

    @Test
    fun `filtered signal retains last matching value when predicate fails`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }

        source.value = 3

        assertEquals(10, filtered.value)
    }

    @Test
    fun `filtered signal updates when new value matches predicate`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }

        source.value = 20

        assertEquals(20, filtered.value)
    }

    @Test
    fun `filtered signal notifies subscribers only for matching values`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }
        val values = mutableListOf<Int>()

        filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        source.value = 3
        source.value = 15

        assertEquals(listOf(20, 15), values)
    }

    @Test
    fun `filtered signal does not notify for non-matching initial value changes`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }
        var callCount = 0

        filtered.subscribe { callCount++ }

        source.value = 3

        assertEquals(1, callCount)
    }

    @Test
    fun `filtered signal stops receiving after close`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }
        val values = mutableListOf<Int>()

        filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        filtered.close()
        values.clear()

        source.value = 20

        assertTrue(values.isEmpty())
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }
        val values = mutableListOf<Int>()

        val unsubscribe = filtered.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20
        unsubscribe()
        source.value = 30

        assertEquals(listOf(20), values)
    }

    @Test
    fun `multiple subscribers receive notifications independently`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()

        filtered.subscribe { it.onSuccess { v -> values1.add(v) } }
        filtered.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = 20

        assertEquals(listOf(20), values1)
        assertEquals(listOf(20), values2)
    }

    @Test
    fun `toString shows value and state`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it > 5 }

        assertTrue(filtered.toString().contains("10"))
        assertTrue(filtered.toString().contains("FilteredSignal"))
    }

    @Test
    fun `filtered signal with complex predicate`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val filtered = _root_ide_package_.io.github.fenrur.signal.impl.FilteredSignal(source) { it.length > 3 }

        assertEquals("hello", filtered.value)

        source.value = "hi"
        assertEquals("hello", filtered.value)

        source.value = "world"
        assertEquals("world", filtered.value)
    }
}
