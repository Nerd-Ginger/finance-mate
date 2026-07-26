package dev.financemate.core.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    private val usd = CurrencyCode.USD
    private fun usd(minor: Long) = Money(minor, usd)

    // --- Construction and conversion -------------------------------------------------

    @Test
    fun `ofMajor converts decimal to minor units`() {
        Money.ofMajor("12.34", usd) shouldBe usd(1234)
        Money.ofMajor("0.01", usd) shouldBe usd(1)
        Money.ofMajor("-99.99", usd) shouldBe usd(-9999)
    }

    @Test
    fun `ofMajor rejects more precision than the currency has`() {
        // Silently rounding 12.345 to 12.34 would lose a user's money without
        // telling anyone. Better to refuse the input.
        shouldThrow<ArithmeticException> { Money.ofMajor("12.345", usd) }
    }

    @Test
    fun `zero-decimal currencies use a scale of zero`() {
        CurrencyCode.JPY.minorUnitScale shouldBe 0
        CurrencyCode.JPY.minorUnitsPerMajor shouldBe 1L
        Money.ofMajor("500", CurrencyCode.JPY) shouldBe Money(500, CurrencyCode.JPY)
    }

    @Test
    fun `toBigDecimal round-trips`() {
        usd(1234).toBigDecimal() shouldBe BigDecimal("12.34")
        usd(-5).toBigDecimal() shouldBe BigDecimal("-0.05")
    }

    // --- Arithmetic -------------------------------------------------------------------

    @Test
    fun `addition and subtraction are exact`() {
        // The canonical floating-point failure: 0.1 + 0.2 != 0.3.
        val result = Money.ofMajor("0.10", usd) + Money.ofMajor("0.20", usd)
        result shouldBe Money.ofMajor("0.30", usd)
    }

    @Test
    fun `mixing currencies is rejected`() {
        val error = shouldThrow<IllegalArgumentException> {
            usd(100) + Money(100, CurrencyCode.EUR)
        }
        error.message!!.contains("Convert explicitly") shouldBe true
    }

    @Test
    fun `overflow throws rather than wrapping`() {
        shouldThrow<ArithmeticException> { Money(Long.MAX_VALUE, usd) + usd(1) }
    }

    @Test
    fun `sum of empty collection is zero`() {
        emptyList<Money>().sum(usd) shouldBe Money.zero(usd)
    }

    // --- Allocation -------------------------------------------------------------------

    @Test
    fun `splitting a dollar three ways loses no cents`() {
        // 100 / 3 = 33.33... The stray cent must land somewhere, not vanish.
        val parts = usd(100).split(3)
        parts shouldBe listOf(usd(34), usd(33), usd(33))
        parts.sum(usd) shouldBe usd(100)
    }

    @Test
    fun `allocation respects weights`() {
        // A 70/30 split of $10.01.
        val parts = usd(1001).allocate(listOf(70, 30))
        parts.sum(usd) shouldBe usd(1001)
        parts shouldBe listOf(usd(701), usd(300))
    }

    @Test
    fun `allocation works for debits`() {
        val parts = usd(-100).split(3)
        parts.sum(usd) shouldBe usd(-100)
        parts shouldHaveSize 3
    }

    @Test
    fun `zero-weight parts receive nothing`() {
        val parts = usd(100).allocate(listOf(1, 0, 1))
        parts[1] shouldBe usd(0)
        parts.sum(usd) shouldBe usd(100)
    }

    @Test
    fun `allocation rejects nonsensical weights`() {
        shouldThrow<IllegalArgumentException> { usd(100).allocate(emptyList()) }
        shouldThrow<IllegalArgumentException> { usd(100).allocate(listOf(0, 0)) }
        shouldThrow<IllegalArgumentException> { usd(100).allocate(listOf(1, -1)) }
    }

    /**
     * The property that matters most: allocation is conservative. Whatever the
     * amount and whatever the weights, the parts must add back up to exactly the
     * original. If this ever fails, every budget total in the app is suspect.
     */
    @Test
    fun `allocation always conserves the total`() {
        runBlocking {
            checkAll(
                1000,
                Arb.long(-1_000_000_000L..1_000_000_000L),
                Arb.list(Arb.long(0L..1000L), 1..12),
            ) { amount, weights ->
                if (weights.sum() > 0) {
                    val money = Money(amount, usd)
                    money.allocate(weights).sum(usd) shouldBe money
                }
            }
        }
    }

    @Test
    fun `even splits always conserve the total`() {
        runBlocking {
            checkAll(500, Arb.long(-1_000_000L..1_000_000L), Arb.int(1..50)) { amount, parts ->
                val money = Money(amount, usd)
                money.split(parts).sum(usd) shouldBe money
            }
        }
    }

    @Test
    fun `split parts differ by at most one minor unit`() {
        runBlocking {
            checkAll(500, Arb.long(0L..1_000_000L), Arb.int(1..50)) { amount, parts ->
                val shares = Money(amount, usd).split(parts).map { it.minorUnits }
                (shares.max() - shares.min() <= 1L) shouldBe true
            }
        }
    }

    // --- Scaling ----------------------------------------------------------------------

    @Test
    fun `percent rounds half up`() {
        usd(1000).percent(BigDecimal("7.5")) shouldBe usd(75)
        // 1001 * 0.075 = 75.075 -> 75
        usd(1001).percent(BigDecimal("7.5")) shouldBe usd(75)
        // 1007 * 0.075 = 75.525 -> 76
        usd(1007).percent(BigDecimal("7.5")) shouldBe usd(76)
    }

    @Test
    fun `comparison orders by amount`() {
        (usd(-100) < usd(0)) shouldBe true
        (usd(500) > usd(499)) shouldBe true
        maxOf(usd(1), usd(2)) shouldBe usd(2)
    }

    @Test
    fun `abs and negation behave`() {
        usd(-250).abs() shouldBe usd(250)
        (-usd(250)) shouldBe usd(-250)
        usd(0).isZero shouldBe true
    }

    @Test
    fun `toString is plain and unambiguous`() {
        usd(-1234).toString() shouldBe "-12.34 USD"
    }

    // --- Currency code guards ---------------------------------------------------------

    @Test
    fun `currency codes must be three uppercase letters`() {
        shouldThrow<IllegalArgumentException> { CurrencyCode("usd") }
        shouldThrow<IllegalArgumentException> { CurrencyCode("US") }
        shouldThrow<IllegalArgumentException> { CurrencyCode("US1") }
    }

    @Test
    fun `unknown currency codes are rejected on scale lookup`() {
        shouldThrow<IllegalArgumentException> { CurrencyCode("ZZZ").minorUnitScale }
    }
}
