package com.github.fenrur.signal

import com.github.fenrur.signal.impl.DefaultMutableSignal
import com.github.fenrur.signal.impl.FilteredSignal
import com.github.fenrur.signal.impl.FlattenSignal
import com.github.fenrur.signal.impl.batch
import com.github.fenrur.signal.operators.combine
import com.github.fenrur.signal.operators.map
import com.github.fenrur.signal.operators.scan
import kotlin.test.*

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
        assertEquals(20, mapped.value)

        // Exception in transform is thrown when reading value
        source.value = -1

        val exception = runCatching { mapped.value }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
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
        assertEquals(10, filtered.value)

        source.value = 20
        assertEquals(20, filtered.value)

        // Predicate throws, but signal remains usable
        shouldThrow = true
        source.value = 30

        val exception = runCatching { filtered.value }.exceptionOrNull()
        assertTrue(exception is RuntimeException)

        // After error, signal can recover
        shouldThrow = false
        source.value = 40
        assertEquals(40, filtered.value)
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
        assertEquals(30, combined.value)

        // Exception when reading value
        a.value = 90

        val exception = runCatching { combined.value }.exceptionOrNull()
        assertTrue(exception is ArithmeticException)
    }

    // =========================================================================
    // LISTENER EXCEPTION HANDLING
    // =========================================================================

    @Test
    fun `listener exception during update does not prevent other listeners from receiving value`() {
        val source = DefaultMutableSignal(10)
        val values1 = mutableListOf<Int>()
        val values2 = mutableListOf<Int>()
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
        assertTrue(values1.contains(20))
        assertTrue(values2.contains(20))

        // Enable throwing in first listener
        firstListenerThrows = true
        values1.clear()
        values2.clear()

        // Second update - first listener throws but others still receive
        source.value = 30

        // Both working listeners received the value
        assertTrue(values1.contains(30))
        assertTrue(values2.contains(30))
    }

    @Test
    fun `listener exception in mapped signal does not break other listeners`() {
        val source = DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }
        val values = mutableListOf<Int>()
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
        assertTrue(values.contains(40))

        // Make first listener throw on value 60
        throwOnValue = 60
        values.clear()

        source.value = 30
        // Second listener still receives the value
        assertTrue(values.contains(60))
    }

    @Test
    fun `listener exception in batched updates does not prevent batch completion`() {
        val source = DefaultMutableSignal(1)
        val values = mutableListOf<Int>()
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
        assertEquals(3, source.value)
        // Listener received at least the final batch value
        assertTrue(values.contains(3))
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
        assertEquals(20, mapped.value)

        // Normal operation
        source.value = 20
        assertEquals(40, mapped.value)

        // Cause error
        shouldThrow = true
        source.value = 30
        // Value throws on access
        assertTrue(runCatching { mapped.value }.isFailure)

        // Recover
        shouldThrow = false
        source.value = 40
        assertEquals(80, mapped.value)
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

        val values = mutableListOf<Int>()
        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Switch to second inner
        outer.value = inner2

        // Close first inner (should not affect flattened)
        inner1.close()

        // Flattened still works with inner2
        inner2.value = 30
        assertTrue(values.contains(30))
    }

    @Test
    fun `flatten signal propagates inner signal computation errors via Result_failure`() {
        var shouldThrow = false
        val inner = DefaultMutableSignal(10)
        val mappedInner = inner.map { v: Int ->
            if (shouldThrow) throw RuntimeException("Inner computation error")
            v * 2
        }
        val outer = DefaultMutableSignal<Signal<Int>>(mappedInner)
        val flattened = FlattenSignal(outer)

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        flattened.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial value
        values.clear()

        // Normal update works
        inner.value = 5
        assertTrue(values.contains(10)) // 5 * 2

        // Cause inner computation to throw
        shouldThrow = true
        inner.value = 7

        // Error should be propagated via Result.failure()
        // Note: errors may propagate from multiple paths in the signal graph
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.all { it is RuntimeException && it.message == "Inner computation error" })
    }

    @Test
    fun `flatten signal recovers after inner computation error`() {
        var shouldThrow = false
        val inner = DefaultMutableSignal(10)
        val mappedInner = inner.map { v: Int ->
            if (shouldThrow) throw RuntimeException("Error")
            v * 2
        }
        val outer = DefaultMutableSignal<Signal<Int>>(mappedInner)
        val flattened = FlattenSignal(outer)

        val errors = mutableListOf<Throwable>()

        flattened.subscribe { result ->
            result.onFailure { errors.add(it) }
        }

        // Cause error
        shouldThrow = true
        inner.value = 5
        assertTrue(errors.isNotEmpty())

        // Recover - clear the throw condition and verify signal becomes usable again
        shouldThrow = false
        inner.value = 7

        // After recovery, reading value should work without throwing
        val recoveredValue = flattened.value
        assertEquals(14, recoveredValue) // 7 * 2
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
        assertEquals(10, mapped.value)

        // Now make it throw
        shouldThrow = true
        source.value = 20

        // Signal would throw on value access
        assertTrue(runCatching { mapped.value }.isFailure)

        // Can still close
        mapped.close()
        assertTrue(mapped.isClosed)

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
        assertEquals(10, throwingMapped.value)
        assertEquals(20, normalMapped.value)

        // Cause error in one branch
        source.value = -5

        // Throwing mapped throws
        assertTrue(runCatching { throwingMapped.value }.isFailure)

        // Normal mapped still works
        assertEquals(-10, normalMapped.value)

        // Source is still usable
        source.value = 15
        assertEquals(15, source.value)
        assertEquals(30, normalMapped.value)
        assertEquals(15, throwingMapped.value)
    }

    // =========================================================================
    // SELF-REMOVING LISTENER TESTS
    // =========================================================================

    @Test
    fun `listener can unsubscribe itself during callback`() {
        val source = DefaultMutableSignal(0)
        val values = mutableListOf<Int>()
        var unsubRef: UnSubscriber? = null

        // Listener that unsubscribes itself after receiving value 2
        val unsub = source.subscribe { result ->
            result.onSuccess { v ->
                values.add(v)
                if (v == 2) {
                    unsubRef?.invoke()
                }
            }
        }
        unsubRef = unsub

        // Clear initial value
        values.clear()

        // First update - listener still active
        source.value = 1
        assertEquals(listOf(1), values.toList())

        // Second update - listener unsubscribes itself
        source.value = 2
        assertTrue(values.contains(2))

        // Third update - listener should not receive this
        source.value = 3
        assertFalse(values.contains(3))
    }

    @Test
    fun `multiple listeners can unsubscribe themselves concurrently`() {
        val source = DefaultMutableSignal(0)
        val unsubRefs = mutableListOf<() -> Unit>()
        val receivedValues = mutableListOf<Pair<Int, Int>>() // (listenerId, value)

        // Create 5 listeners that each unsubscribe after receiving value 3
        repeat(5) { id ->
            var ref: UnSubscriber? = null
            val unsub = source.subscribe { result ->
                result.onSuccess { v ->
                    receivedValues.add(id to v)
                    if (v == 3) {
                        ref?.invoke()
                    }
                }
            }
            ref = unsub
            unsubRefs.add(unsub)
        }

        // Update values
        source.value = 1
        source.value = 2
        source.value = 3 // All listeners unsubscribe here
        source.value = 4 // No listeners should receive this

        // All listeners received values 1, 2, 3
        repeat(5) { id ->
            assertTrue(receivedValues.filter { it.first == id }.map { it.second }
                .containsAll(listOf(1, 2, 3)))
        }

        // No listener received value 4
        assertTrue(receivedValues.none { it.second == 4 })
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
        assertEquals(1, scanned.value) // 0 + 1

        source.value = 10
        assertEquals(11, scanned.value) // 1 + 10

        source.value = 50
        assertEquals(61, scanned.value) // 11 + 50

        // Exception when reading value - use different value to trigger computation
        source.value = 51 // Would be 61 + 51 = 112 > 100

        val exception = runCatching { scanned.value }.exceptionOrNull()
        assertTrue(exception is ArithmeticException)
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
        assertEquals(1, scanned.value)

        // Cause error
        shouldThrow = true
        source.value = 2

        assertTrue(runCatching { scanned.value }.isFailure)

        // Recover
        shouldThrow = false
        source.value = 3

        // Value is computed with new source value
        // Since error happened, accumulator state was preserved
        assertEquals(4, scanned.value) // 1 + 3 (error during 2 lost the acc update)
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

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        mapped.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 5
        assertEquals(listOf(10), values.toList())
        assertTrue(errors.isEmpty())

        // Cause exception - should be propagated to listener
        source.value = -1

        // Error is propagated
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalArgumentException)
        assertTrue(errors[0].message?.contains("-1") == true)
    }

    @Test
    fun `filter exception is propagated to listeners via Result_failure`() {
        val source = DefaultMutableSignal(10)
        val filtered = FilteredSignal(source) { v: Int ->
            if (v < 0) throw IllegalArgumentException("Negative value")
            v > 5
        }

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        filtered.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 20
        assertEquals(listOf(20), values.toList())
        assertTrue(errors.isEmpty())

        // Cause exception
        source.value = -1

        // Error is propagated
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalArgumentException)
    }

    @Test
    fun `combine exception is propagated to listeners via Result_failure`() {
        val a = DefaultMutableSignal(5)
        val b = DefaultMutableSignal(3)
        val combined = combine(a, b) { x, y ->
            if (x == y) throw IllegalStateException("Values cannot be equal")
            x + y
        }

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        combined.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        a.value = 10
        assertEquals(listOf(13), values.toList()) // 10 + 3
        assertTrue(errors.isEmpty())

        // Cause exception - set a = b
        a.value = 3

        // Error is propagated
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalStateException)
    }

    @Test
    fun `scan exception is propagated to listeners via Result_failure`() {
        val source = DefaultMutableSignal(1)
        val scanned = source.scan(0) { acc, v ->
            if (v == 0) throw IllegalArgumentException("Zero not allowed")
            acc + v
        }

        val errors = mutableListOf<Throwable>()
        val values = mutableListOf<Int>()

        scanned.subscribe { result ->
            result.onSuccess { values.add(it) }
            result.onFailure { errors.add(it) }
        }

        // Clear initial
        values.clear()

        // Normal value
        source.value = 5
        assertEquals(listOf(6), values.toList()) // 1 + 5
        assertTrue(errors.isEmpty())

        // Cause exception
        source.value = 0

        // Error is propagated
        assertEquals(1, errors.size)
        assertTrue(errors[0] is IllegalArgumentException)
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
        assertNotNull(thrownException)
        assertEquals("Error on initial value", thrownException?.message)

        // But signal is still usable - second listener works
        val values = mutableListOf<Int>()
        source.subscribe { result ->
            result.onSuccess { values.add(it) }
        }

        // Initial value received by second listener
        assertEquals(listOf(10), values.toList())

        // Updates work
        source.value = 20
        assertTrue(values.contains(20))
    }

    @Test
    fun `listener exception during update notification does not break other listeners`() {
        val source = DefaultMutableSignal(10)
        val values = mutableListOf<Int>()
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
        assertTrue(values.contains(20))

        // Third update also works
        source.value = 30
        assertTrue(values.contains(30))
    }
}
