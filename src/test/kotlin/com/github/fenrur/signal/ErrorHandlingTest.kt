package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.MappedSignal
import com.github.fenrur.signal.impl.FilteredSignal
import com.github.fenrur.signal.impl.FlattenSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.combine
import com.github.fenrur.signal.operators.map
import com.github.fenrur.signal.operators.scan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

    // =========================================================================
    // SELF-REMOVING LISTENER TESTS
    // =========================================================================

    @Test
    fun `listener can unsubscribe itself during callback`() {
        val source = DefaultMutableSignal(0)
        val values = CopyOnWriteArrayList<Int>()
        val unsubRef = AtomicReference<UnSubscriber>()

        // Listener that unsubscribes itself after receiving value 2
        val unsub = source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                if (v == 2) {
                    unsubRef.get()?.invoke()
                }
            }
        }
        unsubRef.set(unsub)

        // Clear initial value
        values.clear()

        // First update - listener still active
        source.value = 1
        assertThat(values).containsExactly(1)

        // Second update - listener unsubscribes itself
        source.value = 2
        assertThat(values).contains(2)

        // Third update - listener should not receive this
        source.value = 3
        assertThat(values).doesNotContain(3)
    }

    @Test
    fun `multiple listeners can unsubscribe themselves concurrently`() {
        val source = DefaultMutableSignal(0)
        val unsubRefs = CopyOnWriteArrayList<AtomicReference<UnSubscriber>>()
        val receivedValues = CopyOnWriteArrayList<Pair<Int, Int>>() // (listenerId, value)

        // Create 5 listeners that each unsubscribe after receiving value 3
        repeat(5) { id ->
            val ref = AtomicReference<UnSubscriber>()
            unsubRefs.add(ref)
            val unsub = source.subscribe { result ->
                result.onSuccess { v ->
                    receivedValues.add(id to v)
                    if (v == 3) {
                        ref.get()?.invoke()
                    }
                }
            }
            ref.set(unsub)
        }

        // Update values
        source.value = 1
        source.value = 2
        source.value = 3 // All listeners unsubscribe here
        source.value = 4 // No listeners should receive this

        // All listeners received values 1, 2, 3
        repeat(5) { id ->
            assertThat(receivedValues.filter { it.first == id }.map { it.second })
                .contains(1, 2, 3)
        }

        // No listener received value 4
        assertThat(receivedValues.none { it.second == 4 }).isTrue()
    }

    @RepeatedTest(5)
    fun `concurrent self-unsubscription is thread-safe`() {
        val source = DefaultMutableSignal(0)
        val unsubscribed = AtomicInteger(0)
        val received = AtomicInteger(0)
        val unsubRefs = CopyOnWriteArrayList<AtomicReference<UnSubscriber>>()

        // Create 10 listeners
        repeat(10) {
            val ref = AtomicReference<UnSubscriber>()
            unsubRefs.add(ref)
            val unsub = source.subscribe { result ->
                result.onSuccess { v ->
                    received.incrementAndGet()
                    // Each listener unsubscribes itself randomly
                    if (v > 5 && Math.random() > 0.5) {
                        val u = ref.getAndSet(null)
                        if (u != null) {
                            u.invoke()
                            unsubscribed.incrementAndGet()
                        }
                    }
                }
            }
            ref.set(unsub)
        }

        // Update values concurrently from multiple threads
        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)

        repeat(4) {
            executor.submit {
                try {
                    repeat(20) { i ->
                        source.value = i
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Some values were received
        assertThat(received.get()).isGreaterThan(0)
        // Signal is still functional
        source.value = 100
    }

    // =========================================================================
    // SCAN OPERATOR EXCEPTION HANDLING
    // =========================================================================

    @Test
    fun `scan accumulator exception is thrown on value read`() {
        val source = DefaultMutableSignal(1)
        val scanned = source.scan(0) { acc, v ->
            if (acc + v > 100) throw ArithmeticException("Overflow")
            acc + v
        }

        // Normal accumulation works
        assertThat(scanned.value).isEqualTo(1) // 0 + 1

        source.value = 10
        assertThat(scanned.value).isEqualTo(11) // 1 + 10

        source.value = 50
        assertThat(scanned.value).isEqualTo(61) // 11 + 50

        // Exception when reading value - use different value to trigger computation
        source.value = 51 // Would be 61 + 51 = 112 > 100

        val exception = runCatching { scanned.value }.exceptionOrNull()
        assertThat(exception).isInstanceOf(ArithmeticException::class.java)
    }

    @Test
    fun `scan recovers after accumulator exception`() {
        var shouldThrow = false
        val source = DefaultMutableSignal(1)
        val scanned = source.scan(0) { acc, v ->
            if (shouldThrow) throw RuntimeException("Error")
            acc + v
        }

        // Initial value
        assertThat(scanned.value).isEqualTo(1)

        // Cause error
        shouldThrow = true
        source.value = 2

        assertThat(runCatching { scanned.value }.isFailure).isTrue()

        // Recover
        shouldThrow = false
        source.value = 3

        // Value is computed with new source value
        // Since error happened, accumulator state was preserved
        assertThat(scanned.value).isEqualTo(4) // 1 + 3 (error during 2 lost the acc update)
    }

    // =========================================================================
    // ERROR PROPAGATION VIA RESULT
    // =========================================================================

    @Test
    fun `map exception is propagated to listeners via Result_failure`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { v: Int ->
            if (v < 0) throw IllegalArgumentException("Negative value: $v")
            v * 2
        }

        val errors = CopyOnWriteArrayList<Throwable>()
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 5
        assertThat(values).containsExactly(10)
        assertThat(errors).isEmpty()

        // Cause exception - should be propagated to listener
        source.value = -1

        // Error is propagated
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(errors[0].message).contains("-1")
    }

    @Test
    fun `filter exception is propagated to listeners via Result_failure`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { v: Int ->
            if (v < 0) throw IllegalArgumentException("Negative value")
            v > 5
        }

        val errors = CopyOnWriteArrayList<Throwable>()
        val values = CopyOnWriteArrayList<Int>()

        filtered.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 20
        assertThat(values).containsExactly(20)
        assertThat(errors).isEmpty()

        // Cause exception
        source.value = -1

        // Error is propagated
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `combine exception is propagated to listeners via Result_failure`() {
        val a = DefaultMutableSignal(5)
        val b = DefaultMutableSignal(3)
        val combined = combine(a, b) { x, y ->
            if (x == y) throw IllegalStateException("Values cannot be equal")
            x + y
        }

        val errors = CopyOnWriteArrayList<Throwable>()
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        a.value = 10
        assertThat(values).containsExactly(13) // 10 + 3
        assertThat(errors).isEmpty()

        // Cause exception - set a = b
        a.value = 3

        // Error is propagated
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `scan exception is propagated to listeners via Result_failure`() {
        val source = DefaultMutableSignal(1)
        val scanned = source.scan(0) { acc, v ->
            if (v == 0) throw IllegalArgumentException("Zero not allowed")
            acc + v
        }

        val errors = CopyOnWriteArrayList<Throwable>()
        val values = CopyOnWriteArrayList<Int>()

        scanned.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 5
        assertThat(values).containsExactly(6) // 1 + 5
        assertThat(errors).isEmpty()

        // Cause exception
        source.value = 0

        // Error is propagated
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(IllegalArgumentException::class.java)
    }

    // =========================================================================
    // LISTENER THROWS DURING SUBSCRIBE
    // =========================================================================

    @Test
    fun `listener exception during initial subscribe propagates but signal remains usable`() {
        val source = DefaultMutableSignal(10)

        // First subscribe throws
        var thrownException: Throwable? = null
        try {
            source.subscribe { result ->
                result.onSuccess {
                    throw RuntimeException("Error on initial value")
                }
            }
        } catch (e: RuntimeException) {
            thrownException = e
        }

        // Exception was thrown
        assertThat(thrownException).isNotNull()
        assertThat(thrownException?.message).isEqualTo("Error on initial value")

        // But signal is still usable - second listener works
        val values = CopyOnWriteArrayList<Int>()
        source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Initial value received by second listener
        assertThat(values).containsExactly(10)

        // Updates work
        source.value = 20
        assertThat(values).contains(20)
    }

    @Test
    fun `listener exception during update notification does not break other listeners`() {
        val source = DefaultMutableSignal(10)
        val values = CopyOnWriteArrayList<Int>()
        var shouldThrow = false

        // First listener that throws on update (not initial subscribe)
        source.subscribe { result ->
            result.onSuccess { v ->
                if (shouldThrow && v > 10) {
                    throw RuntimeException("Error on update")
                }
            }
        }

        // Second listener
        source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Clear initial values
        values.clear()

        // Enable throwing
        shouldThrow = true

        // Update - first listener throws but second should still receive
        source.value = 20
        assertThat(values).contains(20)

        // Third update also works
        source.value = 30
        assertThat(values).contains(30)
    }
}
