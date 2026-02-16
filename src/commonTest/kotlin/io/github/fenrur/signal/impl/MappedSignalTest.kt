package io.github.fenrur.signal.impl

import io.github.fenrur.signal.AbstractSignalTest
import io.github.fenrur.signal.Signal
import kotlin.test.*

class MappedSignalTest : io.github.fenrur.signal.AbstractSignalTest<io.github.fenrur.signal.Signal<Int>>() {

    override fun createSignal(initial: Int): io.github.fenrur.signal.Signal<Int> {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(initial)
        return _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it }
    }

    // ==================== MappedSignal-specific tests ====================

    @Test
    fun `mapped signal transforms value`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it * 2 }

        assertEquals(20, mapped.value)
    }

    @Test
    fun `mapped signal updates when source changes`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it * 2 }

        source.value = 20

        assertEquals(40, mapped.value)
    }

    @Test
    fun `mapped signal notifies subscribers`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it * 2 }
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 20
        source.value = 30

        assertEquals(listOf(20, 40, 60), values)
    }

    @Test
    fun `mapped signal does not notify if transformed value is same`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it / 10 }
        var callCount = 0

        mapped.subscribe { callCount++ }

        source.value = 15

        assertEquals(1, callCount)
    }

    @Test
    fun `mapped signal can chain transformations`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val doubled = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it * 2 }
        val stringified = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(doubled) { "Value: $it" }

        assertEquals("Value: 20", stringified.value)

        source.value = 5
        assertEquals("Value: 10", stringified.value)
    }

    @Test
    fun `mapped signal stops receiving after close`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { it * 2 }
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        mapped.close()
        values.clear()

        source.value = 20

        assertTrue(values.isEmpty())
    }

    @Test
    fun `mapped signal can transform to different type`() {
        val source = _root_ide_package_.io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val mapped: io.github.fenrur.signal.Signal<String> =
            _root_ide_package_.io.github.fenrur.signal.impl.MappedSignal(source) { "Number: $it" }

        assertEquals("Number: 42", mapped.value)
    }
}
