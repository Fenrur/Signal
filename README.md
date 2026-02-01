# Signal

[![](https://jitpack.io/v/fenrur/signal.svg)](https://jitpack.io/#fenrur/signal)

A reactive state management library for Kotlin, inspired by SolidJS signals and Kotlin StateFlow.

## Installation

### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.fenrur:signal:1.0.0")
}
```

### Gradle (Groovy)

```groovy
// build.gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.fenrur:signal:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.fenrur</groupId>
    <artifactId>signal</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Core Concepts

### Signal

A `Signal<T>` is a read-only reactive container that holds a value and notifies subscribers when it changes.

```kotlin
import com.github.fenrur.signal.*

val count: Signal<Int> = signalOf(0)

// Read the current value
println(count.value) // 0

// Subscribe to changes
val unsubscribe = count.subscribe { either ->
    either.fold(
        onLeft = { error -> println("Error: $error") },
        onRight = { value -> println("Value: $value") }
    )
}

// Use as Kotlin property delegate
val currentCount by count
println(currentCount) // 0

// Unsubscribe when done
unsubscribe()

// Close the signal when no longer needed
count.close()
```

### MutableSignal

A `MutableSignal<T>` extends `Signal<T>` with write capabilities.

```kotlin
val count = mutableSignalOf(0)

// Read and write
println(count.value) // 0
count.value = 5
println(count.value) // 5

// Atomic updates
count.update { it + 1 }
println(count.value) // 6

// Use as read-write property delegate
var delegatedCount by count
delegatedCount = 10
println(count.value) // 10
```

## Implementations

The library provides several `MutableSignal` implementations optimized for different use cases:

| Implementation | Factory | Best For |
|---------------|---------|----------|
| `CowSignal` | `mutableSignalOf()` / `cowSignalOf()` | Read-intensive scenarios (default) |
| `QueuedSignal` | `queuedSignalOf()` | Frequent subscribe/unsubscribe |
| `IndexedSignal` | `indexedSignalOf()` | O(1) listener lookup |
| `LockedSignal` | `lockedSignalOf()` | Explicit lock control, fair locking |

### Special Signals

```kotlin
// Read-only signal (immutable)
val constant: Signal<String> = signalOf("Hello")

// From Java Flow.Publisher
val flowSignal = publisher.asSignal(initialValue)
```

### BindableMutableSignal

A `BindableMutableSignal` is a signal that acts as a proxy to another signal. It allows you to switch the underlying signal at runtime.

```kotlin
// Create an unbound signal
val binded = bindableMutableSignalOf<Int>()

// Bind to a source signal
val source1 = mutableSignalOf(10)
binded.bindTo(source1)
println(binded.value) // 10

// Read and write through the binded signal
binded.value = 20
println(source1.value) // 20

// Switch to a different source
val source2 = mutableSignalOf(100)
binded.bindTo(source2)
println(binded.value) // 100

// Create with initial binding
val binded2 = bindableMutableSignalOf(mutableSignalOf(42))
println(binded2.value) // 42

// Check binding state
println(binded.isBound())        // true
println(binded.currentSignal())  // the current source signal
```

With ownership:

```kotlin
// Take ownership: closes previous signals when rebinding or closing
val binded = bindableMutableSignalOf<Int>(takeOwnership = true)

val source1 = mutableSignalOf(1)
binded.bindTo(source1)

val source2 = mutableSignalOf(2)
binded.bindTo(source2)  // source1 is automatically closed

binded.close()  // source2 is also closed
```

## Operators

Import operators:
```kotlin
import com.github.fenrur.signal.operators.*
```

### Transformation

| Operator | Description |
|----------|-------------|
| `map { }` | Transform values |
| `mapToString()` | Convert to string representation |
| `mapNotNull { }` | Map and filter nulls |
| `scan(initial) { acc, value -> }` | Accumulate values over time |
| `runningReduce { acc, value -> }` | Accumulate starting from current value |
| `pairwise()` | Emit pairs of consecutive values `(previous, current)` |
| `flatten()` | Flatten `Signal<Signal<T>>` to `Signal<T>` |
| `flatMap { }` / `switchMap { }` | Map to signal and flatten |

```kotlin
val count = mutableSignalOf(1)

// Transform values
val doubled = count.map { it * 2 }

// Accumulate
val sum = count.scan(0) { acc, value -> acc + value }

// Track changes
val changes = count.pairwise() // Pair(oldValue, newValue)
```

### Filtering

| Operator | Description |
|----------|-------------|
| `filter { }` | Filter values matching predicate |
| `filterNotNull()` | Filter out null values |
| `filterIsInstance<T>()` | Filter by type |
| `distinctUntilChangedBy { }` | Only emit when key changes |
| `distinctUntilChanged()` | No-op (signals are already distinct) |

```kotlin
val items = mutableSignalOf<Any?>(null)

val nonNull = items.filterNotNull()
val strings = items.filterIsInstance<String>()
```

### Combination

| Operator | Description |
|----------|-------------|
| `combine(a, b) { }` | Combine 2-6 signals |
| `zip(other)` | Combine into Pair |
| `zip(b, c)` | Combine into Triple |
| `withLatestFrom(other) { }` | Combine with latest from other (only emits on source change) |
| `combineAll(...)` | Combine multiple signals into List |
| `List<Signal<T>>.combineAll()` | Extension for list of signals |

```kotlin
val a = mutableSignalOf(1)
val b = mutableSignalOf(2)
val c = mutableSignalOf(3)

// Combine with transform
val sum = combine(a, b, c) { x, y, z -> x + y + z }

// Combine into tuple
val pair = a.zip(b) // Signal<Pair<Int, Int>>
val triple = a.zip(b, c) // Signal<Triple<Int, Int, Int>>

// Sample latest
val sampled = a.withLatestFrom(b) { x, y -> x + y }
```

### Boolean

| Operator | Description |
|----------|-------------|
| `not()` | Negate |
| `and(other)` | Logical AND |
| `or(other)` | Logical OR |
| `xor(other)` | Logical XOR |
| `allOf(...)` | True if all signals are true |
| `anyOf(...)` | True if any signal is true |
| `noneOf(...)` | True if no signal is true |

```kotlin
val isLoading = mutableSignalOf(true)
val hasError = mutableSignalOf(false)

val isReady = isLoading.not().and(hasError.not())
val showSpinner = allOf(isLoading, hasError.not())
```

### Numeric

| Operator | Description |
|----------|-------------|
| `+`, `-`, `*`, `/`, `%` | Arithmetic operators |
| `coerceIn(min, max)` | Clamp to range |
| `coerceAtLeast(min)` | Ensure minimum |
| `coerceAtMost(max)` | Ensure maximum |

```kotlin
val a = mutableSignalOf(10)
val b = mutableSignalOf(3)

val sum = a + b      // Signal<Int> = 13
val diff = a - b     // Signal<Int> = 7
val product = a * b  // Signal<Int> = 30
val quotient = a / b // Signal<Int> = 3
val remainder = a % b // Signal<Int> = 1

val clamped = a.coerceIn(mutableSignalOf(0), mutableSignalOf(5)) // 5
```

### Comparison

| Operator | Description |
|----------|-------------|
| `gt(other)` | Greater than |
| `lt(other)` | Less than |
| `eq(other)` | Equal |
| `neq(other)` | Not equal |

```kotlin
val age = mutableSignalOf(25)
val limit = mutableSignalOf(18)

val isAdult = age gt limit  // Signal<Boolean> = true
val isMinor = age lt limit  // Signal<Boolean> = false
```

### String

| Operator | Description |
|----------|-------------|
| `+` | Concatenate |
| `isEmpty()` | Check if empty |
| `isNotEmpty()` | Check if not empty |
| `isBlank()` | Check if blank |
| `isNotBlank()` | Check if not blank |
| `length()` | Get length |
| `trim()` | Trim whitespace |
| `uppercase()` | Convert to uppercase |
| `lowercase()` | Convert to lowercase |

```kotlin
val name = mutableSignalOf("  John  ")

val trimmed = name.trim()           // "John"
val upper = name.uppercase()        // "  JOHN  "
val length = name.length()          // 8
val valid = name.trim().isNotEmpty() // true
```

### Collection (List)

| Operator | Description |
|----------|-------------|
| `size()` | Get size |
| `isEmpty()` | Check if empty |
| `isNotEmpty()` | Check if not empty |
| `firstOrNull()` | Get first element |
| `lastOrNull()` | Get last element |
| `getOrNull(index)` | Get element at index |
| `contains(element)` | Check if contains |
| `filterList { }` | Filter elements |
| `mapList { }` | Map elements |
| `flatMapList { }` | FlatMap elements |
| `sorted()` | Sort ascending |
| `sortedDescending()` | Sort descending |
| `sortedBy { }` | Sort by selector |
| `reversed()` | Reverse order |
| `take(n)` | Take first n |
| `drop(n)` | Drop first n |
| `distinct()` | Remove duplicates |
| `joinToString()` | Join to string |

```kotlin
val items = mutableSignalOf(listOf(3, 1, 4, 1, 5))

val count = items.size()                    // 5
val first = items.firstOrNull()             // 3
val sorted = items.sorted()                 // [1, 1, 3, 4, 5]
val unique = items.distinct()               // [3, 1, 4, 5]
val doubled = items.mapList { it * 2 }      // [6, 2, 8, 2, 10]
val csv = items.joinToString(", ")          // "3, 1, 4, 1, 5"
```

### Utility

| Operator | Description |
|----------|-------------|
| `orDefault(value)` | Default for null |
| `orDefault(signal)` | Default from signal |
| `orElse(signal)` | Alias for orDefault |
| `onEach { }` | Side effect |
| `tap { }` | Alias for onEach |
| `log(prefix)` | Log values |
| `isPresent()` | True if not null |
| `isAbsent()` | True if null |

```kotlin
val name = mutableSignalOf<String?>(null)

val displayName = name.orDefault("Anonymous")
val hasName = name.isPresent()

val debugged = name.log("Name changed")
```

### MutableSignal Modifiers

| Operator | Description |
|----------|-------------|
| `toggle()` | Toggle boolean |
| `increment(by)` | Increment number |
| `decrement(by)` | Decrement number |
| `append(suffix)` | Append to string |
| `prepend(prefix)` | Prepend to string |
| `clear()` | Clear string |
| `add(element)` | Add to list/set |
| `addAll(elements)` | Add all to list |
| `remove(element)` | Remove from list/set/map |
| `removeAt(index)` | Remove at index from list |
| `clearList()` | Clear list |
| `clearSet()` | Clear set |
| `put(key, value)` | Put in map |
| `clearMap()` | Clear map |

```kotlin
val isEnabled = mutableSignalOf(false)
isEnabled.toggle() // true

val count = mutableSignalOf(0)
count.increment()    // 1
count.increment(5)   // 6
count.decrement()    // 5

val items = mutableSignalOf(listOf(1, 2, 3))
items.add(4)         // [1, 2, 3, 4]
items.remove(2)      // [1, 3, 4]
items.clearList()    // []

val cache = mutableSignalOf(mapOf<String, Int>())
cache.put("a", 1)    // {a=1}
cache.remove("a")    // {}
```

## Optional Integrations

The following integrations are optional. Add the corresponding dependency only if you need them.

### Arrow (Optional)

Convert between Signal's `Either` and Arrow's `Either`:

```kotlin
// Add Arrow as a dependency (not included transitively)
implementation("io.arrow-kt:arrow-core:2.0.1")
```

```kotlin
import com.github.fenrur.signal.arrow.*

// Convert Signal's Either to Arrow's Either
signal.subscribe { either ->
    val arrowEither = either.asArrow()
    // Use Arrow's Either methods
}

// Convert Arrow's Either to Signal's Either
val signalEither = arrowEither.asSignal()
```

### Kotlin Coroutines Flow (Optional)

Convert between Signal and Kotlin Flow:

```kotlin
// Add Coroutines as a dependency (not included transitively)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
```

```kotlin
import com.github.fenrur.signal.*

// Convert Signal to Flow
val signal = mutableSignalOf(0)
val flow: Flow<Int> = signal.asFlow()

// Collect the flow
flow.collect { value ->
    println("Value: $value")
}

// Convert StateFlow to read-only Signal (bidirectional sync)
val stateFlow: StateFlow<Int> = someStateFlow
val signal: Signal<Int> = stateFlow.asSignal(scope)

// Convert MutableStateFlow to MutableSignal (bidirectional sync)
val mutableStateFlow = MutableStateFlow(100)
val mutableSignal: MutableSignal<Int> = mutableStateFlow.asSignal(scope)

// Changes from either side are synchronized:
mutableSignal.value = 200       // Updates mutableStateFlow
mutableStateFlow.value = 300    // Notifies signal subscribers
```

### Reactive Streams (Optional)

Convert between Signal and Reactive Streams Publisher:

```kotlin
// Add Reactive Streams as a dependency (not included transitively)
implementation("org.reactivestreams:reactive-streams:1.0.4")
```

```kotlin
import com.github.fenrur.signal.*

// Convert Signal to Reactive Streams Publisher
val signal = mutableSignalOf("hello")
val publisher: Publisher<String> = signal.asReactiveStreamsPublisher()

// Convert Reactive Streams Publisher to Signal
val signal: Signal<Int> = somePublisher.asSignal(initial = 0)
```

### Java Flow (JDK 9+)

Convert Java's `java.util.concurrent.Flow.Publisher` to Signal:

```kotlin
import com.github.fenrur.signal.*

// Convert JDK Flow.Publisher to Signal
val signal = jdkPublisher.asJdkPublisher()

// With initial value
val signal = jdkPublisher.asJdkPublisher(initial = 0)
```

## Thread Safety

All signal implementations are thread-safe. Subscriptions, value reads, and value writes can be performed concurrently from multiple threads.

## License

MIT License
