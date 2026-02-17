package io.github.fenrur.signal.impl

import kotlin.test.*

class BimappedSignalTest {

    @Test
    fun `bimapped signal reads with forward transform`() {
        val source = DefaultMutableSignal("42")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertEquals(42, bimapped.value)
    }

    @Test
    fun `bimapped signal writes with reverse transform`() {
        val source = DefaultMutableSignal("42")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.value = 100

        assertEquals("100", source.value)
        assertEquals(100, bimapped.value)
    }

    @Test
    fun `bimapped signal update applies both transforms`() {
        val source = DefaultMutableSignal("10")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.update { it + 5 }

        assertEquals("15", source.value)
        assertEquals(15, bimapped.value)
    }

    @Test
    fun `bimapped signal notifies subscribers on source change`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = mutableListOf<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = "2"
        source.value = "3"

        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun `bimapped signal notifies subscribers on mapped write`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = mutableListOf<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        bimapped.value = 10
        bimapped.value = 20

        assertEquals(listOf(1, 10, 20), values)
    }

    @Test
    fun `bimapped signal closes properly`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertFalse(bimapped.isClosed)

        bimapped.close()

        assertTrue(bimapped.isClosed)
    }

    @Test
    fun `bimapped signal stops notifications after close`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = mutableListOf<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }
        bimapped.close()

        source.value = "99"

        assertTrue(bimapped.isClosed)
        assertEquals(listOf(1), values) // Only initial value
    }

    @Test
    fun `bimapped signal write on closed signal does nothing`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.close()
        bimapped.value = 99

        assertEquals("1", source.value)
    }

    @Test
    fun `bimapped signal update on closed signal does nothing`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.close()
        bimapped.update { it + 100 }

        assertEquals("1", source.value)
    }

    @Test
    fun `bimapped signal can be used as property delegate`() {
        val source = DefaultMutableSignal("5")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        var prop by bimapped
        assertEquals(5, prop)

        prop = 42
        assertEquals("42", source.value)
    }

    @Test
    fun `bimapped signal chaining works`() {
        val source = DefaultMutableSignal(10)
        val doubled = BimappedSignal(source, { it * 2 }, { it / 2 })
        val asString =
            BimappedSignal(doubled, { it.toString() }, { it.toInt() })

        assertEquals("20", asString.value)

        asString.value = "100"
        assertEquals(100, doubled.value)
        assertEquals(50, source.value)
    }

    @Test
    fun `bimapped signal with identity transforms is pass-through`() {
        val source = DefaultMutableSignal(42)
        val identity = BimappedSignal(source, { it }, { it })

        assertEquals(42, identity.value)
        identity.value = 100
        assertEquals(100, source.value)
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = mutableListOf<Int>()

        val unsubscribe = bimapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = "10"
        unsubscribe()
        source.value = "20"

        assertEquals(listOf(10), values)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal("1")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })
        bimapped.close()

        val values = mutableListOf<Int>()
        val unsubscribe = bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        assertTrue(values.isEmpty())
        unsubscribe() // Should not throw
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal("42")
        val bimapped =
            BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertTrue(bimapped.toString().contains("42"))
        assertTrue(bimapped.toString().contains("BimappedSignal"))
    }
}
