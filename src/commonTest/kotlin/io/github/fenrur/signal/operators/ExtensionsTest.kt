package io.github.fenrur.signal.operators

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.impl.DefaultMutableSignal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.add
import io.github.fenrur.signal.operators.addAll
import io.github.fenrur.signal.operators.and
import io.github.fenrur.signal.operators.append
import io.github.fenrur.signal.operators.bimap
import io.github.fenrur.signal.operators.clear
import io.github.fenrur.signal.operators.clearList
import io.github.fenrur.signal.operators.clearMap
import io.github.fenrur.signal.operators.clearSet
import io.github.fenrur.signal.operators.coerceAtLeast
import io.github.fenrur.signal.operators.coerceAtMost
import io.github.fenrur.signal.operators.coerceIn
import io.github.fenrur.signal.operators.combineAll
import io.github.fenrur.signal.operators.contains
import io.github.fenrur.signal.operators.decrement
import io.github.fenrur.signal.operators.distinct
import io.github.fenrur.signal.operators.distinctUntilChanged
import io.github.fenrur.signal.operators.distinctUntilChangedBy
import io.github.fenrur.signal.operators.div
import io.github.fenrur.signal.operators.drop
import io.github.fenrur.signal.operators.eq
import io.github.fenrur.signal.operators.filter
import io.github.fenrur.signal.operators.filterIsInstance
import io.github.fenrur.signal.operators.filterList
import io.github.fenrur.signal.operators.filterNotNull
import io.github.fenrur.signal.operators.firstOrNull
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.flatMapList
import io.github.fenrur.signal.operators.flatten
import io.github.fenrur.signal.operators.getOrNull
import io.github.fenrur.signal.operators.gt
import io.github.fenrur.signal.operators.increment
import io.github.fenrur.signal.operators.isAbsent
import io.github.fenrur.signal.operators.isBlank
import io.github.fenrur.signal.operators.isEmpty
import io.github.fenrur.signal.operators.isNotBlank
import io.github.fenrur.signal.operators.isNotEmpty
import io.github.fenrur.signal.operators.isPresent
import io.github.fenrur.signal.operators.joinToString
import io.github.fenrur.signal.operators.lastOrNull
import io.github.fenrur.signal.operators.length
import io.github.fenrur.signal.operators.lowercase
import io.github.fenrur.signal.operators.lt
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.mapList
import io.github.fenrur.signal.operators.mapNotNull
import io.github.fenrur.signal.operators.mapToString
import io.github.fenrur.signal.operators.minus
import io.github.fenrur.signal.operators.neq
import io.github.fenrur.signal.operators.not
import io.github.fenrur.signal.operators.onEach
import io.github.fenrur.signal.operators.or
import io.github.fenrur.signal.operators.orDefault
import io.github.fenrur.signal.operators.orElse
import io.github.fenrur.signal.operators.pairwise
import io.github.fenrur.signal.operators.plus
import io.github.fenrur.signal.operators.prepend
import io.github.fenrur.signal.operators.put
import io.github.fenrur.signal.operators.rem
import io.github.fenrur.signal.operators.remove
import io.github.fenrur.signal.operators.removeAt
import io.github.fenrur.signal.operators.reversed
import io.github.fenrur.signal.operators.runningReduce
import io.github.fenrur.signal.operators.scan
import io.github.fenrur.signal.operators.size
import io.github.fenrur.signal.operators.sorted
import io.github.fenrur.signal.operators.sortedBy
import io.github.fenrur.signal.operators.sortedDescending
import io.github.fenrur.signal.operators.switchMap
import io.github.fenrur.signal.operators.take
import io.github.fenrur.signal.operators.tap
import io.github.fenrur.signal.operators.times
import io.github.fenrur.signal.operators.toggle
import io.github.fenrur.signal.operators.trim
import io.github.fenrur.signal.operators.uppercase
import io.github.fenrur.signal.operators.withLatestFrom
import io.github.fenrur.signal.operators.xor
import kotlin.test.*

class ExtensionsTest {

    // =============================================================================
    // TRANSFORMATION OPERATORS
    // =============================================================================

    // ==================== map ====================

    @Test
    fun `map transforms signal value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }

        assertEquals(20, mapped.value)
    }

    @Test
    fun `map updates when source changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.map { it * 2 }

        source.value = 20

        assertEquals(40, mapped.value)
    }

    @Test
    fun `map can change type`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val mapped = source.map { "Value: $it" }

        assertEquals("Value: 42", mapped.value)
    }

    // ==================== mapToString ====================

    @Test
    fun `mapToString converts to string`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val mapped = source.mapToString()

        assertEquals("42", mapped.value)
    }

    @Test
    fun `mapToString works with complex objects`() {
        data class Person(val name: String)
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(Person("John"))
        val mapped = source.mapToString()

        assertEquals("Person(name=John)", mapped.value)
    }

    // ==================== mapNotNull ====================

    @Test
    fun `mapNotNull filters out null values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        assertEquals(20, mapped.value)
    }

    @Test
    fun `mapNotNull retains last non-null value when transform returns null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        source.value = 3 // Transform returns null
        assertEquals(20, mapped.value) // Retains previous value

        source.value = 15 // Transform returns non-null
        assertEquals(30, mapped.value)
    }

    @Test
    fun `mapNotNull throws if initial value transforms to null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(3)

        assertFailsWith<IllegalStateException> {
            source.mapNotNull { if (it > 5) it * 2 else null }
        }
    }

    @Test
    fun `mapNotNull notifies only for non-null values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = 20 // non-null
        source.value = 3  // null - should not notify
        source.value = 15 // non-null

        assertEquals(listOf(40, 30), values)
    }

    // ==================== bimap ====================

    @Test
    fun `bimap reads with forward transform`() {
        val source = io.github.fenrur.signal.mutableSignalOf("42")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        assertEquals(42, mapped.value)
    }

    @Test
    fun `bimap writes with reverse transform`() {
        val source = io.github.fenrur.signal.mutableSignalOf("42")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.value = 100
        assertEquals("100", source.value)
        assertEquals(100, mapped.value)
    }

    @Test
    fun `bimap update applies both transforms`() {
        val source = io.github.fenrur.signal.mutableSignalOf("10")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.update { it + 5 }
        assertEquals("15", source.value)
        assertEquals(15, mapped.value)
    }

    @Test
    fun `bimap notifies subscribers on source change`() {
        val source = io.github.fenrur.signal.mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = "2"
        source.value = "3"

        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun `bimap notifies subscribers on mapped write`() {
        val source = io.github.fenrur.signal.mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }

        mapped.value = 10
        mapped.value = 20

        assertEquals(listOf(1, 10, 20), values)
    }

    @Test
    fun `bimap close stops notifications`() {
        val source = io.github.fenrur.signal.mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = mutableListOf<Int>()

        mapped.subscribe { it.onSuccess { v -> values.add(v) } }
        mapped.close()

        source.value = "99"

        assertTrue(mapped.isClosed)
        assertEquals(listOf(1), values) // Only initial value
    }

    @Test
    fun `bimap write on closed signal does nothing`() {
        val source = io.github.fenrur.signal.mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.close()
        mapped.value = 99

        assertEquals("1", source.value)
    }

    @Test
    fun `bimap update on closed signal does nothing`() {
        val source = io.github.fenrur.signal.mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.close()
        mapped.update { it + 100 }

        assertEquals("1", source.value)
    }

    @Test
    fun `bimap can be used as property delegate`() {
        val source = io.github.fenrur.signal.mutableSignalOf("5")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        var prop by mapped
        assertEquals(5, prop)

        prop = 42
        assertEquals("42", source.value)
    }

    @Test
    fun `bimap chaining works`() {
        val source = io.github.fenrur.signal.mutableSignalOf(10)
        val doubled = source.bimap(
            forward = { it * 2 },
            reverse = { it / 2 }
        )
        val asString = doubled.bimap(
            forward = { it.toString() },
            reverse = { it.toInt() }
        )

        assertEquals("20", asString.value)

        asString.value = "100"
        assertEquals(100, doubled.value)
        assertEquals(50, source.value)
    }

    @Test
    fun `bimap with identity transforms is pass-through`() {
        val source = io.github.fenrur.signal.mutableSignalOf(42)
        val identity = source.bimap(
            forward = { it },
            reverse = { it }
        )

        assertEquals(42, identity.value)
        identity.value = 100
        assertEquals(100, source.value)
    }

    // ==================== scan ====================

    @Test
    fun `scan accumulates values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        // scan applies accumulator(initial, source.value) once at construction
        // With lazy subscription, accumulator only runs again when subscribed AND source changes
        val accumulated = source.scan(0) { acc, value -> acc + value }

        // Initial: accumulator(0, 1) = 1
        assertEquals(1, accumulated.value)

        // Subscribe to enable reactive updates
        accumulated.subscribe { }

        source.value = 2
        assertEquals(3, accumulated.value) // 1 + 2

        source.value = 3
        assertEquals(6, accumulated.value) // 3 + 3
    }

    @Test
    fun `scan can change type`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val accumulated = source.scan("") { acc, value -> "$acc$value" }

        // Initial: "" + "1" = "1"
        assertEquals("1", accumulated.value)

        // Subscribe to enable reactive updates
        accumulated.subscribe { }

        source.value = 2
        assertEquals("12", accumulated.value)
    }

    // ==================== runningReduce ====================

    @Test
    fun `runningReduce accumulates from initial value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val reduced = source.runningReduce { acc, value -> acc + value }

        // runningReduce uses scan(value, accumulator)
        // Initial: accumulator(10, 10) = 20
        assertEquals(20, reduced.value)

        // Subscribe to enable reactive updates
        reduced.subscribe { }

        source.value = 5
        assertEquals(25, reduced.value) // 20 + 5
    }

    // ==================== pairwise ====================

    @Test
    fun `pairwise emits pairs of consecutive values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val pairs = source.pairwise()

        // Initial pair is (initial, initial)
        assertEquals(1 to 1, pairs.value)

        // Subscribe to enable reactive tracking of previous values
        pairs.subscribe { }

        source.value = 2
        assertEquals(1 to 2, pairs.value)

        source.value = 3
        assertEquals(2 to 3, pairs.value)
    }

    // ==================== flatten ====================

    @Test
    fun `flatten flattens nested signals`() {
        val inner1 = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val inner2 = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner1 as io.github.fenrur.signal.Signal<Int>)
        val flattened = outer.flatten()

        assertEquals(10, flattened.value)

        outer.value = inner2
        assertEquals(20, flattened.value)
    }

    @Test
    fun `flatten updates when inner signal changes`() {
        val inner = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val outer =
            io.github.fenrur.signal.impl.DefaultMutableSignal(inner as io.github.fenrur.signal.Signal<Int>)
        val flattened = outer.flatten()

        inner.value = 30
        assertEquals(30, flattened.value)
    }

    // ==================== flatMap / switchMap ====================

    @Test
    fun `flatMap maps and flattens`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val flattened = source.flatMap { io.github.fenrur.signal.impl.DefaultMutableSignal(it * 10) }

        assertEquals(10, flattened.value)

        source.value = 2
        assertEquals(20, flattened.value)
    }

    @Test
    fun `switchMap is alias for flatMap`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val switched = source.switchMap { io.github.fenrur.signal.impl.DefaultMutableSignal(it * 10) }

        assertEquals(10, switched.value)
    }

    // =============================================================================
    // FILTERING OPERATORS
    // =============================================================================

    // ==================== filter ====================

    @Test
    fun `filter keeps matching values`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = source.filter { it > 5 }

        assertEquals(10, filtered.value)
    }

    @Test
    fun `filter retains last matching value`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val filtered = source.filter { it > 5 }

        source.value = 3 // doesn't match
        assertEquals(10, filtered.value) // retains previous

        source.value = 15
        assertEquals(15, filtered.value)
    }

    // ==================== filterNotNull ====================

    @Test
    fun `filterNotNull filters out nulls`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(10)
        val filtered = source.filterNotNull()

        assertEquals(10, filtered.value)

        source.value = null
        assertEquals(10, filtered.value) // retains previous

        source.value = 20
        assertEquals(20, filtered.value)
    }

    // ==================== filterIsInstance ====================

    @Test
    fun `filterIsInstance filters by type`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Any>("hello")
        val filtered = source.filterIsInstance<String>()

        assertEquals("hello", filtered.value)

        source.value = 42
        assertEquals("hello", filtered.value) // retains previous

        source.value = "world"
        assertEquals("world", filtered.value)
    }

    // ==================== distinctUntilChangedBy ====================

    @Test
    fun `distinctUntilChangedBy only emits when key changes`() {
        data class Person(val id: Int, val name: String)
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = mutableListOf<Person>()

        distinct.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Jane") // same id, different name
        source.value = Person(2, "Bob")  // different id

        assertEquals(listOf(Person(2, "Bob")), values)
    }

    // ==================== distinctUntilChanged ====================

    @Test
    fun `distinctUntilChanged is a no-op`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val distinct = source.distinctUntilChanged()

        assertSame(source, distinct)
    }

    // =============================================================================
    // COMBINATION OPERATORS
    // =============================================================================

    // ==================== combine ====================

    @Test
    fun `combine2 combines two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val combined = io.github.fenrur.signal.operators.combine(a, b) { x, y -> x + y }

        assertEquals(30, combined.value)
    }

    @Test
    fun `combine3 combines three signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val combined = io.github.fenrur.signal.operators.combine(a, b, c) { x, y, z -> x + y + z }

        assertEquals(6, combined.value)
    }

    @Test
    fun `combine4 combines four signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(4)
        val combined =
            io.github.fenrur.signal.operators.combine(a, b, c, d) { w, x, y, z -> w + x + y + z }

        assertEquals(10, combined.value)
    }

    @Test
    fun `combine5 combines five signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(4)
        val e = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val combined = io.github.fenrur.signal.operators.combine(
            a,
            b,
            c,
            d,
            e
        ) { v, w, x, y, z -> v + w + x + y + z }

        assertEquals(15, combined.value)
    }

    @Test
    fun `combine6 combines six signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(4)
        val e = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val f = io.github.fenrur.signal.impl.DefaultMutableSignal(6)
        val combined = io.github.fenrur.signal.operators.combine(
            a,
            b,
            c,
            d,
            e,
            f
        ) { u, v, w, x, y, z -> u + v + w + x + y + z }

        assertEquals(21, combined.value)
    }

    // ==================== zip ====================

    @Test
    fun `zip combines two signals into pair`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val zipped = io.github.fenrur.signal.operators.zip(a, b)

        assertEquals(10 to "hello", zipped.value)
    }

    @Test
    fun `zip updates when either signal changes`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val zipped = io.github.fenrur.signal.operators.zip(a, b)

        a.value = 20
        assertEquals(20 to "hello", zipped.value)

        b.value = "world"
        assertEquals(20 to "world", zipped.value)
    }

    @Test
    fun `zip3 combines three signals into triple`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("two")
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3.0)
        val zipped = io.github.fenrur.signal.operators.zip(a, b, c)

        assertEquals(Triple(1, "two", 3.0), zipped.value)
    }

    @Test
    fun `zip4 combines four signals into Tuple4`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("two")
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3.0)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val zipped = io.github.fenrur.signal.operators.zip(a, b, c, d)

        assertEquals(io.github.fenrur.signal.operators.Tuple4(1, "two", 3.0, true), zipped.value)
    }

    @Test
    fun `zip5 combines five signals into Tuple5`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("two")
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3.0)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val e = io.github.fenrur.signal.impl.DefaultMutableSignal('x')
        val zipped = io.github.fenrur.signal.operators.zip(a, b, c, d, e)

        assertEquals(io.github.fenrur.signal.operators.Tuple5(1, "two", 3.0, true, 'x'), zipped.value)
    }

    @Test
    fun `zip6 combines six signals into Tuple6`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("two")
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3.0)
        val d = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val e = io.github.fenrur.signal.impl.DefaultMutableSignal('x')
        val f = io.github.fenrur.signal.impl.DefaultMutableSignal(100L)
        val zipped = io.github.fenrur.signal.operators.zip(a, b, c, d, e, f)

        assertEquals(io.github.fenrur.signal.operators.Tuple6(1, "two", 3.0, true, 'x', 100L), zipped.value)
    }

    // ==================== withLatestFrom ====================

    @Test
    fun `withLatestFrom combines with latest from other`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertEquals(110, combined.value)
    }

    @Test
    fun `withLatestFrom only emits when source changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = mutableListOf<Int>()

        combined.subscribe { it.onSuccess { v -> values.add(v) } }
        values.clear()

        other.value = 200 // should NOT trigger emission
        assertTrue(values.isEmpty())

        source.value = 20 // should trigger emission with latest from other
        assertEquals(listOf(220), values)
    }

    @Test
    fun `withLatestFrom into pair`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val other = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val combined = source.withLatestFrom(other)

        assertEquals(10 to "hello", combined.value)
    }

    // ==================== combineAll ====================

    @Test
    fun `combineAll combines multiple signals into list`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val combined = io.github.fenrur.signal.operators.combineAll(a, b, c)

        assertEquals(listOf(1, 2, 3), combined.value)
    }

    @Test
    fun `combineAll with empty returns empty list`() {
        val combined = io.github.fenrur.signal.operators.combineAll<Int>()
        assertTrue(combined.value.isEmpty())
    }

    @Test
    fun `combineAll updates when any signal changes`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(1)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val combined = io.github.fenrur.signal.operators.combineAll(a, b)

        a.value = 10
        assertEquals(listOf(10, 2), combined.value)
    }

    @Test
    fun `list combineAll extension`() {
        val signals = listOf(
            io.github.fenrur.signal.impl.DefaultMutableSignal(1),
            io.github.fenrur.signal.impl.DefaultMutableSignal(2),
            io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        )
        val combined = signals.combineAll()

        assertEquals(listOf(1, 2, 3), combined.value)
    }

    // =============================================================================
    // BOOLEAN OPERATORS
    // =============================================================================

    // ==================== not ====================

    @Test
    fun `not negates boolean signal`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val negated = source.not()

        assertFalse(negated.value)
    }

    @Test
    fun `not updates when source changes`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val negated = source.not()

        source.value = false

        assertTrue(negated.value)
    }

    // ==================== and ====================

    @Test
    fun `and combines two boolean signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val result = a.and(b)

        assertTrue(result.value)

        b.value = false
        assertFalse(result.value)
    }

    // ==================== or ====================

    @Test
    fun `or combines two boolean signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val result = a.or(b)

        assertFalse(result.value)

        a.value = true
        assertTrue(result.value)
    }

    // ==================== xor ====================

    @Test
    fun `xor combines two boolean signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val result = a.xor(b)

        assertFalse(result.value)

        b.value = false
        assertTrue(result.value)
    }

    // ==================== allOf ====================

    @Test
    fun `allOf returns true if all signals are true`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(true)
        val result = io.github.fenrur.signal.operators.allOf(a, b, c)

        assertTrue(result.value)

        b.value = false
        assertFalse(result.value)
    }

    // ==================== anyOf ====================

    @Test
    fun `anyOf returns true if any signal is true`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val c = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val result = io.github.fenrur.signal.operators.anyOf(a, b, c)

        assertFalse(result.value)

        b.value = true
        assertTrue(result.value)
    }

    // ==================== noneOf ====================

    @Test
    fun `noneOf returns true if no signal is true`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(false)
        val result = io.github.fenrur.signal.operators.noneOf(a, b)

        assertTrue(result.value)

        a.value = true
        assertFalse(result.value)
    }

    // =============================================================================
    // NUMERIC OPERATORS
    // =============================================================================

    // ==================== plus ====================

    @Test
    fun `plus Int adds two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val result = a + b

        assertEquals(30, result.value)
    }

    @Test
    fun `plus Long adds two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20L)
        val result = a + b

        assertEquals(30L, result.value)
    }

    @Test
    fun `plus Double adds two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10.5)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20.5)
        val result = a + b

        assertEquals(31.0, result.value)
    }

    @Test
    fun `plus Float adds two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10.5f)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20.5f)
        val result = a + b

        assertEquals(31.0f, result.value)
    }

    // ==================== minus ====================

    @Test
    fun `minus Int subtracts two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(30)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = a - b

        assertEquals(20, result.value)
    }

    @Test
    fun `minus Long subtracts two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(30L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val result = a - b

        assertEquals(20L, result.value)
    }

    @Test
    fun `minus Double subtracts two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(30.5)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(10.5)
        val result = a - b

        assertEquals(20.0, result.value)
    }

    @Test
    fun `minus Float subtracts two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(30.5f)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(10.5f)
        val result = a - b

        assertEquals(20.0f, result.value)
    }

    // ==================== times ====================

    @Test
    fun `times Int multiplies two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4)
        val result = a * b

        assertEquals(20, result.value)
    }

    @Test
    fun `times Long multiplies two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(5L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4L)
        val result = a * b

        assertEquals(20L, result.value)
    }

    @Test
    fun `times Double multiplies two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(2.5)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4.0)
        val result = a * b

        assertEquals(10.0, result.value)
    }

    @Test
    fun `times Float multiplies two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(2.5f)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4.0f)
        val result = a * b

        assertEquals(10.0f, result.value)
    }

    // ==================== div ====================

    @Test
    fun `div Int divides two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4)
        val result = a / b

        assertEquals(5, result.value)
    }

    @Test
    fun `div Long divides two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(20L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4L)
        val result = a / b

        assertEquals(5L, result.value)
    }

    @Test
    fun `div Double divides two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4.0)
        val result = a / b

        assertEquals(2.5, result.value)
    }

    @Test
    fun `div Float divides two signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0f)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(4.0f)
        val result = a / b

        assertEquals(2.5f, result.value)
    }

    // ==================== rem ====================

    @Test
    fun `rem Int computes remainder`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val result = a % b

        assertEquals(1, result.value)
    }

    @Test
    fun `rem Long computes remainder`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(3L)
        val result = a % b

        assertEquals(1L, result.value)
    }

    // ==================== coerceIn ====================

    @Test
    fun `coerceIn Int clamps value to range`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(0)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = value.coerceIn(min, max)

        assertEquals(10, result.value)

        value.value = 5
        assertEquals(5, result.value)

        value.value = -5
        assertEquals(0, result.value)
    }

    @Test
    fun `coerceIn Long clamps value to range`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15L)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(0L)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val result = value.coerceIn(min, max)

        assertEquals(10L, result.value)
    }

    @Test
    fun `coerceIn Double clamps value to range`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15.0)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(0.0)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0)
        val result = value.coerceIn(min, max)

        assertEquals(10.0, result.value)
    }

    // ==================== coerceAtLeast ====================

    @Test
    fun `coerceAtLeast Int with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = value.coerceAtLeast(min)

        assertEquals(10, result.value)

        value.value = 15
        assertEquals(15, result.value)
    }

    @Test
    fun `coerceAtLeast Int with constant`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val result = value.coerceAtLeast(10)

        assertEquals(10, result.value)

        value.value = 15
        assertEquals(15, result.value)
    }

    @Test
    fun `coerceAtLeast Long with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(5L)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val result = value.coerceAtLeast(min)

        assertEquals(10L, result.value)
    }

    @Test
    fun `coerceAtLeast Double with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(5.0)
        val min = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0)
        val result = value.coerceAtLeast(min)

        assertEquals(10.0, result.value)
    }

    // ==================== coerceAtMost ====================

    @Test
    fun `coerceAtMost Int with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = value.coerceAtMost(max)

        assertEquals(10, result.value)

        value.value = 5
        assertEquals(5, result.value)
    }

    @Test
    fun `coerceAtMost Int with constant`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15)
        val result = value.coerceAtMost(10)

        assertEquals(10, result.value)
    }

    @Test
    fun `coerceAtMost Long with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15L)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val result = value.coerceAtMost(max)

        assertEquals(10L, result.value)
    }

    @Test
    fun `coerceAtMost Double with signal`() {
        val value = io.github.fenrur.signal.impl.DefaultMutableSignal(15.0)
        val max = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0)
        val result = value.coerceAtMost(max)

        assertEquals(10.0, result.value)
    }

    // =============================================================================
    // COMPARISON OPERATORS
    // =============================================================================

    // ==================== gt ====================

    @Test
    fun `gt Int compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val result = a gt b

        assertTrue(result.value)

        a.value = 3
        assertFalse(result.value)
    }

    @Test
    fun `gt Long compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5L)
        val result = a gt b

        assertTrue(result.value)
    }

    @Test
    fun `gt Double compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10.0)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5.0)
        val result = a gt b

        assertTrue(result.value)
    }

    // ==================== lt ====================

    @Test
    fun `lt Int compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(3)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5)
        val result = a lt b

        assertTrue(result.value)

        a.value = 10
        assertFalse(result.value)
    }

    @Test
    fun `lt Long compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(3L)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5L)
        val result = a lt b

        assertTrue(result.value)
    }

    @Test
    fun `lt Double compares signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(3.0)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(5.0)
        val result = a lt b

        assertTrue(result.value)
    }

    // ==================== eq ====================

    @Test
    fun `eq compares signals for equality`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = a eq b

        assertTrue(result.value)

        b.value = 20
        assertFalse(result.value)
    }

    @Test
    fun `eq works with strings`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val result = a eq b

        assertTrue(result.value)
    }

    // ==================== neq ====================

    @Test
    fun `neq compares signals for inequality`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val result = a neq b

        assertTrue(result.value)

        b.value = 10
        assertFalse(result.value)
    }

    // =============================================================================
    // STRING OPERATORS
    // =============================================================================

    // ==================== plus (String) ====================

    @Test
    fun `plus String concatenates signals`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal("Hello")
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(" World")
        val result = a + b

        assertEquals("Hello World", result.value)
    }

    // ==================== isEmpty ====================

    @Test
    fun `isEmpty String returns true for empty`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("")
        val result = source.isEmpty()

        assertTrue(result.value)

        source.value = "hello"
        assertFalse(result.value)
    }

    // ==================== isNotEmpty ====================

    @Test
    fun `isNotEmpty String returns true for non-empty`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val result = source.isNotEmpty()

        assertTrue(result.value)

        source.value = ""
        assertFalse(result.value)
    }

    // ==================== isBlank ====================

    @Test
    fun `isBlank returns true for blank strings`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("   ")
        val result = source.isBlank()

        assertTrue(result.value)

        source.value = "hello"
        assertFalse(result.value)
    }

    // ==================== isNotBlank ====================

    @Test
    fun `isNotBlank returns true for non-blank strings`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val result = source.isNotBlank()

        assertTrue(result.value)

        source.value = "   "
        assertFalse(result.value)
    }

    // ==================== length ====================

    @Test
    fun `length returns string length`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val result = source.length()

        assertEquals(5, result.value)

        source.value = "hi"
        assertEquals(2, result.value)
    }

    // ==================== trim ====================

    @Test
    fun `trim removes whitespace`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("  hello  ")
        val result = source.trim()

        assertEquals("hello", result.value)
    }

    // ==================== uppercase ====================

    @Test
    fun `uppercase converts to uppercase`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("hello")
        val result = source.uppercase()

        assertEquals("HELLO", result.value)
    }

    // ==================== lowercase ====================

    @Test
    fun `lowercase converts to lowercase`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal("HELLO")
        val result = source.lowercase()

        assertEquals("hello", result.value)
    }

    // =============================================================================
    // COLLECTION OPERATORS
    // =============================================================================

    // ==================== size ====================

    @Test
    fun `size returns list size`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.size()

        assertEquals(3, result.value)

        source.value = listOf(1)
        assertEquals(1, result.value)
    }

    // ==================== isEmpty (List) ====================

    @Test
    fun `isEmpty List returns true for empty list`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(emptyList<Int>())
        val result = source.isEmpty()

        assertTrue(result.value)

        source.value = listOf(1)
        assertFalse(result.value)
    }

    // ==================== isNotEmpty (List) ====================

    @Test
    fun `isNotEmpty List returns true for non-empty list`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2))
        val result = source.isNotEmpty()

        assertTrue(result.value)

        source.value = emptyList()
        assertFalse(result.value)
    }

    // ==================== firstOrNull ====================

    @Test
    fun `firstOrNull returns first element`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.firstOrNull()

        assertEquals(1, result.value)
    }

    @Test
    fun `firstOrNull returns null for empty list`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(emptyList<Int>())
        val result = source.firstOrNull()

        assertNull(result.value)
    }

    // ==================== lastOrNull ====================

    @Test
    fun `lastOrNull returns last element`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.lastOrNull()

        assertEquals(3, result.value)
    }

    @Test
    fun `lastOrNull returns null for empty list`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(emptyList<Int>())
        val result = source.lastOrNull()

        assertNull(result.value)
    }

    // ==================== getOrNull ====================

    @Test
    fun `getOrNull returns element at index`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(10, 20, 30))
        val result = source.getOrNull(1)

        assertEquals(20, result.value)
    }

    @Test
    fun `getOrNull returns null for out of bounds`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(10, 20))
        val result = source.getOrNull(5)

        assertNull(result.value)
    }

    @Test
    fun `getOrNull with signal index`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(10, 20, 30))
        val index = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val result = source.getOrNull(index)

        assertEquals(30, result.value)

        index.value = 0
        assertEquals(10, result.value)
    }

    // ==================== contains ====================

    @Test
    fun `contains checks element presence`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.contains(2)

        assertTrue(result.value)
    }

    @Test
    fun `contains returns false for missing element`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.contains(5)

        assertFalse(result.value)
    }

    @Test
    fun `contains with signal element`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val element = io.github.fenrur.signal.impl.DefaultMutableSignal(2)
        val result = source.contains(element)

        assertTrue(result.value)

        element.value = 5
        assertFalse(result.value)
    }

    // ==================== filterList ====================

    @Test
    fun `filterList filters list elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3, 4, 5))
        val result = source.filterList { it > 2 }

        assertEquals(listOf(3, 4, 5), result.value)
    }

    // ==================== mapList ====================

    @Test
    fun `mapList transforms list elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.mapList { it * 2 }

        assertEquals(listOf(2, 4, 6), result.value)
    }

    // ==================== flatMapList ====================

    @Test
    fun `flatMapList flatMaps list elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2))
        val result = source.flatMapList { listOf(it, it * 10) }

        assertEquals(listOf(1, 10, 2, 20), result.value)
    }

    // ==================== sorted ====================

    @Test
    fun `sorted sorts list ascending`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(3, 1, 4, 1, 5))
        val result = source.sorted()

        assertEquals(listOf(1, 1, 3, 4, 5), result.value)
    }

    // ==================== sortedDescending ====================

    @Test
    fun `sortedDescending sorts list descending`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(3, 1, 4, 1, 5))
        val result = source.sortedDescending()

        assertEquals(listOf(5, 4, 3, 1, 1), result.value)
    }

    // ==================== sortedBy ====================

    @Test
    fun `sortedBy sorts by selector`() {
        data class Person(val name: String, val age: Int)
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(
            listOf(
                Person("Bob", 30),
                Person("Alice", 25),
                Person("Charlie", 35)
            )
        )
        val result = source.sortedBy { it.age }

        assertEquals(listOf("Alice", "Bob", "Charlie"), result.value.map { it.name })
    }

    // ==================== reversed ====================

    @Test
    fun `reversed reverses list`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.reversed()

        assertEquals(listOf(3, 2, 1), result.value)
    }

    // ==================== take ====================

    @Test
    fun `take returns first n elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3, 4, 5))
        val result = source.take(3)

        assertEquals(listOf(1, 2, 3), result.value)
    }

    // ==================== drop ====================

    @Test
    fun `drop removes first n elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3, 4, 5))
        val result = source.drop(2)

        assertEquals(listOf(3, 4, 5), result.value)
    }

    // ==================== distinct ====================

    @Test
    fun `distinct removes duplicates`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 2, 3, 1))
        val result = source.distinct()

        assertEquals(listOf(1, 2, 3), result.value)
    }

    // ==================== joinToString ====================

    @Test
    fun `joinToString joins elements`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.joinToString(", ")

        assertEquals("1, 2, 3", result.value)
    }

    @Test
    fun `joinToString with prefix and postfix`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(listOf(1, 2, 3))
        val result = source.joinToString(", ", "[", "]")

        assertEquals("[1, 2, 3]", result.value)
    }

    // =============================================================================
    // UTILITY OPERATORS
    // =============================================================================

    // ==================== orDefault ====================

    @Test
    fun `orDefault provides default for null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(null)
        val result = source.orDefault(42)

        assertEquals(42, result.value)

        source.value = 10
        assertEquals(10, result.value)
    }

    @Test
    fun `orDefault with signal provides default`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(null)
        val default = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val result = source.orDefault(default)

        assertEquals(42, result.value)

        source.value = 10
        assertEquals(10, result.value)

        source.value = null
        default.value = 100
        assertEquals(100, result.value)
    }

    // ==================== orElse ====================

    @Test
    fun `orElse is alias for orDefault`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(null)
        val fallback = io.github.fenrur.signal.impl.DefaultMutableSignal(42)
        val result = source.orElse(fallback)

        assertEquals(42, result.value)
    }

    // ==================== onEach ====================

    @Test
    fun `onEach executes side effect`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val sideEffects = mutableListOf<Int>()
        val result = source.onEach { sideEffects.add(it) }

        assertEquals(10, result.value)
        assertTrue(sideEffects.contains(10))

        source.value = 20
        result.value // trigger
        assertTrue(sideEffects.contains(20))
    }

    // ==================== tap ====================

    @Test
    fun `tap is alias for onEach`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val tapped = mutableListOf<Int>()
        val result = source.tap { tapped.add(it) }

        assertEquals(10, result.value)
        assertTrue(tapped.contains(10))
    }

    // ==================== isPresent ====================

    @Test
    fun `isPresent returns true for non-null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(10)
        val result = source.isPresent()

        assertTrue(result.value)

        source.value = null
        assertFalse(result.value)
    }

    // ==================== isAbsent ====================

    @Test
    fun `isAbsent returns true for null`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal<Int?>(null)
        val result = source.isAbsent()

        assertTrue(result.value)

        source.value = 10
        assertFalse(result.value)
    }

    // =============================================================================
    // MUTABLE SIGNAL OPERATORS
    // =============================================================================

    // ==================== toggle ====================

    @Test
    fun `toggle flips boolean`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(false)

        signal.toggle()
        assertTrue(signal.value)

        signal.toggle()
        assertFalse(signal.value)
    }

    // ==================== increment ====================

    @Test
    fun `increment Int increases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10)

        signal.increment()
        assertEquals(11, signal.value)

        signal.increment(5)
        assertEquals(16, signal.value)
    }

    @Test
    fun `increment Long increases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10L)

        signal.increment()
        assertEquals(11L, signal.value)

        signal.increment(5L)
        assertEquals(16L, signal.value)
    }

    @Test
    fun `increment Double increases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10.0)

        signal.increment()
        assertEquals(11.0, signal.value)

        signal.increment(0.5)
        assertEquals(11.5, signal.value)
    }

    // ==================== decrement ====================

    @Test
    fun `decrement Int decreases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10)

        signal.decrement()
        assertEquals(9, signal.value)

        signal.decrement(5)
        assertEquals(4, signal.value)
    }

    @Test
    fun `decrement Long decreases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10L)

        signal.decrement()
        assertEquals(9L, signal.value)

        signal.decrement(5L)
        assertEquals(4L, signal.value)
    }

    @Test
    fun `decrement Double decreases value`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(10.0)

        signal.decrement()
        assertEquals(9.0, signal.value)

        signal.decrement(0.5)
        assertEquals(8.5, signal.value)
    }

    // ==================== append ====================

    @Test
    fun `append adds suffix to string`() {
        val signal = io.github.fenrur.signal.mutableSignalOf("Hello")

        signal.append(" World")
        assertEquals("Hello World", signal.value)
    }

    // ==================== prepend ====================

    @Test
    fun `prepend adds prefix to string`() {
        val signal = io.github.fenrur.signal.mutableSignalOf("World")

        signal.prepend("Hello ")
        assertEquals("Hello World", signal.value)
    }

    // ==================== clear (String) ====================

    @Test
    fun `clear empties string`() {
        val signal = io.github.fenrur.signal.mutableSignalOf("Hello")

        signal.clear()
        assertTrue(signal.value.isEmpty())
    }

    // ==================== add (List) ====================

    @Test
    fun `add appends to list`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(listOf(1, 2))

        signal.add(3)
        assertEquals(listOf(1, 2, 3), signal.value)
    }

    // ==================== addAll ====================

    @Test
    fun `addAll appends multiple to list`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(listOf(1))

        signal.addAll(listOf(2, 3, 4))
        assertEquals(listOf(1, 2, 3, 4), signal.value)
    }

    // ==================== remove (List) ====================

    @Test
    fun `remove removes from list`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(listOf(1, 2, 3))

        signal.remove(2)
        assertEquals(listOf(1, 3), signal.value)
    }

    // ==================== removeAt ====================

    @Test
    fun `removeAt removes element at index`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(listOf(10, 20, 30))

        signal.removeAt(1)
        assertEquals(listOf(10, 30), signal.value)
    }

    // ==================== clearList ====================

    @Test
    fun `clearList empties list`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(listOf(1, 2, 3))

        signal.clearList()
        assertTrue(signal.value.isEmpty())
    }

    // ==================== add (Set) ====================

    @Test
    fun `add appends to set`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(setOf(1, 2))

        signal.add(3)
        assertEquals(setOf(1, 2, 3), signal.value)
    }

    // ==================== remove (Set) ====================

    @Test
    fun `remove removes from set`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(setOf(1, 2, 3))

        signal.remove(2)
        assertEquals(setOf(1, 3), signal.value)
    }

    // ==================== clearSet ====================

    @Test
    fun `clearSet empties set`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(setOf(1, 2, 3))

        signal.clearSet()
        assertTrue(signal.value.isEmpty())
    }

    // ==================== put (Map) ====================

    @Test
    fun `put adds entry to map`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(mapOf("a" to 1))

        signal.put("b", 2)
        assertEquals(mapOf("a" to 1, "b" to 2), signal.value)
    }

    // ==================== remove (Map) ====================

    @Test
    fun `remove removes key from map`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(mapOf("a" to 1, "b" to 2))

        signal.remove("a")
        assertEquals(mapOf("b" to 2), signal.value)
    }

    // ==================== clearMap ====================

    @Test
    fun `clearMap empties map`() {
        val signal = io.github.fenrur.signal.mutableSignalOf(mapOf("a" to 1, "b" to 2))

        signal.clearMap()
        assertTrue(signal.value.isEmpty())
    }

    // =============================================================================
    // CHAINING
    // =============================================================================

    @Test
    fun `operators can be chained`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val result = source
            .map { it * 2 }
            .map { it + 5 }
            .mapToString()

        assertEquals("25", result.value)

        source.value = 20
        assertEquals("45", result.value)
    }

    @Test
    fun `combine and map can be chained`() {
        val a = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val b = io.github.fenrur.signal.impl.DefaultMutableSignal(20)
        val result = io.github.fenrur.signal.operators.combine(a, b) { x, y -> x + y }
            .map { it * 2 }
            .mapToString()

        assertEquals("60", result.value)
    }

    // ==================== notifications through chain ====================

    @Test
    fun `changes propagate through chain`() {
        val source = io.github.fenrur.signal.impl.DefaultMutableSignal(10)
        val doubled = source.map { it * 2 }
        val values = mutableListOf<Int>()

        doubled.subscribe { it.onSuccess { v -> values.add(v) } }

        source.value = 20
        source.value = 30

        assertEquals(listOf(20, 40, 60), values)
    }
}
