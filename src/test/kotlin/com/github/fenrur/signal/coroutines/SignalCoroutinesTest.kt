package com.github.fenrur.signal.coroutines

import com.github.fenrur.signal.asFlow
import com.github.fenrur.signal.asSignal
import com.github.fenrur.signal.mutableSignalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SignalCoroutinesTest {

    @Test
    fun `asFlow emits current value immediately`() = runBlocking {
        val signal = mutableSignalOf(42)
        val flow = signal.asFlow()

        val firstValue = flow.first()
        assertThat(firstValue).isEqualTo(42)
    }

    @Test
    fun `asFlow emits new values when signal changes`() = runBlocking {
        val signal = mutableSignalOf(1)
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
        assertThat(values).containsExactly(1, 2, 3)
    }

    @Test
    fun `MutableStateFlow asSignal reads value`() = runBlocking {
        val stateFlow = MutableStateFlow(100)
        val signal = stateFlow.asSignal(this)

        assertThat(signal.value).isEqualTo(100)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal writes value`() = runBlocking {
        val stateFlow = MutableStateFlow(100)
        val signal = stateFlow.asSignal(this)

        signal.value = 200

        assertThat(stateFlow.value).isEqualTo(200)
        assertThat(signal.value).isEqualTo(200)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal notifies subscribers on signal write`() = runBlocking {
        val stateFlow = MutableStateFlow(0)
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe { either ->
            either.fold({}, { values.add(it) })
        }

        delay(50) // Wait for initial collection
        signal.value = 1
        delay(50)
        signal.value = 2
        delay(50)

        assertThat(values).contains(0, 1, 2)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal notifies subscribers on stateFlow write`() = runBlocking {
        val stateFlow = MutableStateFlow(0)
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe { either ->
            either.fold({}, { values.add(it) })
        }

        delay(50) // Wait for initial collection
        stateFlow.value = 10
        delay(50)
        stateFlow.value = 20
        delay(50)

        assertThat(values).contains(0, 10, 20)
        signal.close()
    }

    @Test
    fun `MutableStateFlow asSignal update works`() = runBlocking {
        val stateFlow = MutableStateFlow(10)
        val signal = stateFlow.asSignal(this)

        signal.update { it * 2 }

        assertThat(stateFlow.value).isEqualTo(20)
        assertThat(signal.value).isEqualTo(20)
        signal.close()
    }

    @Test
    fun `StateFlow asSignal reads value`() = runBlocking {
        val mutableStateFlow = MutableStateFlow(100)
        val stateFlow: StateFlow<Int> = mutableStateFlow
        val signal = stateFlow.asSignal(this)

        assertThat(signal.value).isEqualTo(100)
        signal.close()
    }

    @Test
    fun `StateFlow asSignal notifies subscribers on stateFlow change`() = runBlocking {
        val mutableStateFlow = MutableStateFlow(0)
        val stateFlow: StateFlow<Int> = mutableStateFlow
        val signal = stateFlow.asSignal(this)

        val values = mutableListOf<Int>()
        signal.subscribe { either ->
            either.fold({}, { values.add(it) })
        }

        delay(50) // Wait for initial collection
        mutableStateFlow.value = 5
        delay(50)
        mutableStateFlow.value = 10
        delay(50)

        assertThat(values).contains(0, 5, 10)
        signal.close()
    }
}
