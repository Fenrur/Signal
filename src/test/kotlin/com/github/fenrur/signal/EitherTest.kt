package com.github.fenrur.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EitherTest {

    // ==================== Construction ====================

    @Test
    fun `Left creates left instance`() {
        val either = Either.Left("error")
        assertThat(either.isLeft).isTrue()
        assertThat(either.isRight).isFalse()
    }

    @Test
    fun `Right creates right instance`() {
        val either = Either.Right(42)
        assertThat(either.isLeft).isFalse()
        assertThat(either.isRight).isTrue()
    }

    @Test
    fun `companion left creates Left`() {
        val either = Either.left("error")
        assertThat(either).isInstanceOf(Either.Left::class.java)
    }

    @Test
    fun `companion right creates Right`() {
        val either = Either.right(42)
        assertThat(either).isInstanceOf(Either.Right::class.java)
    }

    // ==================== fold ====================

    @Test
    fun `fold on Left calls left function`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.fold(
            ifLeft = { "Left: $it" },
            ifRight = { "Right: $it" }
        )
        assertThat(result).isEqualTo("Left: error")
    }

    @Test
    fun `fold on Right calls right function`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.fold(
            ifLeft = { "Left: $it" },
            ifRight = { "Right: $it" }
        )
        assertThat(result).isEqualTo("Right: 42")
    }

    // ==================== getOrNull / leftOrNull ====================

    @Test
    fun `getOrNull on Right returns value`() {
        val either: Either<String, Int> = Either.Right(42)
        assertThat(either.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `getOrNull on Left returns null`() {
        val either: Either<String, Int> = Either.Left("error")
        assertThat(either.getOrNull()).isNull()
    }

    @Test
    fun `leftOrNull on Left returns value`() {
        val either: Either<String, Int> = Either.Left("error")
        assertThat(either.leftOrNull()).isEqualTo("error")
    }

    @Test
    fun `leftOrNull on Right returns null`() {
        val either: Either<String, Int> = Either.Right(42)
        assertThat(either.leftOrNull()).isNull()
    }

    // ==================== getOrElse / getOrDefault ====================

    @Test
    fun `getOrElse on Right returns value`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.getOrElse { -1 }
        assertThat(result).isEqualTo(42)
    }

    @Test
    fun `getOrElse on Left returns default`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.getOrElse { -1 }
        assertThat(result).isEqualTo(-1)
    }

    @Test
    fun `getOrElse receives left value`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.getOrElse { it.length }
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `getOrDefault on Right returns value`() {
        val either: Either<String, Int> = Either.Right(42)
        assertThat(either.getOrDefault(-1)).isEqualTo(42)
    }

    @Test
    fun `getOrDefault on Left returns default`() {
        val either: Either<String, Int> = Either.Left("error")
        assertThat(either.getOrDefault(-1)).isEqualTo(-1)
    }

    // ==================== map ====================

    @Test
    fun `map on Right transforms value`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.map { it * 2 }
        assertThat(result.getOrNull()).isEqualTo(84)
    }

    @Test
    fun `map on Left returns same Left`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.map { it * 2 }
        assertThat(result.isLeft).isTrue()
        assertThat(result.leftOrNull()).isEqualTo("error")
    }

    @Test
    fun `map can change type`() {
        val either: Either<String, Int> = Either.Right(42)
        val result: Either<String, String> = either.map { "Value: $it" }
        assertThat(result.getOrNull()).isEqualTo("Value: 42")
    }

    // ==================== mapLeft ====================

    @Test
    fun `mapLeft on Left transforms value`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.mapLeft { it.uppercase() }
        assertThat(result.leftOrNull()).isEqualTo("ERROR")
    }

    @Test
    fun `mapLeft on Right returns same Right`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.mapLeft { it.uppercase() }
        assertThat(result.getOrNull()).isEqualTo(42)
    }

    // ==================== flatMap ====================

    @Test
    fun `flatMap on Right with Right returns Right`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.flatMap { Either.Right(it * 2) }
        assertThat(result.getOrNull()).isEqualTo(84)
    }

    @Test
    fun `flatMap on Right with Left returns Left`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.flatMap { Either.Left("failed") }
        assertThat(result.isLeft).isTrue()
        assertThat(result.leftOrNull()).isEqualTo("failed")
    }

    @Test
    fun `flatMap on Left returns same Left`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.flatMap { Either.Right(it * 2) }
        assertThat(result.leftOrNull()).isEqualTo("error")
    }

    // ==================== onRight / onLeft ====================

    @Test
    fun `onRight on Right executes action`() {
        var executed = false
        val either: Either<String, Int> = Either.Right(42)
        either.onRight { executed = true }
        assertThat(executed).isTrue()
    }

    @Test
    fun `onRight on Left does not execute action`() {
        var executed = false
        val either: Either<String, Int> = Either.Left("error")
        either.onRight { executed = true }
        assertThat(executed).isFalse()
    }

    @Test
    fun `onLeft on Left executes action`() {
        var executed = false
        val either: Either<String, Int> = Either.Left("error")
        either.onLeft { executed = true }
        assertThat(executed).isTrue()
    }

    @Test
    fun `onLeft on Right does not execute action`() {
        var executed = false
        val either: Either<String, Int> = Either.Right(42)
        either.onLeft { executed = true }
        assertThat(executed).isFalse()
    }

    @Test
    fun `onRight returns same Either for chaining`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.onRight { }
        assertThat(result).isSameAs(either)
    }

    @Test
    fun `onLeft returns same Either for chaining`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.onLeft { }
        assertThat(result).isSameAs(either)
    }

    // ==================== swap ====================

    @Test
    fun `swap on Left returns Right`() {
        val either: Either<String, Int> = Either.Left("error")
        val swapped = either.swap()
        assertThat(swapped.isRight).isTrue()
        assertThat(swapped.getOrNull()).isEqualTo("error")
    }

    @Test
    fun `swap on Right returns Left`() {
        val either: Either<String, Int> = Either.Right(42)
        val swapped = either.swap()
        assertThat(swapped.isLeft).isTrue()
        assertThat(swapped.leftOrNull()).isEqualTo(42)
    }

    // ==================== catch ====================

    @Test
    fun `catch on success returns Right`() {
        val result = Either.catch { 42 }
        assertThat(result.isRight).isTrue()
        assertThat(result.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `catch on exception returns Left`() {
        val result = Either.catch { throw RuntimeException("test error") }
        assertThat(result.isLeft).isTrue()
        assertThat(result.leftOrNull()).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `catch captures exception message`() {
        val result = Either.catch { throw RuntimeException("test error") }
        assertThat((result.leftOrNull() as RuntimeException).message).isEqualTo("test error")
    }

    // ==================== toString ====================

    @Test
    fun `Left toString`() {
        val either = Either.Left("error")
        assertThat(either.toString()).isEqualTo("Left(error)")
    }

    @Test
    fun `Right toString`() {
        val either = Either.Right(42)
        assertThat(either.toString()).isEqualTo("Right(42)")
    }

    // ==================== equals / hashCode ====================

    @Test
    fun `Left equals`() {
        assertThat(Either.Left("a")).isEqualTo(Either.Left("a"))
        assertThat(Either.Left("a")).isNotEqualTo(Either.Left("b"))
    }

    @Test
    fun `Right equals`() {
        assertThat(Either.Right(42)).isEqualTo(Either.Right(42))
        assertThat(Either.Right(42)).isNotEqualTo(Either.Right(43))
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun `Left and Right are not equal`() {
        val left: Either<Int, Int> = Either.Left(42)
        val right: Either<Int, Int> = Either.Right(42)
        assertThat(left).isNotEqualTo(right)
    }
}
