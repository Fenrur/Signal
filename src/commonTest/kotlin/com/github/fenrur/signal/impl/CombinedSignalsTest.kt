package com.github.fenrur.signal.impl

import kotlin.test.*

class CombinedSignalsTest {

    // ==================== CombinedSignal2 ====================

    @Test
    fun `combined2 combines two signals`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        assertEquals(30, combined.value)
    }

    @Test
    fun `combined2 updates when first signal changes`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        a.value = 100

        assertEquals(120, combined.value)
    }

    @Test
    fun `combined2 updates when second signal changes`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        b.value = 200

        assertEquals(210, combined.value)
    }

    @Test
    fun `combined2 notifies subscribers`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }

        a.value = 100
        b.value = 200

        assertEquals(listOf(30, 120, 300), values)
    }

    // ==================== CombinedSignal3 ====================

    @Test
    fun `combined3 combines three signals`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val combined = CombinedSignal3(a, b, c) { x, y, z -> x + y + z }

        assertEquals(6, combined.value)
    }

    @Test
    fun `combined3 updates when any signal changes`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val combined = CombinedSignal3(a, b, c) { x, y, z -> x + y + z }

        a.value = 10
        assertEquals(15, combined.value)

        b.value = 20
        assertEquals(33, combined.value)

        c.value = 30
        assertEquals(60, combined.value)
    }

    // ==================== CombinedSignal4 ====================

    @Test
    fun `combined4 combines four signals`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val d = DefaultMutableSignal(4)
        val combined = CombinedSignal4(a, b, c, d) { w, x, y, z -> w + x + y + z }

        assertEquals(10, combined.value)
    }

    @Test
    fun `combined4 updates when any signal changes`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val d = DefaultMutableSignal(4)
        val combined = CombinedSignal4(a, b, c, d) { w, x, y, z -> w * x * y * z }

        d.value = 10

        assertEquals(60, combined.value) // 1 * 2 * 3 * 10
    }

    // ==================== CombinedSignal5 ====================

    @Test
    fun `combined5 combines five signals`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val d = DefaultMutableSignal(4)
        val e = DefaultMutableSignal(5)
        val combined = CombinedSignal5(a, b, c, d, e) { v, w, x, y, z -> v + w + x + y + z }

        assertEquals(15, combined.value)
    }

    // ==================== CombinedSignal6 ====================

    @Test
    fun `combined6 combines six signals`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val d = DefaultMutableSignal(4)
        val e = DefaultMutableSignal(5)
        val f = DefaultMutableSignal(6)
        val combined = CombinedSignal6(a, b, c, d, e, f) { u, v, w, x, y, z -> u + v + w + x + y + z }

        assertEquals(21, combined.value)
    }

    @Test
    fun `combined6 updates when last signal changes`() {
        val a = DefaultMutableSignal(1)
        val b = DefaultMutableSignal(2)
        val c = DefaultMutableSignal(3)
        val d = DefaultMutableSignal(4)
        val e = DefaultMutableSignal(5)
        val f = DefaultMutableSignal(6)
        val combined = CombinedSignal6(a, b, c, d, e, f) { u, v, w, x, y, z -> u + v + w + x + y + z }

        f.value = 100

        assertEquals(115, combined.value)
    }

    // ==================== Close behavior ====================

    @Test
    fun `combined signal closes properly`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        combined.close()

        assertTrue(combined.isClosed)
    }

    @Test
    fun `combined signal stops receiving after close`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        combined.close()
        values.clear()

        a.value = 100

        assertTrue(values.isEmpty())
    }

    // ==================== Different types ====================

    @Test
    fun `combined signals can have different types`() {
        val name = DefaultMutableSignal("John")
        val age = DefaultMutableSignal(30)
        val combined = CombinedSignal2(name, age) { n, a -> "$n is $a years old" }

        assertEquals("John is 30 years old", combined.value)

        name.value = "Jane"
        assertEquals("Jane is 30 years old", combined.value)
    }

    @Test
    fun `combined signals can produce complex types`() {
        data class Person(val name: String, val age: Int)

        val name = DefaultMutableSignal("John")
        val age = DefaultMutableSignal(30)
        val combined = CombinedSignal2(name, age) { n, a -> Person(n, a) }

        assertEquals(Person("John", 30), combined.value)
    }
}
