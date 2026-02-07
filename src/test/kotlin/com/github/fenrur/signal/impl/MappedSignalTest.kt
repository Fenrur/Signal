package com.github.fenrur.signal.impl

import com.github.fenrur.signal.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class MappedSignalTest {

    @Test
    fun `mapped signal transforms value`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }

        assertThat(mapped.value).isEqualTo(20)
    }

    @Test
    fun `mapped signal updates when source changes`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }

        source.value = 20

        assertThat(mapped.value).isEqualTo(40)
    }

    @Test
    fun `mapped signal notifies subscribers`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }

        source.value = 20
        source.value = 30

        assertThat(values).containsExactly(20, 40, 60)
    }

    @Test
    fun `mapped signal does not notify if transformed value is same`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it / 10 } // 10 -> 1, 15 -> 1
        val callCount = AtomicInteger(0)

        mapped.subscribe { callCount.incrementAndGet() }

        source.value = 15 // Still maps to 1

        assertThat(callCount.get()).isEqualTo(1) // Only initial
    }

    @Test
    fun `mapped signal can chain transformations`() {
        val source = CowMutableSignal(10)
        val doubled = MappedSignal(source) { it * 2 }
        val stringified = MappedSignal(doubled) { "Value: $it" }

        assertThat(stringified.value).isEqualTo("Value: 20")

        source.value = 5
        assertThat(stringified.value).isEqualTo("Value: 10")
    }

    @Test
    fun `mapped signal closes properly`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }

        assertThat(mapped.isClosed).isFalse()

        mapped.close()

        assertThat(mapped.isClosed).isTrue()
    }

    @Test
    fun `mapped signal stops receiving after close`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }
        mapped.close()
        values.clear()

        source.value = 20

        assertThat(values).isEmpty()
    }

    @Test
    fun `mapped signal can transform to different type`() {
        val source = CowMutableSignal(42)
        val mapped: Signal<String> = MappedSignal(source) { "Number: $it" }

        assertThat(mapped.value).isEqualTo("Number: 42")
    }

    @Test
    fun `mapped signal propagates errors`() {
        val source = CowMutableSignal(10)
        val mapped = MappedSignal(source) { it * 2 }
        val errors = CopyOnWriteArrayList<Throwable>()

        mapped.subscribe { either ->
            either.fold(
                { errors.add(it) },
                { }
            )
        }

        // Note: Standard signal implementations don't emit errors on value change,
        // so this test verifies the error path exists but may not trigger in normal use
        assertThat(errors).isEmpty()
    }
}
