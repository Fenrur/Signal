package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractSignalTest
import com.github.fenrur.signal.BindableSignal
import com.github.fenrur.signal.Signal
import com.github.fenrur.signal.bindableSignalOf
import com.github.fenrur.signal.mutableSignalOf
import com.github.fenrur.signal.signalOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class CowBindableSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = signalOf(initial)
        return CowBindableSignal(source)
    }

    // ==================== DefaultBindableSignal specific tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = CowBindableSignal<Int>()

        assertThatThrownBy { signal.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not bound")
    }

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = CowBindableSignal(source1)

        assertThat(signal.value).isEqualTo(10)

        signal.bindTo(source2)

        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = CowBindableSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }
        values.clear()

        signal.bindTo(source2)

        assertThat(values).contains(20)
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = CowBindableSignal<Int>(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source2.value = 30

        assertThat(values).contains(30)
        assertThat(signal.value).isEqualTo(30)
    }

    @Test
    fun `changes to old source are not propagated after rebind`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = CowBindableSignal<Int>(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source1.value = 100

        assertThat(values).doesNotContain(100)
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `can bind to read-only signal`() {
        val readOnly = signalOf(42)
        val signal = CowBindableSignal(readOnly)

        assertThat(signal.value).isEqualTo(42)
    }

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = CowBindableSignal(source1, takeOwnership = true)

        assertThat(source1.isClosed).isFalse()

        signal.bindTo(source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = mutableSignalOf(10)
        val signal = CowBindableSignal(source, takeOwnership = true)

        signal.close()

        assertThat(source.isClosed).isTrue()
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = mutableSignalOf(10)
        val signal = CowBindableSignal(source, takeOwnership = false)

        signal.close()

        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `isBound returns correct state`() {
        val signal = CowBindableSignal<Int>()
        assertThat(signal.isBound()).isFalse()

        signal.bindTo(signalOf(10))
        assertThat(signal.isBound()).isTrue()
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = signalOf(10)
        val signal = CowBindableSignal(source)

        assertThat(signal.currentSignal()).isSameAs(source)
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = CowBindableSignal<Int>()
        assertThat(signal.currentSignal()).isNull()
    }

    // ==================== Circular binding detection tests ====================

    @Test
    fun `wouldCreateCycle returns false for non-circular binding`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf(2)

        assertThat(BindableSignal.wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns true for direct self-binding`() {
        val a = bindableSignalOf(1)

        assertThat(BindableSignal.wouldCreateCycle(a, a)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for simple cycle A to B to A`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf<Int>()

        b.bindTo(a)

        assertThat(BindableSignal.wouldCreateCycle(a, b)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns true for chain cycle A to B to C to A`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf<Int>()
        val c = bindableSignalOf<Int>()

        b.bindTo(a)
        c.bindTo(b)

        assertThat(BindableSignal.wouldCreateCycle(a, c)).isTrue()
    }

    @Test
    fun `wouldCreateCycle returns false for valid chain`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf<Int>()
        val c = bindableSignalOf<Int>()

        b.bindTo(a)

        assertThat(BindableSignal.wouldCreateCycle(c, a)).isFalse()
    }

    @Test
    fun `wouldCreateCycle returns false when target is regular Signal`() {
        val a = bindableSignalOf(1)
        val b = signalOf(2)

        assertThat(BindableSignal.wouldCreateCycle(a, b)).isFalse()
    }

    @Test
    fun `bindTo throws on direct self-binding`() {
        val a = bindableSignalOf(1)

        assertThatThrownBy { a.bindTo(a) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on simple cycle`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf<Int>()

        b.bindTo(a)

        assertThatThrownBy { a.bindTo(b) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo throws on chain cycle`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf<Int>()
        val c = bindableSignalOf<Int>()

        b.bindTo(a)
        c.bindTo(b)

        assertThatThrownBy { a.bindTo(c) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Circular binding detected")
    }

    @Test
    fun `bindTo allows valid rebinding without cycle`() {
        val a = bindableSignalOf(1)
        val b = bindableSignalOf(2)
        val c = bindableSignalOf(3)

        a.bindTo(b)
        a.bindTo(c)

        assertThat(a.value).isEqualTo(3)
    }

    // ==================== BindableSignal is read-only ====================

    @Test
    fun `bindable signal reflects source value without write capability`() {
        val source = mutableSignalOf(10)
        val signal = CowBindableSignal(source)

        assertThat(signal.value).isEqualTo(10)

        source.value = 20
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `toString shows value and state`() {
        val signal = CowBindableSignal(signalOf(42))
        assertThat(signal.toString()).contains("42")
        assertThat(signal.toString()).contains("CowBindableSignal")
    }

    @Test
    fun `toString shows not bound when unbound`() {
        val signal = CowBindableSignal<Int>()
        assertThat(signal.toString()).contains("<not bound>")
    }
}
