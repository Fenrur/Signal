package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.BindableMutableSignal
import com.github.fenrur.signal.MutableSignal
import com.github.fenrur.signal.bindableMutableSignalOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class DefaultBindableMutableSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> {
        val source = DefaultMutableSignal(initial)
        return DefaultBindableMutableSignal(source)
    }

    override fun createNullableSignal(): MutableSignal<Int?> {
        val source = DefaultMutableSignal<Int?>(null)
        return DefaultBindableMutableSignal(source)
    }

    // ==================== DefaultBindableMutableSignal specific tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = DefaultBindableMutableSignal<Int>()

        assertThatThrownBy { signal.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not bound")
    }

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = DefaultMutableSignal(10)
        val source2 = DefaultMutableSignal(20)
        val signal = DefaultBindableMutableSignal(source1)

        assertThat(signal.value).isEqualTo(10)

        signal.bindTo(source2)

        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = DefaultMutableSignal(10)
        val source2 = DefaultMutableSignal(20)
        val signal = DefaultBindableMutableSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        signal.bindTo(source2)

        assertThat(values).contains(20)
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = DefaultMutableSignal(10)
        val source2 = DefaultMutableSignal(20)
        val signal = DefaultBindableMutableSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source2.value = 30

        assertThat(values).contains(30)
        assertThat(signal.value).isEqualTo(30)
    }

    @Test
    fun `changes to old source are not propagated after rebind`() {
        val source1 = DefaultMutableSignal(10)
        val source2 = DefaultMutableSignal(20)
        val signal = DefaultBindableMutableSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source1.value = 100 // Change old source

        assertThat(values).doesNotContain(100)
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = DefaultMutableSignal(10)
        val source2 = DefaultMutableSignal(20)
        val signal = DefaultBindableMutableSignal(source1, takeOwnership = true)

        assertThat(source1.isClosed).isFalse()

        signal.bindTo(source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source, takeOwnership = true)

        signal.close()

        assertThat(source.isClosed).isTrue()
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source, takeOwnership = false)

        signal.close()

        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `isBound returns correct state`() {
        val signal = DefaultBindableMutableSignal<Int>()
        assertThat(signal.isBound()).isFalse()

        signal.bindTo(DefaultMutableSignal(10))
        assertThat(signal.isBound()).isTrue()
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source)

        assertThat(signal.currentSignal()).isSameAs(source)
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = DefaultBindableMutableSignal<Int>()
        assertThat(signal.currentSignal()).isNull()
    }

    @Test
    fun `setting value updates the bound source`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source)

        signal.value = 20

        assertThat(source.value).isEqualTo(20)
    }

    @Test
    fun `update updates the bound source`() {
        val source = DefaultMutableSignal(10)
        val signal = DefaultBindableMutableSignal(source)

        signal.update { it + 5 }

        assertThat(source.value).isEqualTo(15)
    }

    // ==================== Circular binding detection tests ====================

    @Test
    fun `wouldCreateCycle returns false for non-circular binding`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)

        assertThat(BindableMutableSignal.wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns true for direct self-binding`() {
        val a = bindableMutableSignalOf(1)

        assertThat(BindableMutableSignal.wouldCreateCycle(a, a)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for simple cycle A to B to A`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)

        a.bindTo(b)

        assertThat(BindableMutableSignal.wouldCreateCycle(b, a)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for chain cycle A to B to C to A`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)
        val c = bindableMutableSignalOf(3)

        a.bindTo(b)
        b.bindTo(c)

        assertThat(BindableMutableSignal.wouldCreateCycle(c, a)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns false for valid chain`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)
        val c = bindableMutableSignalOf(3)

        a.bindTo(b)

        assertThat(BindableMutableSignal.wouldCreateCycle(c, a)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns false when target is regular MutableSignal`() {
        val a = bindableMutableSignalOf(1)
        val b = DefaultMutableSignal(2)

        assertThat(BindableMutableSignal.wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `bindTo throws on direct self-binding`() {
        val a = bindableMutableSignalOf(1)

        assertThatThrownBy { a.bindTo(a) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on simple cycle`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)

        a.bindTo(b)

        assertThatThrownBy { b.bindTo(a) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on chain cycle`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)
        val c = bindableMutableSignalOf(3)

        a.bindTo(b)
        b.bindTo(c)

        assertThatThrownBy { c.bindTo(a) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo allows valid rebinding without cycle`() {
        val a = bindableMutableSignalOf(1)
        val b = bindableMutableSignalOf(2)
        val c = bindableMutableSignalOf(3)

        a.bindTo(b)
        a.bindTo(c) // rebind a to c, no cycle

        assertThat(a.value).isEqualTo(3)
    }
}
