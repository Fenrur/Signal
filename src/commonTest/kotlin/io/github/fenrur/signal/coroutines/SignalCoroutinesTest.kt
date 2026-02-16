package io.github.fenrur.signal.coroutines

import io.github.fenrur.signal.asFlow
import io.github.fenrur.signal.asSignal
import io.github.fenrur.signal.mutableSignalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SignalCoroutinesTest {

    @Test
    fun `asFlow emits current value immediately`() = runTest {
        val signal = _root_ide_package_.io.github.fenrur.signal.mutableSignalOf(42)
        val flow = signal.asFlow()

        val firstValue = flow.first()
        assertEquals(42, firstValue)
    }

    @Test
    fun `asFlow emits new values when signal changes`() = runTest {
        val signal = _root_ide_package_.io.github.fenrur.signal.mutableSignalOf(1)
        val flow = signal.asFlow()

        val values = mutableListOf<Int>()
        val job = launch {
            flow.take(3).toList(values)
        }

        // Give time for first emission
        delay(50)
        signal.value = 2
        delay(50)
        signal.value = 3
        delay(50)

        job.join()
        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun `MutableStateFlow asSignal reads value`() = runTest {
        val stateFlow = MutableStateFlow(100)
        val signal = stateFlow.asSignal(this)

        assertEquals(100, signal.value)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal writes value`() = runTest {
        val stateFlow = MutableStateFlow(100)
        val signal = stateFlow.asSignal(this)

        signal.value = 200

        assertEquals(200, stateFlow.value)
        assertEquals(200, signal.value)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal notifies subscribers on signal write`() = runTest {
        val stateFlow = MutableStateFlow(0)
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe {
            it.onSuccess { v -> values.add(v) }
        }

        delay(50) // Wait for initial collection
        signal.value = 1
        delay(50)
        signal.value = 2
        delay(50)

        assertTrue(values.contains(0))
        assertTrue(values.contains(1))
        assertTrue(values.contains(2))
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal notifies subscribers on stateFlow write`() = runTest {
        val stateFlow = MutableStateFlow(0)
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe {
            it.onSuccess { v -> values.add(v) }
        }

        delay(50) // Wait for initial collection
        stateFlow.value = 10
        delay(50)
        stateFlow.value = 20
        delay(50)

        assertTrue(values.contains(0))
        assertTrue(values.contains(10))
        assertTrue(values.contains(20))
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal update works`() = runTest {
        val stateFlow = MutableStateFlow(10)
        val signal = stateFlow.asSignal(this)

        signal.update { it * 2 }

        assertEquals(20, stateFlow.value)
        assertEquals(20, signal.value)
        signal.close()
    }

    @Test
    fun `StateFlow asSignal reads value`() = runTest {
        val mutableStateFlow = MutableStateFlow(100)
        val stateFlow: StateFlow<Int> = mutableStateFlow
        val signal = stateFlow.asSignal(this)

        assertEquals(100, signal.value)
        signal.close()
    }

    @Test
    fun `StateFlow asSignal notifies subscribers on stateFlow change`() = runTest {
        val mutableStateFlow = MutableStateFlow(0)
        val stateFlow: StateFlow<Int> = mutableStateFlow
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe {
            it.onSuccess { v -> values.add(v) }
        }

        delay(50) // Wait for initial collection
        mutableStateFlow.value = 5
        delay(50)
        mutableStateFlow.value = 10
        delay(50)

        assertTrue(values.contains(0))
        assertTrue(values.contains(5))
        assertTrue(values.contains(10))
        signal.close()
    }
}
