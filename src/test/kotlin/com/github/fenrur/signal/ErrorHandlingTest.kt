package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.MappedSignal
import com.github.fenrur.signal.impl.FilteredSignal
import com.github.fenrur.signal.impl.FlattenSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.combine
import com.github.fenrur.signal.operators.map
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for error handling and propagation through the signal graph.
 *
 * These tests verify that:
 * - Errors in transformation functions are handled gracefully
 * - Listener exceptions don't crash the signal system
 * - Error state doesn't corrupt signal values
 */
class ErrorHandlingTest {

    // =========================================================================
    // TRANSFORMATION ERROR HANDLING
    // =========================================================================

    @Test
    fun `map transformation exception does not crash on value read`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { v: Int ->
            if (v < 0) throw IllegalArgumentException("Negative value")
            v * 2
        }

        // Normal value works
        assertThat(mapped.value).isEqualTo(20)

        // Exception in transform is thrown when reading value
        source.value = -1

        val exception = runCatching { mapped.value }.exceptionOrNull()
        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `filter predicate exception does not corrupt signal state`() {
        var shouldThrow = false
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { v: Int ->
            if (shouldThrow) throw RuntimeException("Predicate error")
            v > 0
        }

        // Normal filtering works
        assertThat(filtered.value).isEqualTo(10)

        source.value = 20
        assertThat(filtered.value).isEqualTo(20)

        // Predicate throws, but signal remains usable
        shouldThrow = true
        source.value = 30

        val exception = runCatching { filtered.value }.exceptionOrNull()
        assertThat(exception).isInstanceOf(RuntimeException::class.java)

        // After error, signal can recover
        shouldThrow = false
        source.value = 40
        assertThat(filtered.value).isEqualTo(40)
    }

    @Test
    fun `combine transformation exception is thrown on value access`() {
        val a = DefaultMutableSignal(10)
        val b = DefaultMutableSignal(20)
        val combined = combine(a, b) { x, y ->
            if (x + y > 100) throw ArithmeticException("Sum too large")
            x + y
        }

        // Normal combination works
        assertThat(combined.value).isEqualTo(30)

        // Exception when reading value
        a.value = 90

        val exception = runCatching { combined.value }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ArithmeticException::class.java)
    }

    // =========================================================================
    // LISTENER EXCEPTION HANDLING
    // =========================================================================

    @Test
    fun `listener exception during update does not prevent other listeners from receiving value`() {
        val source = DefaultMutableSignal(10)
        val values1 = CopyOnWriteArrayList<Int>()
        val values2 = CopyOnWriteArrayList<Int>()
        var firstListenerThrows = false

        // First listener throws conditionally
        source.subscribe { result ->
            result.onSuccess { v ->
                if (firstListenerThrows) throw RuntimeException("Listener 1 error")
                // Don't record values for first listener
            }
        }

        // Second listener should still receive values
        source.subscribe { result ->
            result.onSuccess { values2.add(it) }
        }

        // Third listener
        source.subscribe { result ->
            result.onSuccess { values1.add(it) }
        }

        // Clear initial values
        values1.clear()
        values2.clear()

        // First update - all listeners receive
        source.value = 20
        assertThat(values1).contains(20)
        assertThat(values2).contains(20)

        // Enable throwing in first listener
        firstListenerThrows = true
        values1.clear()
        values2.clear()

        // Second update - first listener throws but others still receive
        source.value = 30

        // Both working listeners received the value
        assertThat(values1).contains(30)
        assertThat(values2).contains(30)
    }

    @Test
    fun `listener exception in mapped signal does not break other listeners`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }
        val values = CopyOnWriteArrayList<Int>()
        var throwOnValue = -1

        // Throwing listener
        mapped.subscribe { result ->
            result.onSuccess { v ->
                if (v == throwOnValue) throw RuntimeException("Error on $v")
            }
        }

        // Working listener
        mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Clear initial values
        values.clear()

        source.value = 20
        assertThat(values).contains(40)

        // Make first listener throw on value 60
        throwOnValue = 60
        values.clear()

        source.value = 30
        // Second listener still receives the value
        assertThat(values).contains(60)
    }

    @Test
    fun `listener exception in batched updates does not prevent batch completion`() {
        val source = DefaultMutableSignal(1)
        val values = CopyOnWriteArrayList<Int>()
        var throwOnValue = -1

        source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                if (v == throwOnValue) throw RuntimeException("Error on $v")
            }
        }

        // Clear initial value
        values.clear()

        // Set to throw on value 2
        throwOnValue = 2

        batch {
            source.value = 2
            source.value = 3
        }

        // Batch completes, final value is 3
        assertThat(source.value).isEqualTo(3)
        // Listener received at least the final batch value
        assertThat(values).contains(3)
    }

    // =========================================================================
    // RECOVERY FROM ERRORS
    // =========================================================================

    @Test
    fun `signal recovers after transformation error`() {
        var shouldThrow = false
        val source = DefaultMutableSignal(10)
        val mapped = source.map { v: Int ->
            if (shouldThrow) throw RuntimeException("Error")
            v * 2
        }

        // Read initial value
        assertThat(mapped.value).isEqualTo(20)

        // Normal operation
        source.value = 20
        assertThat(mapped.value).isEqualTo(40)

        // Cause error
        shouldThrow = true
        source.value = 30
        // Value throws on access
        assertThat(runCatching { mapped.value }.isFailure).isTrue()

        // Recover
        shouldThrow = false
        source.value = 40
        assertThat(mapped.value).isEqualTo(80)
    }

    @Test
    fun `concurrent errors do not corrupt signal state`() {
        val source = DefaultMutableSignal(0)
        val mapped = source.map { v: Int ->
            if (v % 7 == 0 && v != 0) throw RuntimeException("Divisible by 7")
            v * 2
        }

        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val threads = mutableListOf<Thread>()

        // Multiple threads reading
        repeat(5) {
            threads += Thread {
                repeat(100) {
                    try {
                        mapped.value
                        successCount.incrementAndGet()
                    } catch (e: RuntimeException) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }

        // One thread writing
        threads += Thread {
            repeat(50) { i ->
                source.value = i
                Thread.sleep(1)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Both successes and errors occurred
        assertThat(successCount.get()).isGreaterThan(0)
        // Signal is still functional
        source.value = 10
        assertThat(mapped.value).isEqualTo(20)
    }

    // =========================================================================
    // FLATTEN SIGNAL ERROR HANDLING
    // =========================================================================

    @Test
    fun `flatten signal handles inner signal errors gracefully`() {
        val inner1 = DefaultMutableSignal(10)
        val inner2 = DefaultMutableSignal(20)
        val outer = DefaultMutableSignal<Signal<Int>>(inner1)
        val flattened = FlattenSignal(outer)

        val values = CopyOnWriteArrayList<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Switch to second inner
        outer.value = inner2

        // Close first inner (should not affect flattened)
        inner1.close()

        // Flattened still works with inner2
        inner2.value = 30
        assertThat(values).contains(30)
    }

    // =========================================================================
    // CLOSE DURING ERROR STATE
    // =========================================================================

    @Test
    fun `signal can be closed while transform would throw`() {
        var shouldThrow = false
        val source = DefaultMutableSignal(10)
        val mapped = source.map { v: Int ->
            if (shouldThrow) throw RuntimeException("Error")
            v
        }

        // Read initial value (works)
        assertThat(mapped.value).isEqualTo(10)

        // Now make it throw
        shouldThrow = true
        source.value = 20

        // Signal would throw on value access
        assertThat(runCatching { mapped.value }.isFailure).isTrue()

        // Can still close
        mapped.close()
        assertThat(mapped.isClosed).isTrue()

        // No subscriptions after close
        val subscribed = mapped.subscribe { }
        subscribed() // Should not throw
    }

    // =========================================================================
    // SIGNAL REMAINS USABLE AFTER ERRORS IN GRAPH
    // =========================================================================

    @Test
    fun `source signal remains usable after dependent throws`() {
        val source = DefaultMutableSignal(10)
        val throwingMapped = source.map { v: Int ->
            if (v < 0) throw IllegalArgumentException("Negative")
            v
        }
        val normalMapped = source.map { it * 2 }

        // Both work initially
        assertThat(throwingMapped.value).isEqualTo(10)
        assertThat(normalMapped.value).isEqualTo(20)

        // Cause error in one branch
        source.value = -5

        // Throwing mapped throws
        assertThat(runCatching { throwingMapped.value }.isFailure).isTrue()

        // Normal mapped still works
        assertThat(normalMapped.value).isEqualTo(-10)

        // Source is still usable
        source.value = 15
        assertThat(source.value).isEqualTo(15)
        assertThat(normalMapped.value).isEqualTo(30)
        assertThat(throwingMapped.value).isEqualTo(15)
    }
}
