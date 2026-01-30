package com.github.fenrur.signal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import arrow.core.Either as ArrowEither

class EitherArrowTest {

    @Test
    fun `asArrow converts Left to Arrow Left`() {
        val signal: Either<String, Int> = Either.Left("error")
        val arrow: ArrowEither<String, Int> = signal.asArrow()

        assertThat(arrow.isLeft()).isTrue()
        assertThat(arrow.leftOrNull()).isEqualTo("error")
    }

    @Test
    fun `asArrow converts Right to Arrow Right`() {
        val signal: Either<String, Int> = Either.Right(42)
        val arrow: ArrowEither<String, Int> = signal.asArrow()

        assertThat(arrow.isRight()).isTrue()
        assertThat(arrow.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `asSignal converts Arrow Left to Signal Left`() {
        val arrow: ArrowEither<String, Int> = ArrowEither.Left("error")
        val signal: Either<String, Int> = arrow.asSignal()

        assertThat(signal.isLeft).isTrue()
        assertThat(signal.leftOrNull()).isEqualTo("error")
    }

    @Test
    fun `asSignal converts Arrow Right to Signal Right`() {
        val arrow: ArrowEither<String, Int> = ArrowEither.Right(42)
        val signal: Either<String, Int> = arrow.asSignal()

        assertThat(signal.isRight).isTrue()
        assertThat(signal.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `round trip Left preserves value`() {
        val original: Either<String, Int> = Either.Left("test")
        val roundTrip = original.asArrow().asSignal()

        assertThat(roundTrip).isEqualTo(original)
    }

    @Test
    fun `round trip Right preserves value`() {
        val original: Either<String, Int> = Either.Right(42)
        val roundTrip = original.asArrow().asSignal()

        assertThat(roundTrip).isEqualTo(original)
    }
}
