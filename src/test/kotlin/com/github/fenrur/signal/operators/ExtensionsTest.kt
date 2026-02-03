package com.github.fenrur.signal.operators

import com.github.fenrur.signal.impl.CowSignal
import com.github.fenrur.signal.mutableSignalOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class ExtensionsTest {

    // =============================================================================
    // TRANSFORMATION OPERATORS
    // =============================================================================

    // ==================== map ====================

    @Test
    fun `map transforms signal value`() {
        val source = CowSignal(10)
        val mapped = source.map { it * 2 }

        assertThat(mapped.value).isEqualTo(20)
    }

    @Test
    fun `map updates when source changes`() {
        val source = CowSignal(10)
        val mapped = source.map { it * 2 }

        source.value = 20

        assertThat(mapped.value).isEqualTo(40)
    }

    @Test
    fun `map can change type`() {
        val source = CowSignal(42)
        val mapped = source.map { "Value: $it" }

        assertThat(mapped.value).isEqualTo("Value: 42")
    }

    // ==================== mapToString ====================

    @Test
    fun `mapToString converts to string`() {
        val source = CowSignal(42)
        val mapped = source.mapToString()

        assertThat(mapped.value).isEqualTo("42")
    }

    @Test
    fun `mapToString works with complex objects`() {
        data class Person(val name: String)
        val source = CowSignal(Person("John"))
        val mapped = source.mapToString()

        assertThat(mapped.value).isEqualTo("Person(name=John)")
    }

    // ==================== mapNotNull ====================

    @Test
    fun `mapNotNull filters out null values`() {
        val source = CowSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        assertThat(mapped.value).isEqualTo(20)
    }

    @Test
    fun `mapNotNull retains last non-null value when transform returns null`() {
        val source = CowSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }

        source.value = 3 // Transform returns null
        assertThat(mapped.value).isEqualTo(20) // Retains previous value

        source.value = 15 // Transform returns non-null
        assertThat(mapped.value).isEqualTo(30)
    }

    @Test
    fun `mapNotNull throws if initial value transforms to null`() {
        val source = CowSignal(3)

        assertThatThrownBy {
            source.mapNotNull { if (it > 5) it * 2 else null }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `mapNotNull notifies only for non-null values`() {
        val source = CowSignal(10)
        val mapped = source.mapNotNull { if (it > 5) it * 2 else null }
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }
        values.clear()

        source.value = 20 // non-null
        source.value = 3  // null - should not notify
        source.value = 15 // non-null

        assertThat(values).containsExactly(40, 30)
    }

    // ==================== bimap ====================

    @Test
    fun `bimap reads with forward transform`() {
        val source = mutableSignalOf("42")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        assertThat(mapped.value).isEqualTo(42)
    }

    @Test
    fun `bimap writes with reverse transform`() {
        val source = mutableSignalOf("42")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.value = 100
        assertThat(source.value).isEqualTo("100")
        assertThat(mapped.value).isEqualTo(100)
    }

    @Test
    fun `bimap update applies both transforms`() {
        val source = mutableSignalOf("10")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.update { it + 5 }
        assertThat(source.value).isEqualTo("15")
        assertThat(mapped.value).isEqualTo(15)
    }

    @Test
    fun `bimap notifies subscribers on source change`() {
        val source = mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }

        source.value = "2"
        source.value = "3"

        assertThat(values).containsExactly(1, 2, 3)
    }

    @Test
    fun `bimap notifies subscribers on mapped write`() {
        val source = mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }

        mapped.value = 10
        mapped.value = 20

        assertThat(values).containsExactly(1, 10, 20)
    }

    @Test
    fun `bimap close stops notifications`() {
        val source = mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )
        val values = CopyOnWriteArrayList<Int>()

        mapped.subscribe { it.onRight { v -> values.add(v) } }
        mapped.close()

        source.value = "99"

        assertThat(mapped.isClosed).isTrue()
        assertThat(values).containsExactly(1) // Only initial value
    }

    @Test
    fun `bimap write on closed signal does nothing`() {
        val source = mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.close()
        mapped.value = 99

        assertThat(source.value).isEqualTo("1")
    }

    @Test
    fun `bimap update on closed signal does nothing`() {
        val source = mutableSignalOf("1")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        mapped.close()
        mapped.update { it + 100 }

        assertThat(source.value).isEqualTo("1")
    }

    @Test
    fun `bimap can be used as property delegate`() {
        val source = mutableSignalOf("5")
        val mapped = source.bimap(
            forward = { it.toInt() },
            reverse = { it.toString() }
        )

        var prop by mapped
        assertThat(prop).isEqualTo(5)

        prop = 42
        assertThat(source.value).isEqualTo("42")
    }

    @Test
    fun `bimap chaining works`() {
        val source = mutableSignalOf(10)
        val doubled = source.bimap(
            forward = { it * 2 },
            reverse = { it / 2 }
        )
        val asString = doubled.bimap(
            forward = { it.toString() },
            reverse = { it.toInt() }
        )

        assertThat(asString.value).isEqualTo("20")

        asString.value = "100"
        assertThat(doubled.value).isEqualTo(100)
        assertThat(source.value).isEqualTo(50)
    }

    @Test
    fun `bimap with identity transforms is pass-through`() {
        val source = mutableSignalOf(42)
        val identity = source.bimap(
            forward = { it },
            reverse = { it }
        )

        assertThat(identity.value).isEqualTo(42)
        identity.value = 100
        assertThat(source.value).isEqualTo(100)
    }

    // ==================== scan ====================

    @Test
    fun `scan accumulates values`() {
        val source = CowSignal(1)
        // scan applies accumulator(initial, source.value) immediately, then subscribes
        // and the subscription callback also applies it once more with the current value
        val accumulated = source.scan(0) { acc, value -> acc + value }

        // Initial: accumulator(0, 1) = 1, then subscribe triggers accumulator(1, 1) = 2
        assertThat(accumulated.value).isEqualTo(2)

        source.value = 2
        assertThat(accumulated.value).isEqualTo(4) // 2 + 2

        source.value = 3
        assertThat(accumulated.value).isEqualTo(7) // 4 + 3
    }

    @Test
    fun `scan can change type`() {
        val source = CowSignal(1)
        val accumulated = source.scan("") { acc, value -> "$acc$value" }

        // Initial: "" + "1" = "1", then subscribe triggers "1" + "1" = "11"
        assertThat(accumulated.value).isEqualTo("11")

        source.value = 2
        assertThat(accumulated.value).isEqualTo("112")
    }

    // ==================== runningReduce ====================

    @Test
    fun `runningReduce accumulates from initial value`() {
        val source = CowSignal(10)
        val reduced = source.runningReduce { acc, value -> acc + value }

        // runningReduce uses scan(value, accumulator)
        // Initial: accumulator(10, 10) = 20, then subscribe triggers accumulator(20, 10) = 30
        assertThat(reduced.value).isEqualTo(30)

        source.value = 5
        assertThat(reduced.value).isEqualTo(35) // 30 + 5
    }

    // ==================== pairwise ====================

    @Test
    fun `pairwise emits pairs of consecutive values`() {
        val source = CowSignal(1)
        val pairs = source.pairwise()

        assertThat(pairs.value).isEqualTo(1 to 1)

        source.value = 2
        assertThat(pairs.value).isEqualTo(1 to 2)

        source.value = 3
        assertThat(pairs.value).isEqualTo(2 to 3)
    }

    // ==================== flatten ====================

    @Test
    fun `flatten flattens nested signals`() {
        val inner1 = CowSignal(10)
        val inner2 = CowSignal(20)
        val outer = CowSignal(inner1 as com.github.fenrur.signal.Signal<Int>)
        val flattened = outer.flatten()

        assertThat(flattened.value).isEqualTo(10)

        outer.value = inner2
        assertThat(flattened.value).isEqualTo(20)
    }

    @Test
    fun `flatten updates when inner signal changes`() {
        val inner = CowSignal(10)
        val outer = CowSignal(inner as com.github.fenrur.signal.Signal<Int>)
        val flattened = outer.flatten()

        inner.value = 30
        assertThat(flattened.value).isEqualTo(30)
    }

    // ==================== flatMap / switchMap ====================

    @Test
    fun `flatMap maps and flattens`() {
        val source = CowSignal(1)
        val flattened = source.flatMap { CowSignal(it * 10) }

        assertThat(flattened.value).isEqualTo(10)

        source.value = 2
        assertThat(flattened.value).isEqualTo(20)
    }

    @Test
    fun `switchMap is alias for flatMap`() {
        val source = CowSignal(1)
        val switched = source.switchMap { CowSignal(it * 10) }

        assertThat(switched.value).isEqualTo(10)
    }

    // =============================================================================
    // FILTERING OPERATORS
    // =============================================================================

    // ==================== filter ====================

    @Test
    fun `filter keeps matching values`() {
        val source = CowSignal(10)
        val filtered = source.filter { it > 5 }

        assertThat(filtered.value).isEqualTo(10)
    }

    @Test
    fun `filter retains last matching value`() {
        val source = CowSignal(10)
        val filtered = source.filter { it > 5 }

        source.value = 3 // doesn't match
        assertThat(filtered.value).isEqualTo(10) // retains previous

        source.value = 15
        assertThat(filtered.value).isEqualTo(15)
    }

    // ==================== filterNotNull ====================

    @Test
    fun `filterNotNull filters out nulls`() {
        val source = CowSignal<Int?>(10)
        val filtered = source.filterNotNull()

        assertThat(filtered.value).isEqualTo(10)

        source.value = null
        assertThat(filtered.value).isEqualTo(10) // retains previous

        source.value = 20
        assertThat(filtered.value).isEqualTo(20)
    }

    // ==================== filterIsInstance ====================

    @Test
    fun `filterIsInstance filters by type`() {
        val source = CowSignal<Any>("hello")
        val filtered = source.filterIsInstance<String>()

        assertThat(filtered.value).isEqualTo("hello")

        source.value = 42
        assertThat(filtered.value).isEqualTo("hello") // retains previous

        source.value = "world"
        assertThat(filtered.value).isEqualTo("world")
    }

    // ==================== distinctUntilChangedBy ====================

    @Test
    fun `distinctUntilChangedBy only emits when key changes`() {
        data class Person(val id: Int, val name: String)
        val source = CowSignal(Person(1, "John"))
        val distinct = source.distinctUntilChangedBy { it.id }
        val values = CopyOnWriteArrayList<Person>()

        distinct.subscribe { it.onRight { v -> values.add(v) } }
        values.clear()

        source.value = Person(1, "Jane") // same id, different name
        source.value = Person(2, "Bob")  // different id

        assertThat(values).containsExactly(Person(2, "Bob"))
    }

    // ==================== distinctUntilChanged ====================

    @Test
    fun `distinctUntilChanged is a no-op`() {
        val source = CowSignal(10)
        val distinct = source.distinctUntilChanged()

        assertThat(distinct).isSameAs(source)
    }

    // =============================================================================
    // COMBINATION OPERATORS
    // =============================================================================

    // ==================== combine ====================

    @Test
    fun `combine2 combines two signals`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val combined = combine(a, b) { x, y -> x + y }

        assertThat(combined.value).isEqualTo(30)
    }

    @Test
    fun `combine3 combines three signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val combined = combine(a, b, c) { x, y, z -> x + y + z }

        assertThat(combined.value).isEqualTo(6)
    }

    @Test
    fun `combine4 combines four signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val combined = combine(a, b, c, d) { w, x, y, z -> w + x + y + z }

        assertThat(combined.value).isEqualTo(10)
    }

    @Test
    fun `combine5 combines five signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val e = CowSignal(5)
        val combined = combine(a, b, c, d, e) { v, w, x, y, z -> v + w + x + y + z }

        assertThat(combined.value).isEqualTo(15)
    }

    @Test
    fun `combine6 combines six signals`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val d = CowSignal(4)
        val e = CowSignal(5)
        val f = CowSignal(6)
        val combined = combine(a, b, c, d, e, f) { u, v, w, x, y, z -> u + v + w + x + y + z }

        assertThat(combined.value).isEqualTo(21)
    }

    // ==================== zip ====================

    @Test
    fun `zip combines two signals into pair`() {
        val a = CowSignal(10)
        val b = CowSignal("hello")
        val zipped = a.zip(b)

        assertThat(zipped.value).isEqualTo(10 to "hello")
    }

    @Test
    fun `zip updates when either signal changes`() {
        val a = CowSignal(10)
        val b = CowSignal("hello")
        val zipped = a.zip(b)

        a.value = 20
        assertThat(zipped.value).isEqualTo(20 to "hello")

        b.value = "world"
        assertThat(zipped.value).isEqualTo(20 to "world")
    }

    @Test
    fun `zip3 combines three signals into triple`() {
        val a = CowSignal(1)
        val b = CowSignal("two")
        val c = CowSignal(3.0)
        val zipped = a.zip(b, c)

        assertThat(zipped.value).isEqualTo(Triple(1, "two", 3.0))
    }

    // ==================== withLatestFrom ====================

    @Test
    fun `withLatestFrom combines with latest from other`() {
        val source = CowSignal(10)
        val other = CowSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }

        assertThat(combined.value).isEqualTo(110)
    }

    @Test
    fun `withLatestFrom only emits when source changes`() {
        val source = CowSignal(10)
        val other = CowSignal(100)
        val combined = source.withLatestFrom(other) { a, b -> a + b }
        val values = CopyOnWriteArrayList<Int>()

        combined.subscribe { it.onRight { v -> values.add(v) } }
        values.clear()

        other.value = 200 // should NOT trigger emission
        assertThat(values).isEmpty()

        source.value = 20 // should trigger emission with latest from other
        assertThat(values).containsExactly(220)
    }

    @Test
    fun `withLatestFrom into pair`() {
        val source = CowSignal(10)
        val other = CowSignal("hello")
        val combined = source.withLatestFrom(other)

        assertThat(combined.value).isEqualTo(10 to "hello")
    }

    // ==================== combineAll ====================

    @Test
    fun `combineAll combines multiple signals into list`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val c = CowSignal(3)
        val combined = combineAll(a, b, c)

        assertThat(combined.value).isEqualTo(listOf(1, 2, 3))
    }

    @Test
    fun `combineAll with empty returns empty list`() {
        val combined = combineAll<Int>()
        assertThat(combined.value).isEmpty()
    }

    @Test
    fun `combineAll updates when any signal changes`() {
        val a = CowSignal(1)
        val b = CowSignal(2)
        val combined = combineAll(a, b)

        a.value = 10
        assertThat(combined.value).isEqualTo(listOf(10, 2))
    }

    @Test
    fun `list combineAll extension`() {
        val signals = listOf(CowSignal(1), CowSignal(2), CowSignal(3))
        val combined = signals.combineAll()

        assertThat(combined.value).isEqualTo(listOf(1, 2, 3))
    }

    // =============================================================================
    // BOOLEAN OPERATORS
    // =============================================================================

    // ==================== not ====================

    @Test
    fun `not negates boolean signal`() {
        val source = CowSignal(true)
        val negated = source.not()

        assertThat(negated.value).isFalse()
    }

    @Test
    fun `not updates when source changes`() {
        val source = CowSignal(true)
        val negated = source.not()

        source.value = false

        assertThat(negated.value).isTrue()
    }

    // ==================== and ====================

    @Test
    fun `and combines two boolean signals`() {
        val a = CowSignal(true)
        val b = CowSignal(true)
        val result = a.and(b)

        assertThat(result.value).isTrue()

        b.value = false
        assertThat(result.value).isFalse()
    }

    // ==================== or ====================

    @Test
    fun `or combines two boolean signals`() {
        val a = CowSignal(false)
        val b = CowSignal(false)
        val result = a.or(b)

        assertThat(result.value).isFalse()

        a.value = true
        assertThat(result.value).isTrue()
    }

    // ==================== xor ====================

    @Test
    fun `xor combines two boolean signals`() {
        val a = CowSignal(true)
        val b = CowSignal(true)
        val result = a.xor(b)

        assertThat(result.value).isFalse()

        b.value = false
        assertThat(result.value).isTrue()
    }

    // ==================== allOf ====================

    @Test
    fun `allOf returns true if all signals are true`() {
        val a = CowSignal(true)
        val b = CowSignal(true)
        val c = CowSignal(true)
        val result = allOf(a, b, c)

        assertThat(result.value).isTrue()

        b.value = false
        assertThat(result.value).isFalse()
    }

    // ==================== anyOf ====================

    @Test
    fun `anyOf returns true if any signal is true`() {
        val a = CowSignal(false)
        val b = CowSignal(false)
        val c = CowSignal(false)
        val result = anyOf(a, b, c)

        assertThat(result.value).isFalse()

        b.value = true
        assertThat(result.value).isTrue()
    }

    // ==================== noneOf ====================

    @Test
    fun `noneOf returns true if no signal is true`() {
        val a = CowSignal(false)
        val b = CowSignal(false)
        val result = noneOf(a, b)

        assertThat(result.value).isTrue()

        a.value = true
        assertThat(result.value).isFalse()
    }

    // =============================================================================
    // NUMERIC OPERATORS
    // =============================================================================

    // ==================== plus ====================

    @Test
    fun `plus Int adds two signals`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val result = a + b

        assertThat(result.value).isEqualTo(30)
    }

    @Test
    fun `plus Long adds two signals`() {
        val a = CowSignal(10L)
        val b = CowSignal(20L)
        val result = a + b

        assertThat(result.value).isEqualTo(30L)
    }

    @Test
    fun `plus Double adds two signals`() {
        val a = CowSignal(10.5)
        val b = CowSignal(20.5)
        val result = a + b

        assertThat(result.value).isEqualTo(31.0)
    }

    @Test
    fun `plus Float adds two signals`() {
        val a = CowSignal(10.5f)
        val b = CowSignal(20.5f)
        val result = a + b

        assertThat(result.value).isEqualTo(31.0f)
    }

    // ==================== minus ====================

    @Test
    fun `minus Int subtracts two signals`() {
        val a = CowSignal(30)
        val b = CowSignal(10)
        val result = a - b

        assertThat(result.value).isEqualTo(20)
    }

    @Test
    fun `minus Long subtracts two signals`() {
        val a = CowSignal(30L)
        val b = CowSignal(10L)
        val result = a - b

        assertThat(result.value).isEqualTo(20L)
    }

    @Test
    fun `minus Double subtracts two signals`() {
        val a = CowSignal(30.5)
        val b = CowSignal(10.5)
        val result = a - b

        assertThat(result.value).isEqualTo(20.0)
    }

    @Test
    fun `minus Float subtracts two signals`() {
        val a = CowSignal(30.5f)
        val b = CowSignal(10.5f)
        val result = a - b

        assertThat(result.value).isEqualTo(20.0f)
    }

    // ==================== times ====================

    @Test
    fun `times Int multiplies two signals`() {
        val a = CowSignal(5)
        val b = CowSignal(4)
        val result = a * b

        assertThat(result.value).isEqualTo(20)
    }

    @Test
    fun `times Long multiplies two signals`() {
        val a = CowSignal(5L)
        val b = CowSignal(4L)
        val result = a * b

        assertThat(result.value).isEqualTo(20L)
    }

    @Test
    fun `times Double multiplies two signals`() {
        val a = CowSignal(2.5)
        val b = CowSignal(4.0)
        val result = a * b

        assertThat(result.value).isEqualTo(10.0)
    }

    @Test
    fun `times Float multiplies two signals`() {
        val a = CowSignal(2.5f)
        val b = CowSignal(4.0f)
        val result = a * b

        assertThat(result.value).isEqualTo(10.0f)
    }

    // ==================== div ====================

    @Test
    fun `div Int divides two signals`() {
        val a = CowSignal(20)
        val b = CowSignal(4)
        val result = a / b

        assertThat(result.value).isEqualTo(5)
    }

    @Test
    fun `div Long divides two signals`() {
        val a = CowSignal(20L)
        val b = CowSignal(4L)
        val result = a / b

        assertThat(result.value).isEqualTo(5L)
    }

    @Test
    fun `div Double divides two signals`() {
        val a = CowSignal(10.0)
        val b = CowSignal(4.0)
        val result = a / b

        assertThat(result.value).isEqualTo(2.5)
    }

    @Test
    fun `div Float divides two signals`() {
        val a = CowSignal(10.0f)
        val b = CowSignal(4.0f)
        val result = a / b

        assertThat(result.value).isEqualTo(2.5f)
    }

    // ==================== rem ====================

    @Test
    fun `rem Int computes remainder`() {
        val a = CowSignal(10)
        val b = CowSignal(3)
        val result = a % b

        assertThat(result.value).isEqualTo(1)
    }

    @Test
    fun `rem Long computes remainder`() {
        val a = CowSignal(10L)
        val b = CowSignal(3L)
        val result = a % b

        assertThat(result.value).isEqualTo(1L)
    }

    // ==================== coerceIn ====================

    @Test
    fun `coerceIn Int clamps value to range`() {
        val value = CowSignal(15)
        val min = CowSignal(0)
        val max = CowSignal(10)
        val result = value.coerceIn(min, max)

        assertThat(result.value).isEqualTo(10)

        value.value = 5
        assertThat(result.value).isEqualTo(5)

        value.value = -5
        assertThat(result.value).isEqualTo(0)
    }

    @Test
    fun `coerceIn Long clamps value to range`() {
        val value = CowSignal(15L)
        val min = CowSignal(0L)
        val max = CowSignal(10L)
        val result = value.coerceIn(min, max)

        assertThat(result.value).isEqualTo(10L)
    }

    @Test
    fun `coerceIn Double clamps value to range`() {
        val value = CowSignal(15.0)
        val min = CowSignal(0.0)
        val max = CowSignal(10.0)
        val result = value.coerceIn(min, max)

        assertThat(result.value).isEqualTo(10.0)
    }

    // ==================== coerceAtLeast ====================

    @Test
    fun `coerceAtLeast Int with signal`() {
        val value = CowSignal(5)
        val min = CowSignal(10)
        val result = value.coerceAtLeast(min)

        assertThat(result.value).isEqualTo(10)

        value.value = 15
        assertThat(result.value).isEqualTo(15)
    }

    @Test
    fun `coerceAtLeast Int with constant`() {
        val value = CowSignal(5)
        val result = value.coerceAtLeast(10)

        assertThat(result.value).isEqualTo(10)

        value.value = 15
        assertThat(result.value).isEqualTo(15)
    }

    @Test
    fun `coerceAtLeast Long with signal`() {
        val value = CowSignal(5L)
        val min = CowSignal(10L)
        val result = value.coerceAtLeast(min)

        assertThat(result.value).isEqualTo(10L)
    }

    @Test
    fun `coerceAtLeast Double with signal`() {
        val value = CowSignal(5.0)
        val min = CowSignal(10.0)
        val result = value.coerceAtLeast(min)

        assertThat(result.value).isEqualTo(10.0)
    }

    // ==================== coerceAtMost ====================

    @Test
    fun `coerceAtMost Int with signal`() {
        val value = CowSignal(15)
        val max = CowSignal(10)
        val result = value.coerceAtMost(max)

        assertThat(result.value).isEqualTo(10)

        value.value = 5
        assertThat(result.value).isEqualTo(5)
    }

    @Test
    fun `coerceAtMost Int with constant`() {
        val value = CowSignal(15)
        val result = value.coerceAtMost(10)

        assertThat(result.value).isEqualTo(10)
    }

    @Test
    fun `coerceAtMost Long with signal`() {
        val value = CowSignal(15L)
        val max = CowSignal(10L)
        val result = value.coerceAtMost(max)

        assertThat(result.value).isEqualTo(10L)
    }

    @Test
    fun `coerceAtMost Double with signal`() {
        val value = CowSignal(15.0)
        val max = CowSignal(10.0)
        val result = value.coerceAtMost(max)

        assertThat(result.value).isEqualTo(10.0)
    }

    // =============================================================================
    // COMPARISON OPERATORS
    // =============================================================================

    // ==================== gt ====================

    @Test
    fun `gt Int compares signals`() {
        val a = CowSignal(10)
        val b = CowSignal(5)
        val result = a gt b

        assertThat(result.value).isTrue()

        a.value = 3
        assertThat(result.value).isFalse()
    }

    @Test
    fun `gt Long compares signals`() {
        val a = CowSignal(10L)
        val b = CowSignal(5L)
        val result = a gt b

        assertThat(result.value).isTrue()
    }

    @Test
    fun `gt Double compares signals`() {
        val a = CowSignal(10.0)
        val b = CowSignal(5.0)
        val result = a gt b

        assertThat(result.value).isTrue()
    }

    // ==================== lt ====================

    @Test
    fun `lt Int compares signals`() {
        val a = CowSignal(3)
        val b = CowSignal(5)
        val result = a lt b

        assertThat(result.value).isTrue()

        a.value = 10
        assertThat(result.value).isFalse()
    }

    @Test
    fun `lt Long compares signals`() {
        val a = CowSignal(3L)
        val b = CowSignal(5L)
        val result = a lt b

        assertThat(result.value).isTrue()
    }

    @Test
    fun `lt Double compares signals`() {
        val a = CowSignal(3.0)
        val b = CowSignal(5.0)
        val result = a lt b

        assertThat(result.value).isTrue()
    }

    // ==================== eq ====================

    @Test
    fun `eq compares signals for equality`() {
        val a = CowSignal(10)
        val b = CowSignal(10)
        val result = a eq b

        assertThat(result.value).isTrue()

        b.value = 20
        assertThat(result.value).isFalse()
    }

    @Test
    fun `eq works with strings`() {
        val a = CowSignal("hello")
        val b = CowSignal("hello")
        val result = a eq b

        assertThat(result.value).isTrue()
    }

    // ==================== neq ====================

    @Test
    fun `neq compares signals for inequality`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val result = a neq b

        assertThat(result.value).isTrue()

        b.value = 10
        assertThat(result.value).isFalse()
    }

    // =============================================================================
    // STRING OPERATORS
    // =============================================================================

    // ==================== plus (String) ====================

    @Test
    fun `plus String concatenates signals`() {
        val a = CowSignal("Hello")
        val b = CowSignal(" World")
        val result = a + b

        assertThat(result.value).isEqualTo("Hello World")
    }

    // ==================== isEmpty ====================

    @Test
    fun `isEmpty String returns true for empty`() {
        val source = CowSignal("")
        val result = source.isEmpty()

        assertThat(result.value).isTrue()

        source.value = "hello"
        assertThat(result.value).isFalse()
    }

    // ==================== isNotEmpty ====================

    @Test
    fun `isNotEmpty String returns true for non-empty`() {
        val source = CowSignal("hello")
        val result = source.isNotEmpty()

        assertThat(result.value).isTrue()

        source.value = ""
        assertThat(result.value).isFalse()
    }

    // ==================== isBlank ====================

    @Test
    fun `isBlank returns true for blank strings`() {
        val source = CowSignal("   ")
        val result = source.isBlank()

        assertThat(result.value).isTrue()

        source.value = "hello"
        assertThat(result.value).isFalse()
    }

    // ==================== isNotBlank ====================

    @Test
    fun `isNotBlank returns true for non-blank strings`() {
        val source = CowSignal("hello")
        val result = source.isNotBlank()

        assertThat(result.value).isTrue()

        source.value = "   "
        assertThat(result.value).isFalse()
    }

    // ==================== length ====================

    @Test
    fun `length returns string length`() {
        val source = CowSignal("hello")
        val result = source.length()

        assertThat(result.value).isEqualTo(5)

        source.value = "hi"
        assertThat(result.value).isEqualTo(2)
    }

    // ==================== trim ====================

    @Test
    fun `trim removes whitespace`() {
        val source = CowSignal("  hello  ")
        val result = source.trim()

        assertThat(result.value).isEqualTo("hello")
    }

    // ==================== uppercase ====================

    @Test
    fun `uppercase converts to uppercase`() {
        val source = CowSignal("hello")
        val result = source.uppercase()

        assertThat(result.value).isEqualTo("HELLO")
    }

    // ==================== lowercase ====================

    @Test
    fun `lowercase converts to lowercase`() {
        val source = CowSignal("HELLO")
        val result = source.lowercase()

        assertThat(result.value).isEqualTo("hello")
    }

    // =============================================================================
    // COLLECTION OPERATORS
    // =============================================================================

    // ==================== size ====================

    @Test
    fun `size returns list size`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.size()

        assertThat(result.value).isEqualTo(3)

        source.value = listOf(1)
        assertThat(result.value).isEqualTo(1)
    }

    // ==================== isEmpty (List) ====================

    @Test
    fun `isEmpty List returns true for empty list`() {
        val source = CowSignal(emptyList<Int>())
        val result = source.isEmpty()

        assertThat(result.value).isTrue()

        source.value = listOf(1)
        assertThat(result.value).isFalse()
    }

    // ==================== isNotEmpty (List) ====================

    @Test
    fun `isNotEmpty List returns true for non-empty list`() {
        val source = CowSignal(listOf(1, 2))
        val result = source.isNotEmpty()

        assertThat(result.value).isTrue()

        source.value = emptyList()
        assertThat(result.value).isFalse()
    }

    // ==================== firstOrNull ====================

    @Test
    fun `firstOrNull returns first element`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.firstOrNull()

        assertThat(result.value).isEqualTo(1)
    }

    @Test
    fun `firstOrNull returns null for empty list`() {
        val source = CowSignal(emptyList<Int>())
        val result = source.firstOrNull()

        assertThat(result.value).isNull()
    }

    // ==================== lastOrNull ====================

    @Test
    fun `lastOrNull returns last element`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.lastOrNull()

        assertThat(result.value).isEqualTo(3)
    }

    @Test
    fun `lastOrNull returns null for empty list`() {
        val source = CowSignal(emptyList<Int>())
        val result = source.lastOrNull()

        assertThat(result.value).isNull()
    }

    // ==================== getOrNull ====================

    @Test
    fun `getOrNull returns element at index`() {
        val source = CowSignal(listOf(10, 20, 30))
        val result = source.getOrNull(1)

        assertThat(result.value).isEqualTo(20)
    }

    @Test
    fun `getOrNull returns null for out of bounds`() {
        val source = CowSignal(listOf(10, 20))
        val result = source.getOrNull(5)

        assertThat(result.value).isNull()
    }

    @Test
    fun `getOrNull with signal index`() {
        val source = CowSignal(listOf(10, 20, 30))
        val index = CowSignal(2)
        val result = source.getOrNull(index)

        assertThat(result.value).isEqualTo(30)

        index.value = 0
        assertThat(result.value).isEqualTo(10)
    }

    // ==================== contains ====================

    @Test
    fun `contains checks element presence`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.contains(2)

        assertThat(result.value).isTrue()
    }

    @Test
    fun `contains returns false for missing element`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.contains(5)

        assertThat(result.value).isFalse()
    }

    @Test
    fun `contains with signal element`() {
        val source = CowSignal(listOf(1, 2, 3))
        val element = CowSignal(2)
        val result = source.contains(element)

        assertThat(result.value).isTrue()

        element.value = 5
        assertThat(result.value).isFalse()
    }

    // ==================== filterList ====================

    @Test
    fun `filterList filters list elements`() {
        val source = CowSignal(listOf(1, 2, 3, 4, 5))
        val result = source.filterList { it > 2 }

        assertThat(result.value).isEqualTo(listOf(3, 4, 5))
    }

    // ==================== mapList ====================

    @Test
    fun `mapList transforms list elements`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.mapList { it * 2 }

        assertThat(result.value).isEqualTo(listOf(2, 4, 6))
    }

    // ==================== flatMapList ====================

    @Test
    fun `flatMapList flatMaps list elements`() {
        val source = CowSignal(listOf(1, 2))
        val result = source.flatMapList { listOf(it, it * 10) }

        assertThat(result.value).isEqualTo(listOf(1, 10, 2, 20))
    }

    // ==================== sorted ====================

    @Test
    fun `sorted sorts list ascending`() {
        val source = CowSignal(listOf(3, 1, 4, 1, 5))
        val result = source.sorted()

        assertThat(result.value).isEqualTo(listOf(1, 1, 3, 4, 5))
    }

    // ==================== sortedDescending ====================

    @Test
    fun `sortedDescending sorts list descending`() {
        val source = CowSignal(listOf(3, 1, 4, 1, 5))
        val result = source.sortedDescending()

        assertThat(result.value).isEqualTo(listOf(5, 4, 3, 1, 1))
    }

    // ==================== sortedBy ====================

    @Test
    fun `sortedBy sorts by selector`() {
        data class Person(val name: String, val age: Int)
        val source = CowSignal(listOf(
            Person("Bob", 30),
            Person("Alice", 25),
            Person("Charlie", 35)
        ))
        val result = source.sortedBy { it.age }

        assertThat(result.value.map { it.name }).isEqualTo(listOf("Alice", "Bob", "Charlie"))
    }

    // ==================== reversed ====================

    @Test
    fun `reversed reverses list`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.reversed()

        assertThat(result.value).isEqualTo(listOf(3, 2, 1))
    }

    // ==================== take ====================

    @Test
    fun `take returns first n elements`() {
        val source = CowSignal(listOf(1, 2, 3, 4, 5))
        val result = source.take(3)

        assertThat(result.value).isEqualTo(listOf(1, 2, 3))
    }

    // ==================== drop ====================

    @Test
    fun `drop removes first n elements`() {
        val source = CowSignal(listOf(1, 2, 3, 4, 5))
        val result = source.drop(2)

        assertThat(result.value).isEqualTo(listOf(3, 4, 5))
    }

    // ==================== distinct ====================

    @Test
    fun `distinct removes duplicates`() {
        val source = CowSignal(listOf(1, 2, 2, 3, 1))
        val result = source.distinct()

        assertThat(result.value).isEqualTo(listOf(1, 2, 3))
    }

    // ==================== joinToString ====================

    @Test
    fun `joinToString joins elements`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.joinToString(", ")

        assertThat(result.value).isEqualTo("1, 2, 3")
    }

    @Test
    fun `joinToString with prefix and postfix`() {
        val source = CowSignal(listOf(1, 2, 3))
        val result = source.joinToString(", ", "[", "]")

        assertThat(result.value).isEqualTo("[1, 2, 3]")
    }

    // =============================================================================
    // UTILITY OPERATORS
    // =============================================================================

    // ==================== orDefault ====================

    @Test
    fun `orDefault provides default for null`() {
        val source = CowSignal<Int?>(null)
        val result = source.orDefault(42)

        assertThat(result.value).isEqualTo(42)

        source.value = 10
        assertThat(result.value).isEqualTo(10)
    }

    @Test
    fun `orDefault with signal provides default`() {
        val source = CowSignal<Int?>(null)
        val default = CowSignal(42)
        val result = source.orDefault(default)

        assertThat(result.value).isEqualTo(42)

        source.value = 10
        assertThat(result.value).isEqualTo(10)

        source.value = null
        default.value = 100
        assertThat(result.value).isEqualTo(100)
    }

    // ==================== orElse ====================

    @Test
    fun `orElse is alias for orDefault`() {
        val source = CowSignal<Int?>(null)
        val fallback = CowSignal(42)
        val result = source.orElse(fallback)

        assertThat(result.value).isEqualTo(42)
    }

    // ==================== onEach ====================

    @Test
    fun `onEach executes side effect`() {
        val source = CowSignal(10)
        val sideEffects = mutableListOf<Int>()
        val result = source.onEach { sideEffects.add(it) }

        assertThat(result.value).isEqualTo(10)
        assertThat(sideEffects).contains(10)

        source.value = 20
        result.value // trigger
        assertThat(sideEffects).contains(20)
    }

    // ==================== tap ====================

    @Test
    fun `tap is alias for onEach`() {
        val source = CowSignal(10)
        val tapped = mutableListOf<Int>()
        val result = source.tap { tapped.add(it) }

        assertThat(result.value).isEqualTo(10)
        assertThat(tapped).contains(10)
    }

    // ==================== isPresent ====================

    @Test
    fun `isPresent returns true for non-null`() {
        val source = CowSignal<Int?>(10)
        val result = source.isPresent()

        assertThat(result.value).isTrue()

        source.value = null
        assertThat(result.value).isFalse()
    }

    // ==================== isAbsent ====================

    @Test
    fun `isAbsent returns true for null`() {
        val source = CowSignal<Int?>(null)
        val result = source.isAbsent()

        assertThat(result.value).isTrue()

        source.value = 10
        assertThat(result.value).isFalse()
    }

    // =============================================================================
    // MUTABLE SIGNAL OPERATORS
    // =============================================================================

    // ==================== toggle ====================

    @Test
    fun `toggle flips boolean`() {
        val signal = mutableSignalOf(false)

        signal.toggle()
        assertThat(signal.value).isTrue()

        signal.toggle()
        assertThat(signal.value).isFalse()
    }

    // ==================== increment ====================

    @Test
    fun `increment Int increases value`() {
        val signal = mutableSignalOf(10)

        signal.increment()
        assertThat(signal.value).isEqualTo(11)

        signal.increment(5)
        assertThat(signal.value).isEqualTo(16)
    }

    @Test
    fun `increment Long increases value`() {
        val signal = mutableSignalOf(10L)

        signal.increment()
        assertThat(signal.value).isEqualTo(11L)

        signal.increment(5L)
        assertThat(signal.value).isEqualTo(16L)
    }

    @Test
    fun `increment Double increases value`() {
        val signal = mutableSignalOf(10.0)

        signal.increment()
        assertThat(signal.value).isEqualTo(11.0)

        signal.increment(0.5)
        assertThat(signal.value).isEqualTo(11.5)
    }

    // ==================== decrement ====================

    @Test
    fun `decrement Int decreases value`() {
        val signal = mutableSignalOf(10)

        signal.decrement()
        assertThat(signal.value).isEqualTo(9)

        signal.decrement(5)
        assertThat(signal.value).isEqualTo(4)
    }

    @Test
    fun `decrement Long decreases value`() {
        val signal = mutableSignalOf(10L)

        signal.decrement()
        assertThat(signal.value).isEqualTo(9L)

        signal.decrement(5L)
        assertThat(signal.value).isEqualTo(4L)
    }

    @Test
    fun `decrement Double decreases value`() {
        val signal = mutableSignalOf(10.0)

        signal.decrement()
        assertThat(signal.value).isEqualTo(9.0)

        signal.decrement(0.5)
        assertThat(signal.value).isEqualTo(8.5)
    }

    // ==================== append ====================

    @Test
    fun `append adds suffix to string`() {
        val signal = mutableSignalOf("Hello")

        signal.append(" World")
        assertThat(signal.value).isEqualTo("Hello World")
    }

    // ==================== prepend ====================

    @Test
    fun `prepend adds prefix to string`() {
        val signal = mutableSignalOf("World")

        signal.prepend("Hello ")
        assertThat(signal.value).isEqualTo("Hello World")
    }

    // ==================== clear (String) ====================

    @Test
    fun `clear empties string`() {
        val signal = mutableSignalOf("Hello")

        signal.clear()
        assertThat(signal.value).isEmpty()
    }

    // ==================== add (List) ====================

    @Test
    fun `add appends to list`() {
        val signal = mutableSignalOf(listOf(1, 2))

        signal.add(3)
        assertThat(signal.value).isEqualTo(listOf(1, 2, 3))
    }

    // ==================== addAll ====================

    @Test
    fun `addAll appends multiple to list`() {
        val signal = mutableSignalOf(listOf(1))

        signal.addAll(listOf(2, 3, 4))
        assertThat(signal.value).isEqualTo(listOf(1, 2, 3, 4))
    }

    // ==================== remove (List) ====================

    @Test
    fun `remove removes from list`() {
        val signal = mutableSignalOf(listOf(1, 2, 3))

        signal.remove(2)
        assertThat(signal.value).isEqualTo(listOf(1, 3))
    }

    // ==================== removeAt ====================

    @Test
    fun `removeAt removes element at index`() {
        val signal = mutableSignalOf(listOf(10, 20, 30))

        signal.removeAt(1)
        assertThat(signal.value).isEqualTo(listOf(10, 30))
    }

    // ==================== clearList ====================

    @Test
    fun `clearList empties list`() {
        val signal = mutableSignalOf(listOf(1, 2, 3))

        signal.clearList()
        assertThat(signal.value).isEmpty()
    }

    // ==================== add (Set) ====================

    @Test
    fun `add appends to set`() {
        val signal = mutableSignalOf(setOf(1, 2))

        signal.add(3)
        assertThat(signal.value).isEqualTo(setOf(1, 2, 3))
    }

    // ==================== remove (Set) ====================

    @Test
    fun `remove removes from set`() {
        val signal = mutableSignalOf(setOf(1, 2, 3))

        signal.remove(2)
        assertThat(signal.value).isEqualTo(setOf(1, 3))
    }

    // ==================== clearSet ====================

    @Test
    fun `clearSet empties set`() {
        val signal = mutableSignalOf(setOf(1, 2, 3))

        signal.clearSet()
        assertThat(signal.value).isEmpty()
    }

    // ==================== put (Map) ====================

    @Test
    fun `put adds entry to map`() {
        val signal = mutableSignalOf(mapOf("a" to 1))

        signal.put("b", 2)
        assertThat(signal.value).isEqualTo(mapOf("a" to 1, "b" to 2))
    }

    // ==================== remove (Map) ====================

    @Test
    fun `remove removes key from map`() {
        val signal = mutableSignalOf(mapOf("a" to 1, "b" to 2))

        signal.remove("a")
        assertThat(signal.value).isEqualTo(mapOf("b" to 2))
    }

    // ==================== clearMap ====================

    @Test
    fun `clearMap empties map`() {
        val signal = mutableSignalOf(mapOf("a" to 1, "b" to 2))

        signal.clearMap()
        assertThat(signal.value).isEmpty()
    }

    // =============================================================================
    // CHAINING
    // =============================================================================

    @Test
    fun `operators can be chained`() {
        val source = CowSignal(10)
        val result = source
            .map { it * 2 }
            .map { it + 5 }
            .mapToString()

        assertThat(result.value).isEqualTo("25")

        source.value = 20
        assertThat(result.value).isEqualTo("45")
    }

    @Test
    fun `combine and map can be chained`() {
        val a = CowSignal(10)
        val b = CowSignal(20)
        val result = combine(a, b) { x, y -> x + y }
            .map { it * 2 }
            .mapToString()

        assertThat(result.value).isEqualTo("60")
    }

    // ==================== notifications through chain ====================

    @Test
    fun `changes propagate through chain`() {
        val source = CowSignal(10)
        val doubled = source.map { it * 2 }
        val values = CopyOnWriteArrayList<Int>()

        doubled.subscribe { it.onRight { v -> values.add(v) } }

        source.value = 20
        source.value = 30

        assertThat(values).containsExactly(20, 40, 60)
    }
}
