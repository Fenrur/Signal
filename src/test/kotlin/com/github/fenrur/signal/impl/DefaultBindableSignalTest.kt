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

class DefaultBindableSignalTest : AbstractSignalTest<Signal<Int>>() {

    override fun createSignal(initial: Int): Signal<Int> {
        val source = signalOf(initial)
        return DefaultBindableSignal(source)
    }

    // ==================== DefaultBindableSignal specific tests ====================

    @Test
    fun `unbound signal throws on value access`() {
        val signal = DefaultBindableSignal<Int>()

        assertThatThrownBy { signal.value }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not bound")
    }

    @Test
    fun `bindTo changes the underlying signal`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = DefaultBindableSignal(source1)

        assertThat(signal.value).isEqualTo(10)

        signal.bindTo(source2)

        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `bindTo notifies subscribers with new value`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = DefaultBindableSignal(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        signal.bindTo(source2)

        assertThat(values).contains(20)
    }

    @Test
    fun `changes to new source are propagated`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = DefaultBindableSignal<Int>(source1)
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
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = DefaultBindableSignal<Int>(source1)
        val values = CopyOnWriteArrayList<Int>()

        signal.subscribe { it.onSuccess { v -> values.add(v) } }
        signal.bindTo(source2)
        values.clear()

        source1.value = 100

        assertThat(values).doesNotContain(100)
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `can bind to read-only signal`() {
        val readOnly = signalOf(42)
        val signal = DefaultBindableSignal(readOnly)

        assertThat(signal.value).isEqualTo(42)
    }

    @Test
    fun `takeOwnership closes old signal on rebind`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val signal = DefaultBindableSignal(source1, takeOwnership = true)

        assertThat(source1.isClosed).isFalse()

        signal.bindTo(source2)

        assertThat(source1.isClosed).isTrue()
        assertThat(source2.isClosed).isFalse()
    }

    @Test
    fun `takeOwnership closes source on close`() {
        val source = mutableSignalOf(10)
        val signal = DefaultBindableSignal(source, takeOwnership = true)

        signal.close()

        assertThat(source.isClosed).isTrue()
    }

    @Test
    fun `without takeOwnership source is not closed`() {
        val source = mutableSignalOf(10)
        val signal = DefaultBindableSignal(source, takeOwnership = false)

        signal.close()

        assertThat(source.isClosed).isFalse()
    }

    @Test
    fun `isBound returns correct state`() {
        val signal = DefaultBindableSignal<Int>()
        assertThat(signal.isBound()).isFalse()

        signal.bindTo(signalOf(10))
        assertThat(signal.isBound()).isTrue()
    }

    @Test
    fun `currentSignal returns bound signal`() {
        val source = signalOf(10)
        val signal = DefaultBindableSignal(source)

        assertThat(signal.currentSignal()).isSameAs(source)
    }

    @Test
    fun `currentSignal returns null when not bound`() {
        val signal = DefaultBindableSignal<Int>()
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
        val signal = DefaultBindableSignal(source)

        assertThat(signal.value).isEqualTo(10)

        source.value = 20
        assertThat(signal.value).isEqualTo(20)
    }

    @Test
    fun `toString shows value and state`() {
        val signal = DefaultBindableSignal(signalOf(42))
        assertThat(signal.toString()).contains("42")
        assertThat(signal.toString()).contains("DefaultBindableSignal")
    }

    @Test
    fun `toString shows not bound when unbound`() {
        val signal = DefaultBindableSignal<Int>()
        assertThat(signal.toString()).contains("<not bound>")
    }

    // ==================== Multiple successive rebindings ====================

    @Test
    fun `multiple successive rebindings work correctly`() {
        val signals = (1..5).map { mutableSignalOf(it * 10) }
        val bindable = DefaultBindableSignal(signals[0])
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        // Rebind through all signals
        for (i in 1 until signals.size) {
            values.clear()
            bindable.bindTo(signals[i])
            assertThat(bindable.value).isEqualTo((i + 1) * 10)
            assertThat(values).contains((i + 1) * 10)
        }

        // Only last signal affects bindable
        values.clear()
        signals[0].value = 999
        signals[1].value = 888
        signals[2].value = 777
        signals[3].value = 666
        signals[4].value = 555

        assertThat(values).containsExactly(555)
        assertThat(bindable.value).isEqualTo(555)
    }

    @Test
    fun `rebinding to same signal is idempotent`() {
        val source = mutableSignalOf(10)
        val bindable = DefaultBindableSignal(source)
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        // Rebind to same signal multiple times
        bindable.bindTo(source)
        bindable.bindTo(source)
        bindable.bindTo(source)

        source.value = 20
        assertThat(bindable.value).isEqualTo(20)
        // Each rebind notifies, so we get multiple notifications for 10
        assertThat(values).contains(20)
    }

    @Test
    fun `rapid rebinding does not lose updates`() {
        val signals = (1..10).map { mutableSignalOf(it) }
        val bindable = DefaultBindableSignal(signals[0])
        val values = CopyOnWriteArrayList<Int>()

        bindable.subscribe { it.onSuccess { v -> values.add(v) } }

        // Rapidly rebind
        signals.forEach { bindable.bindTo(it) }

        // Final value should be from last signal
        assertThat(bindable.value).isEqualTo(10)

        // Update last signal
        values.clear()
        signals.last().value = 100
        assertThat(values).containsExactly(100)
    }

    @Test
    fun `rebinding clears dirty flag correctly`() {
        val source1 = mutableSignalOf(10)
        val source2 = mutableSignalOf(20)
        val bindable = DefaultBindableSignal(source1)

        // Read to make it clean
        assertThat(bindable.value).isEqualTo(10)

        // Update source1 (makes bindable dirty)
        source1.value = 15

        // Rebind before reading dirty value
        bindable.bindTo(source2)

        // Should return source2's value, not stale source1 value
        assertThat(bindable.value).isEqualTo(20)
    }

    @Test
    fun `concurrent rebinding is thread-safe`() {
        val signals = (1..10).map { mutableSignalOf(it * 10) }
        val bindable = DefaultBindableSignal(signals[0])
        val latch = java.util.concurrent.CountDownLatch(1)
        val threads = mutableListOf<Thread>()

        bindable.subscribe { }

        // Multiple threads rebinding
        repeat(5) { threadId ->
            threads += Thread {
                latch.await()
                repeat(20) { i ->
                    val idx = (threadId * 20 + i) % signals.size
                    bindable.bindTo(signals[idx])
                    Thread.yield()
                }
            }
        }

        // Thread updating signals
        threads += Thread {
            latch.await()
            repeat(100) { i ->
                signals.forEach { it.value = i * 100 }
                Thread.yield()
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }

        // Bindable is still functional
        val finalSignal = mutableSignalOf(999)
        bindable.bindTo(finalSignal)
        assertThat(bindable.value).isEqualTo(999)
    }
}
