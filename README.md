# Signal

[![Maven Central](https://img.shields.io/maven-central/v/io.github.fenrur/signal)](https://central.sonatype.com/artifact/io.github.fenrur/signal)
[![Build](https://github.com/Fenrur/Signal/actions/workflows/ci.yml/badge.svg)](https://github.com/Fenrur/Signal/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A lightweight, glitch-free reactive signal library for Kotlin Multiplatform, inspired by [SolidJS signals](https://www.solidjs.com/docs/latest/api#createsignal) and Kotlin StateFlow.

### Platforms

| JVM | JS | WasmJS | WasmWASI | Linux x64 | macOS x64 | macOS ARM64 | Windows x64 |
|:---:|:--:|:------:|:--------:|:---------:|:---------:|:-----------:|:-----------:|
| :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |

### Quick Example

```kotlin
import io.github.fenrur.signal.*
import io.github.fenrur.signal.operators.*

val count = mutableSignalOf(0)
val doubled = count.map { it * 2 }

doubled.subscribe { it.onSuccess { v -> println("doubled = $v") } }
// prints: doubled = 0

count.value = 5
// prints: doubled = 10
```

## Features

- **Glitch-free propagation** -- derived signals never observe inconsistent intermediate values, even in diamond dependency graphs
- **Thread-safe** -- all signal implementations are safe for concurrent reads, writes, and subscriptions
- **Rich operator library** -- 60+ built-in operators: map, filter, combine, flatMap, scan, boolean, numeric, string, collection, and more
- **Batching** -- group multiple signal updates together to defer notifications and avoid intermediate states
- **Bindable signals** -- dynamically switch the source signal at runtime with automatic circular binding detection
- **Property delegates** -- use signals as Kotlin `by` delegates for seamless integration
- **Coroutines Flow interop** -- bidirectional conversion between Signal and `StateFlow` / `MutableStateFlow`
- **Reactive Streams interop** -- convert to/from `org.reactivestreams.Publisher` (JVM)
- **Zero mandatory dependencies** -- only `kotlinx-coroutines-core` for Flow/StateFlow interop

## Table of Contents

- [Installation](#installation)
- [Core Concepts](#core-concepts)
  - [Signal](#signal)
  - [MutableSignal](#mutablesignal)
  - [BindableSignal](#bindablesignal)
  - [BindableMutableSignal](#bindablemutablesignal)
- [Operators](#operators)
  - [Transformation](#transformation) | [Filtering](#filtering) | [Combination](#combination)
  - [Boolean](#boolean) | [Numeric](#numeric) | [Comparison](#comparison)
  - [String](#string) | [Collection](#collection-list) | [Utility](#utility)
  - [MutableSignal Modifiers](#mutablesignal-modifiers)
- [Batching](#batching)
- [Glitch-Free Semantics](#glitch-free-semantics)
- [Integrations](#integrations)
  - [Coroutines Flow](#kotlin-coroutines-flow) | [Reactive Streams](#reactive-streams-jvm-only) | [Java Flow](#java-flow-jdk-9-jvm-only)
- [Thread Safety](#thread-safety)
- [License](#license)

## Installation

### Kotlin Multiplatform

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.fenrur:signal:3.0.0")
            }
        }
    }
}
```

### JVM / Android only

```kotlin
dependencies {
    implementation("io.github.fenrur:signal-jvm:3.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.fenrur</groupId>
    <artifactId>signal-jvm</artifactId>
    <version>3.0.0</version>
</dependency>
```

## Core Concepts

### Signal

A `Signal<T>` is a read-only reactive container that holds a value and notifies subscribers when it changes.

```kotlin
import io.github.fenrur.signal.*

val count: Signal<Int> = signalOf(0)

// Read the current value
println(count.value) // 0

// Subscribe to changes
val unsubscribe = count.subscribe { result ->
    result.fold(
        onSuccess = { value -> println("Value: $value") },
        onFailure = { error -> println("Error: $error") }
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

### BindableSignal

A `BindableSignal<T>` is a read-only signal that acts as a proxy to another `Signal`. It allows you to dynamically switch the underlying signal at runtime while maintaining subscriptions.

```kotlin
val bindable = bindableSignalOf<Int>()

// Bind to a source signal
val source1 = signalOf(10)
bindable.bindTo(source1)
println(bindable.value) // 10

// Switch to a different source -- subscribers are automatically notified
val source2 = mutableSignalOf(100)
bindable.bindTo(source2)
println(bindable.value) // 100

source2.value = 200
println(bindable.value) // 200
```

<details>
<summary><strong>Factory Functions</strong></summary>

```kotlin
// Create unbound (throws on value access until bound)
val unbound = bindableSignalOf<String>()

// Create with initial signal binding
val withSignal = bindableSignalOf(signalOf(42))
println(withSignal.value) // 42

// Create with initial value (creates internal signal automatically)
val withValue = bindableSignalOf(initialValue = "Hello")
println(withValue.value) // "Hello"

// Check binding state
println(withSignal.isBound())        // true
println(withSignal.currentSignal())  // returns the bound Signal
```

</details>

<details>
<summary><strong>Ownership Mode</strong></summary>

When `takeOwnership = true`, the bindable signal takes ownership of bound signals and closes them automatically:

```kotlin
val bindable = bindableSignalOf<Int>(takeOwnership = true)

val source1 = signalOf(1)
bindable.bindTo(source1)

val source2 = signalOf(2)
bindable.bindTo(source2)
println(source1.isClosed) // true - automatically closed on rebind

bindable.close()
println(source2.isClosed) // true - closed when bindable closes
```

</details>

<details>
<summary><strong>Circular Binding Detection</strong></summary>

The library automatically detects and prevents circular bindings that would cause infinite loops:

```kotlin
val a = bindableSignalOf(1)
val b = bindableSignalOf<Int>()
val c = bindableSignalOf<Int>()

b.bindTo(a)
c.bindTo(b)

// This would create a cycle: a -> b -> c -> a
a.bindTo(c) // Throws IllegalStateException: "Circular binding detected"

// You can check for cycles before binding
if (!BindableSignal.wouldCreateCycle(a, c)) {
    a.bindTo(c)
}
```

</details>

### BindableMutableSignal

A `BindableMutableSignal<T>` acts as a proxy to another `MutableSignal`. Read and write operations are forwarded to the bound source.

```kotlin
val bindable = bindableMutableSignalOf<Int>()

val source = mutableSignalOf(10)
bindable.bindTo(source)
println(bindable.value) // 10

// Write through the bindable signal
bindable.value = 20
println(source.value) // 20 - changes propagate to source
```

<details>
<summary><strong>Factory Functions, Ownership Mode & Circular Binding Detection</strong></summary>

Works the same as `BindableSignal`, but for `MutableSignal` sources:

```kotlin
// Create with initial value
val withValue = bindableMutableSignalOf(initialValue = "Hello")

// Create with initial mutable signal binding
val withSignal = bindableMutableSignalOf(mutableSignalOf(42))

// Ownership mode
val owned = bindableMutableSignalOf<Int>(takeOwnership = true)

// Circular detection
val a = bindableMutableSignalOf(1)
val b = bindableMutableSignalOf(2)
a.bindTo(b)
b.bindTo(a) // Throws IllegalStateException: "Circular binding detected"
```

</details>

## Operators

```kotlin
import io.github.fenrur.signal.operators.*
```

### Transformation

| Operator | Description |
|----------|-------------|
| `map { }` | Transform values |
| `bimap(forward, reverse)` | Bidirectional transform for `MutableSignal` |
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

// Bidirectional transform (read and write through a lens)
val stringSignal = mutableSignalOf("42")
val intSignal = stringSignal.bimap(
    forward = { it.toInt() },
    reverse = { it.toString() }
)
println(intSignal.value)  // 42
intSignal.value = 100
println(stringSignal.value) // "100"

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
| `isEmpty()` / `isNotEmpty()` | Check empty |
| `isBlank()` / `isNotBlank()` | Check blank |
| `length()` | Get length |
| `trim()` | Trim whitespace |
| `uppercase()` / `lowercase()` | Case conversion |

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
| `size()` / `isEmpty()` / `isNotEmpty()` | Size checks |
| `firstOrNull()` / `lastOrNull()` / `getOrNull(index)` | Element access |
| `contains(element)` | Membership check |
| `filterList { }` / `mapList { }` / `flatMapList { }` | Transform elements |
| `sorted()` / `sortedDescending()` / `sortedBy { }` | Sorting |
| `reversed()` / `take(n)` / `drop(n)` / `distinct()` | Slicing |
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
| `orDefault(value)` / `orDefault(signal)` | Default for null |
| `onEach { }` / `tap { }` | Side effect |
| `log(prefix)` | Log values |
| `isPresent()` / `isAbsent()` | Null checks |

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
| `increment(by)` / `decrement(by)` | Increment/decrement number |
| `append(suffix)` / `prepend(prefix)` / `clear()` | String mutations |
| `add(element)` / `addAll(elements)` / `remove(element)` / `removeAt(index)` / `clearList()` | List mutations |
| `clearSet()` | Clear set |
| `put(key, value)` / `remove(key)` / `clearMap()` | Map mutations |

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

## Batching

Group multiple signal updates together to defer notifications until the batch completes. This prevents intermediate states and reduces unnecessary recomputations.

```kotlin
val firstName = mutableSignalOf("John")
val lastName = mutableSignalOf("Doe")
val fullName = combine(firstName, lastName) { first, last -> "$first $last" }

val emissions = mutableListOf<String>()
fullName.subscribe { result ->
    result.onSuccess { emissions.add(it) }
}
// emissions = ["John Doe"]

// Without batch: each update triggers a notification
firstName.value = "Jane"  // emissions = ["John Doe", "Jane Doe"]
lastName.value = "Smith"  // emissions = ["John Doe", "Jane Doe", "Jane Smith"]

// With batch: only one notification at the end
batch {
    firstName.value = "Bob"
    lastName.value = "Wilson"
}
// emissions = ["John Doe", "Jane Doe", "Jane Smith", "Bob Wilson"]
// Note: "Bob Doe" was never emitted!
```

<details>
<summary><strong>Nested Batches & Return Values</strong></summary>

Batches can be nested. Only the outermost batch triggers notifications:

```kotlin
batch {
    firstName.value = "Alice"
    batch {
        lastName.value = "Johnson"
        // No notification yet
    }
    // Still no notification
}
// Now subscribers are notified with "Alice Johnson"
```

`batch { }` returns the result of the block:

```kotlin
val result = batch {
    firstName.value = "New"
    lastName.value = "Name"
    "Operation completed"
}
println(result) // "Operation completed"
```

</details>

**Use cases:** form submissions, state resets, complex calculations, performance optimization of derived signals.

## Glitch-Free Semantics

This library implements a push-pull model that guarantees **glitch-free** behavior:

- **No intermediate states** -- derived signals never observe inconsistent intermediate values
- **Diamond pattern safety** -- in dependency graphs like `c = combine(a.map{}, b.map{})`, derived signals receive exactly one notification per source update
- **Consistent snapshots** -- subscribers always see a consistent view of the signal graph

```kotlin
val source = mutableSignalOf(1)
val doubled = source.map { it * 2 }
val tripled = source.map { it * 3 }
val sum = combine(doubled, tripled) { d, t -> d + t }

val emissions = mutableListOf<Int>()
sum.subscribe { it.onSuccess { v -> emissions.add(v) } }
// emissions = [5] (2 + 3)

source.value = 2
// emissions = [5, 10] (4 + 6)
// Note: [5, 7] or [5, 8] never appears (no glitch)
```

## Integrations

### Kotlin Coroutines Flow

Convert between Signal and Kotlin Flow (all platforms):

```kotlin
// Add Coroutines as a dependency (not included transitively)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
```

```kotlin
// Signal -> Flow
val signal = mutableSignalOf(0)
val flow: Flow<Int> = signal.asFlow()

// StateFlow -> Signal (bidirectional sync)
val stateFlow: StateFlow<Int> = someStateFlow
val signal: Signal<Int> = stateFlow.asSignal(scope)

// MutableStateFlow -> MutableSignal (bidirectional sync)
val mutableStateFlow = MutableStateFlow(100)
val mutableSignal: MutableSignal<Int> = mutableStateFlow.asSignal(scope)

// Changes from either side are synchronized:
mutableSignal.value = 200       // Updates mutableStateFlow
mutableStateFlow.value = 300    // Notifies signal subscribers
```

### Reactive Streams (JVM only)

```kotlin
// Add Reactive Streams as a dependency (not included transitively)
implementation("org.reactivestreams:reactive-streams:1.0.4")
```

```kotlin
// Signal -> Publisher
val publisher: Publisher<String> = signal.asReactiveStreamsPublisher()

// Publisher -> Signal
val signal: Signal<Int> = somePublisher.asSignal(initial = 0)
```

### Java Flow (JDK 9+, JVM only)

```kotlin
// JDK Flow.Publisher -> Signal
val signal = jdkPublisher.asJdkPublisher(initial = 0)
```

## Thread Safety

All signal implementations are thread-safe. Subscriptions, value reads, and value writes can be performed concurrently from multiple threads without additional synchronization.

## Contributing

Contributions are welcome! Please feel free to submit a [Pull Request](https://github.com/Fenrur/Signal/pulls).

## License

```
MIT License

Copyright (c) 2026 Livio TINNIRELLO
```

See [LICENSE](LICENSE) for full details.
