package com.github.fenrur.signal.impl

import com.github.fenrur.signal.AbstractMutableSignalTest
import com.github.fenrur.signal.MutableSignal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class BindedSignalTest : AbstractMutableSignalTest() {

    override fun createSignal(initial: Int): MutableSignal<Int> {
        val source = CowSignal(initial)
        return BindedSignal(source)
    }

    override fun createNullableSignal(): MutableSignal<Int?> {
        val source = CowSignal<Int?>(null)
        return BindedSignal(source)
    }

    // ==================== BindedSignal specific tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = BindedSignal<Int>()

        assertThatThrownBy { signal.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not bound")
    }

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = CowSignal(10)
        val source2 = CowSignal(20)
        val signal = BindedSignal(source1)

        assertThat(signal.value).isEqualTo(10)

        signal.bindTo(source2)

        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = CowSignal(10)
        val source2 = CowSignal(20)
        val signal = BindedSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }
        values.clear()

        signal.bindTo(source2)

        assertThat(values).contains(20)
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = CowSignal(10)
        val source2 = CowSignal(20)
        val signal = BindedSignal(source1)
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
        val source1 = CowSignal(10)
        val source2 = CowSignal(20)
        val signal = BindedSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onRight { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source1.value = 100 // Change old source

        assertThat(values).doesNotContain(100)
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = CowSignal(10)
        val source2 = CowSignal(20)
        val signal = BindedSignal(source1, takeOwnership = true)

        assertThat(source1.isClosed).isFalse()

        signal.bindTo(source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = CowSignal(10)
        val signal = BindedSignal(source, takeOwnership = true)

        signal.close()

        assertThat(source.isClosed).isTrue()
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = CowSignal(10)
        val signal = BindedSignal(source, takeOwnership = false)

        signal.close()

        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `isBound returns correct state`() {
        val signal = BindedSignal<Int>()
        assertThat(signal.isBound()).isFalse()

        signal.bindTo(CowSignal(10))
        assertThat(signal.isBound()).isTrue()
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = CowSignal(10)
        val signal = BindedSignal(source)

        assertThat(signal.currentSignal()).isSameAs(source)
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = BindedSignal<Int>()
        assertThat(signal.currentSignal()).isNull()
    }

    @Test
    fun `setting value updates the bound source`() {
        val source = CowSignal(10)
        val signal = BindedSignal(source)

        signal.value = 20

        assertThat(source.value).isEqualTo(20)
    }

    @Test
    fun `update updates the bound source`() {
        val source = CowSignal(10)
        val signal = BindedSignal(source)

        signal.update { it + 5 }

        assertThat(source.value).isEqualTo(15)
    }
}
