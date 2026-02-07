package com.github.fenrur.signal.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class DistinctBySignalTest {

    data class Person(val id: Int, val name: String)

    @Test
    fun `distinctBy signal returns initial value`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }

        assertThat(distinct.value).isEqualTo(Person(1, "John"))
    }

    @Test
    fun `distinctBy signal does not emit when key is same`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Jane") // Same id, different name

        assertThat(values).isEmpty()
        assertThat(distinct.value).isEqualTo(Person(1, "John")) // Retains original
    }

    @Test
    fun `distinctBy signal emits when key changes`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(2, "Jane") // Different id

        assertThat(values).containsExactly(Person(2, "Jane"))
        assertThat(distinct.value).isEqualTo(Person(2, "Jane"))
    }

    @Test
    fun `distinctBy signal with multiple changes`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Johnny") // Same id
        source.value = Person(2, "Jane")   // Different id
        source.value = Person(2, "Janet")  // Same id
        source.value = Person(3, "Bob")    // Different id

        assertThat(values).containsExactly(Person(2, "Jane"), Person(3, "Bob"))
    }

    @Test
    fun `distinctBy signal closes properly`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }

        assertThat(distinct.isClosed).isFalse()

        distinct.close()

        assertThat(distinct.isClosed).isTrue()
    }

    @Test
    fun `distinctBy signal stops receiving after close`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        distinct.close()
        values.clear()

        source.value = Person(2, "Jane")

        assertThat(values).isEmpty()
    }

    @Test
    fun `distinctBy signal with primitive key selector`() {
        val source = DefaultMutableSignal(10)
        val distinct = DistinctBySignal(source) { it / 10 } // Key is 1 for 10-19, 2 for 20-29, etc.
        val values = CopyOnWriteArrayList<Int>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 15 // Key still 1
        source.value = 20 // Key now 2
        source.value = 25 // Key still 2
        source.value = 30 // Key now 3

        assertThat(values).containsExactly(20, 30)
    }

    @Test
    fun `distinctBy signal with string key selector`() {
        val source = DefaultMutableSignal("hello")
        val distinct = DistinctBySignal(source) { it.length }
        val values = CopyOnWriteArrayList<String>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = "world" // Length 5, same as "hello"
        source.value = "hi"    // Length 2, different
        source.value = "ok"    // Length 2, same as "hi"
        source.value = "test"  // Length 4, different

        assertThat(values).containsExactly("hi", "test")
    }

    @Test
    fun `unsubscribe stops receiving notifications`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values = CopyOnWriteArrayList<Person>()

        val unsubscribe = distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(2, "Jane")
        unsubscribe()
        source.value = Person(3, "Bob")

        assertThat(values).containsExactly(Person(2, "Jane"))
    }

    @Test
    fun `subscribe on closed signal returns no-op unsubscriber`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        distinct.close()

        val values = CopyOnWriteArrayList<Person>()
        val unsubscribe = distinct.subscribe { it.onSuccess { v -> values.add(v) } }

        assertThat(values).isEmpty()
        unsubscribe() // Should not throw
    }

    @Test
    fun `multiple subscribers receive same notifications`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }
        val values1 = CopyOnWriteArrayList<Person>()
        val values2 = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onSuccess { v -> values1.add(v) } }
        distinct.subscribe { it.onSuccess { v -> values2.add(v) } }
        values1.clear()
        values2.clear()

        source.value = Person(2, "Jane")

        assertThat(values1).containsExactly(Person(2, "Jane"))
        assertThat(values2).containsExactly(Person(2, "Jane"))
    }

    @Test
    fun `toString shows value and state`() {
        val source = DefaultMutableSignal(Person(1, "John"))
        val distinct = DistinctBySignal(source) { it.id }

        assertThat(distinct.toString()).contains("Person")
        assertThat(distinct.toString()).contains("DistinctBySignal")
    }
}
