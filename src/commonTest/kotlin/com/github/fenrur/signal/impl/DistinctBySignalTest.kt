package com.github.fenrur.signal.impl

import com.github.fenrur.signal.operators.distinctUntilChangedBy
import kotlin.test.*

class DistinctBySignalTest {

    data class Person(val id: Int, val name: String)

    @Test
    fun `distinctBy signal returns initial value`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }

        assertEquals(Person(1, "John"), distinct.value)
    }

    @Test
    fun `distinctBy signal does not emit when key is same`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Jane") // Same id, different name

        assertTrue(values.isEmpty())
        assertEquals(Person(1, "John"), distinct.value) // Retains original
    }

    @Test
    fun `distinctBy signal emits when key changes`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(2, "Jane") // Different id

        assertEquals(listOf(Person(2, "Jane")), values)
        assertEquals(Person(2, "Jane"), distinct.value)
    }

    @Test
    fun `distinctBy signal with multiple changes`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Johnny") // Same id
        source.value = Person(2, "Jane")   // Different id
        source.value = Person(2, "Janet")  // Same id
        source.value = Person(3, "Bob")    // Different id

        assertEquals(listOf(Person(2, "Jane"), Person(3, "Bob")), values)
    }

    @Test
    fun `distinctBy signal closes properly`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }

        assertFalse(distinct.isClosed)

        distinct.close()

        assertTrue(distinct.isClosed)
    }

    @Test
    fun `distinctBy signal stops receiving after close`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        distinct.close()
        values.clear()

        source.value = Person(2, "Jane")

        assertTrue(values.isEmpty())
    }

    @Test
    fun `distinctBy signal with primitive key selector`() {
        val source = DefaultMutableSignal(10)
        val distinct = source.distinctUntilChangedBy { it / 10 } // Key is 1 for 10-19, 2 for 20-29, etc.
        val values = mutableListOf<Int>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 15 // Key still 1
        source.value = 20 // Key now 2
        source.value = 25 // Key still 2
        source.value = 30 // Key now 3

        assertEquals(listOf(20, 30), values)
    }

    @Test
    fun `distinctBy signal with string key selector`() {
        val source = DefaultMutableSignal("hello")
        val distinct = source.distinctUntilChangedBy { it.length }
        val values = mutableListOf<String>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = "world" // Length 5, same as "hello"
        source.value = "hi"    // Length 2, different
        source.value = "ok"    // Length 2, same as "hi"
        source.value = "test"  // Length 4, different

        assertEquals(listOf("hi", "test"), values)
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        val unsubscribe = distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(2, "Jane")
        unsubscribe()
        source.value = Person(3, "Bob")

        assertEquals(listOf(Person(2, "Jane")), values)
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        distinct.close()

        val values = mutableListOf<Person>()
        val unsubscribe = distinct.subscribe { it.onSuccess { v -> values.add(v) } }

        assertTrue(values.isEmpty())
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values1 = mutableListOf<Person>()
        val values2 = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values1.add(v) } }
        distinct.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = Person(2, "Jane")

        assertEquals(listOf(Person(2, "Jane")), values1)
        assertEquals(listOf(Person(2, "Jane")), values2)
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }

        assertTrue(distinct.toString().contains("Person"))
        assertTrue(distinct.toString().contains("DistinctBySignal"))
    }
}
