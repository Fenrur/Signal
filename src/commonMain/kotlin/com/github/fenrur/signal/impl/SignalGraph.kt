package com.github.fenrur.signal.impl

import kotlin.concurrent.atomics.*
import kotlin.jvm.JvmStatic

/**
 * Core infrastructure for glitch-free signal propagation.
 *
 * ## Architecture Overview
 *
 * This implements a push-pull model inspired by Preact Signals that guarantees
 * glitch-free reactive updates. The key insight is to separate notification
 * (push) from computation (pull).
 *
 * ## Push-Pull Model
 *
 * ### PUSH Phase (Invalidation)
 *
 * When a source signal changes:
 * 1. Mark all direct dependents as DIRTY
 * 2. Propagate MAYBE_DIRTY to indirect dependents
 * 3. Schedule effects for listener notification
 * 4. Do NOT compute new values yet
 *
 * ### PULL Phase (Validation)
 *
 * When reading a derived signal's value:
 * 1. Check if flag is CLEAN → return cached value
 * 2. If MAYBE_DIRTY → validate source versions, upgrade to DIRTY if changed
 * 3. If DIRTY → recompute value, update cache, set CLEAN
 * 4. Version tracking prevents unnecessary recomputation
 *
 * ## Signal Flags
 *
 * - **CLEAN**: Cached value is up-to-date, can be returned immediately
 * - **DIRTY**: A direct dependency has changed, must recompute
 * - **MAYBE_DIRTY**: An indirect dependency may have changed, must validate
 *
 * ## Why This Works
 *
 * Consider a diamond pattern: A → B → D and A → C → D
 *
 * When A changes:
 * 1. PUSH: B and C are marked DIRTY, D is marked MAYBE_DIRTY (via B and C)
 * 2. Effect scheduled for D's listeners
 * 3. When effect runs, D.value is read (PULL)
 * 4. D validates: checks if B or C actually changed
 * 5. D recomputes once with consistent B and C values
 * 6. D's listeners receive exactly one notification
 *
 * ## Batching
 *
 * Multiple signal updates can be grouped into a batch:
 * - Effects are queued during the batch
 * - Effects execute only when the outermost batch ends
 * - Listeners see the final consistent state, not intermediate states
 *
 * ## Thread-Safety
 *
 * - All operations use atomic primitives (AtomicInt, ConcurrentQueue)
 * - No blocking synchronization avoids deadlocks
 * - Effects are executed serially to prevent reentrancy issues
 */
object SignalGraph {

    /**
     * Global version counter. Incremented whenever any MutableSignal changes.
     * Used for quick staleness detection without traversing the dependency graph.
     */
    @JvmStatic
    val globalVersion = AtomicLong(0L)

    /**
     * Current batch depth. When > 0, effects are queued instead of executed immediately.
     */
    @JvmStatic
    private val batchDepth = AtomicInt(0)

    /**
     * Queue of effects pending execution at the end of the current batch.
     */
    @JvmStatic
    private val pendingEffects = ConcurrentQueue<EffectNode>()

    /**
     * Whether we're currently flushing effects. Prevents recursive flush.
     */
    @JvmStatic
    private val isFlushing = AtomicBoolean(false)

    /**
     * Executes a block within a batch context.
     *
     * Multiple signal mutations within the batch are grouped together.
     * Effects are only executed once at the end of the outermost batch,
     * seeing the final consistent state.
     *
     * Example:
     * ```kotlin
     * val a = mutableSignalOf(1)
     * val b = mutableSignalOf(10)
     * val c = combine(a, b) { x, y -> x + y }
     * val emissions = mutableListOf<Int>()
     * c.subscribe { emissions.add(it) }
     *
     * batch {
     *     a.value = 2
     *     b.value = 20
     * }
     * // emissions contains only [22], not [12, 22]
     * ```
     *
     * @param block the code to execute within the batch
     * @return the result of the block
     */
    @JvmStatic
    fun <T> batch(block: () -> T): T {
        startBatch()
        try {
            return block()
        } finally {
            endBatch()
        }
    }

    /**
     * Starts a new batch level. Can be nested.
     */
    @JvmStatic
    fun startBatch() {
        batchDepth.incrementAndFetch()
    }

    /**
     * Ends the current batch level. When reaching 0, flushes all pending effects.
     */
    @JvmStatic
    fun endBatch() {
        if (batchDepth.decrementAndFetch() == 0) {
            flushEffects()
        }
    }

    /**
     * Returns true if we're currently inside a batch.
     */
    @JvmStatic
    fun isInBatch(): Boolean = batchDepth.load() > 0

    /**
     * Schedules an effect to run at the end of the current batch.
     * If not in a batch, the effect runs immediately.
     */
    @JvmStatic
    fun scheduleEffect(effect: EffectNode) {
        if (isInBatch()) {
            // Only add if not already pending
            if (effect.markPending()) {
                pendingEffects.offer(effect)
            }
        } else {
            // Execute immediately if not in batch
            effect.execute()
        }
    }

    /**
     * Flushes all pending effects after a batch ends.
     */
    @JvmStatic
    private fun flushEffects() {
        if (!isFlushing.compareAndSet(false, true)) return
        try {
            while (true) {
                val effect = pendingEffects.poll() ?: break
                effect.execute()
            }
        } finally {
            isFlushing.store(false)
        }
        // Re-check: effects may have been added between the last poll() and store(false)
        if (pendingEffects.isNotEmpty()) {
            flushEffects()
        }
    }

    /**
     * Increments the global version and returns the new value.
     */
    @JvmStatic
    fun incrementGlobalVersion(): Long = globalVersion.incrementAndFetch()
}

/**
 * Flags representing the state of a computed signal's cached value.
 */
enum class SignalFlag {
    /**
     * The cached value is up-to-date. Can be returned immediately.
     */
    CLEAN,

    /**
     * A direct dependency has changed. Must recompute.
     */
    DIRTY,

    /**
     * An indirect dependency may have changed.
     * Need to validate dependencies before deciding whether to recompute.
     */
    MAYBE_DIRTY
}

/**
 * Interface for signals that can be marked dirty in the push phase.
 */
interface DirtyMarkable {
    fun markDirty()
    fun markMaybeDirty()
}

/**
 * Interface for effect nodes that can be scheduled for execution.
 */
interface EffectNode {
    fun markPending(): Boolean
    fun execute()
}

/**
 * Base interface for computed signals that participate in the dependency graph.
 */
interface ComputedSignalNode : DirtyMarkable {
    val version: Long
    fun validateAndGet(): Any?
    fun addTarget(target: DirtyMarkable)
    fun removeTarget(target: DirtyMarkable)
}

/**
 * Interface for source signals (MutableSignal) that can notify targets.
 */
interface SourceSignalNode {
    val version: Long
    fun addTarget(target: DirtyMarkable)
    fun removeTarget(target: DirtyMarkable)
}

/**
 * Top-level batch function for convenient access.
 */
fun <T> batch(block: () -> T): T = SignalGraph.batch(block)
