package com.github.fenrur.signal.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class BimappedSignalTest {

    @Test
    fun `bimapped signal reads with forward transform`() {
        val source = DefaultMutableSignal("42")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertThat(bimapped.value).isEqualTo(42)
    }

    @Test
    fun `bimapped signal writes with reverse transform`() {
        val source = DefaultMutableSignal("42")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.value = 100

        assertThat(source.value).isEqualTo("100")
        assertThat(bimapped.value).isEqualTo(100)
    }

    @Test
    fun `bimapped signal update applies both transforms`() {
        val source = DefaultMutableSignal("10")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.update { it + 5 }

        assertThat(source.value).isEqualTo("15")
        assertThat(bimapped.value).isEqualTo(15)
    }

    @Test
    fun `bimapped signal notifies subscribers on source change`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = CopyOnWriteArrayList<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = "2"
        source.value = "3"

        assertThat(values).containsExactly(1, 2, 3)
    }

    @Test
    fun `bimapped signal notifies subscribers on mapped write`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = CopyOnWriteArrayList<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        bimapped.value = 10
        bimapped.value = 20

        assertThat(values).containsExactly(1, 10, 20)
    }

    @Test
    fun `bimapped signal closes properly`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertThat(bimapped.isClosed).isFalse()

        bimapped.close()

        assertThat(bimapped.isClosed).isTrue()
    }

    @Test
    fun `bimapped signal stops notifications after close`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = CopyOnWriteArrayList<Int>()

        bimapped.subscribe { it.onSuccess { v -> values.add(v) } }
        bimapped.close()

        source.value = "99"

        assertThat(bimapped.isClosed).isTrue()
        assertThat(values).containsExactly(1) // Only initial value
    }

    @Test
    fun `bimapped signal write on closed signal does nothing`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.close()
        bimapped.value = 99

        assertThat(source.value).isEqualTo("1")
    }

    @Test
    fun `bimapped signal update on closed signal does nothing`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        bimapped.close()
        bimapped.update { it + 100 }

        assertThat(source.value).isEqualTo("1")
    }

    @Test
    fun `bimapped signal can be used as property delegate`() {
        val source = DefaultMutableSignal("5")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        var prop by bimapped
        assertThat(prop).isEqualTo(5)

        prop = 42
        assertThat(source.value).isEqualTo("42")
    }

    @Test
    fun `bimapped signal chaining works`() {
        val source = DefaultMutableSignal(10)
        val doubled = BimappedSignal(source, { it * 2 }, { it / 2 })
        val asString = BimappedSignal(doubled, { it.toString() }, { it.toInt() })

        assertThat(asString.value).isEqualTo("20")

        asString.value = "100"
        assertThat(doubled.value).isEqualTo(100)
        assertThat(source.value).isEqualTo(50)
    }

    @Test
    fun `bimapped signal with identity transforms is pass-through`() {
        val source = DefaultMutableSignal(42)
        val identity = BimappedSignal(source, { it }, { it })

        assertThat(identity.value).isEqualTo(42)
        identity.value = 100
        assertThat(source.value).isEqualTo(100)
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })
        val values = CopyOnWriteArrayList<Int>()

        val unsubscribe = bimapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = "10"
        unsubscribe()
        source.value = "20"

        assertThat(values).containsExactly(10)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal("1")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })
        bimapped.close()

        val values = CopyOnWriteArrayList<Int>()
        val unsubscribe = bimapped.subscribe { it.onSuccess { v -> values.add(v) } }

        assertThat(values).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal("42")
        val bimapped = BimappedSignal(source, { it.toInt() }, { it.toString() })

        assertThat(bimapped.toString()).contains("42")
        assertThat(bimapped.toString()).contains("BimappedSignal")
    }
}
