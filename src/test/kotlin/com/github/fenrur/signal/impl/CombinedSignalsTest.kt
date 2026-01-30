package com.github.fenrur.signal.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class CombinedSignalsTest {

    // ==================== CombinedSignal2 ====================

    @Test
    fun `combined2 combines two signals`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        assertThat(combined.value).isEqualTo(30)
    }

    @Test
    fun `combined2 updates when first signal changes`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        a.value = 100

        assertThat(combined.value).isEqualTo(120)
    }

    @Test
    fun `combined2 updates when second signal changes`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        b.value = 200

        assertThat(combined.value).isEqualTo(210)
    }

    @Test
    fun `combined2 notifies subscribers`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onRight { v -> values.add(v) } }

        a.value = 100
        b.value = 200

        assertThat(values).containsExactly(30, 120, 300)
    }

    // ==================== CombinedSignal3 ====================

    @Test
    fun `combined3 combines three signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val combined = CombinedSignal3(a, b, c) { x, y, z -> x + y + z }

        assertThat(combined.value).isEqualTo(6)
    }

    @Test
    fun `combined3 updates when any signal changes`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val combined = CombinedSignal3(a, b, c) { x, y, z -> x + y + z }

        a.value = 10
        assertThat(combined.value).isEqualTo(15)

        b.value = 20
        assertThat(combined.value).isEqualTo(33)

        c.value = 30
        assertThat(combined.value).isEqualTo(60)
    }

    // ==================== CombinedSignal4 ====================

    @Test
    fun `combined4 combines four signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val combined = CombinedSignal4(a, b, c, d) { w, x, y, z -> w + x + y + z }

        assertThat(combined.value).isEqualTo(10)
    }

    @Test
    fun `combined4 updates when any signal changes`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val combined = CombinedSignal4(a, b, c, d) { w, x, y, z -> w * x * y * z }

        d.value = 10

        assertThat(combined.value).isEqualTo(60) // 1 * 2 * 3 * 10
    }

    // ==================== CombinedSignal5 ====================

    @Test
    fun `combined5 combines five signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val e = CowSignal(5)
        val combined = CombinedSignal5(a, b, c, d, e) { v, w, x, y, z -> v + w + x + y + z }

        assertThat(combined.value).isEqualTo(15)
    }

    // ==================== CombinedSignal6 ====================

    @Test
    fun `combined6 combines six signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val e = CowSignal(5)
        val f = CowSignal(6)
        val combined = CombinedSignal6(a, b, c, d, e, f) { u, v, w, x, y, z -> u + v + w + x + y + z }

        assertThat(combined.value).isEqualTo(21)
    }

    @Test
    fun `combined6 updates when last signal changes`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val e = CowSignal(5)
        val f = CowSignal(6)
        val combined = CombinedSignal6(a, b, c, d, e, f) { u, v, w, x, y, z -> u + v + w + x + y + z }

        f.value = 100

        assertThat(combined.value).isEqualTo(115)
    }

    // ==================== Close behavior ====================

    @Test
    fun `combined signal closes properly`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }

        combined.close()

        assertThat(combined.isClosed).isTrue()
    }

    @Test
    fun `combined signal stops receiving after close`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = CombinedSignal2(a, b) { x, y -> x + y }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onRight { v -> values.add(v) } }
        combined.close()
        values.clear()

        a.value = 100

        assertThat(values).isEmpty()
    }

    // ==================== Different types ====================

    @Test
    fun `combined signals can have different types`() {
        val name = CowSignal("John")
        val age = CowSignal(30)
        val combined = CombinedSignal2(name, age) { n, a -> "$n is $a years old" }

        assertThat(combined.value).isEqualTo("John is 30 years old")

        name.value = "Jane"
        assertThat(combined.value).isEqualTo("Jane is 30 years old")
    }

    @Test
    fun `combined signals can produce complex types`() {
        data class Person(val name: String, val age: Int)

        val name = CowSignal("John")
        val age = CowSignal(30)
        val combined = CombinedSignal2(name, age) { n, a -> Person(n, a) }

        assertThat(combined.value).isEqualTo(Person("John", 30))
    }
}
