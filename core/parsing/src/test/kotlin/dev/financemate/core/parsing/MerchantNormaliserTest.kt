package dev.financemate.core.parsing

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test

class MerchantNormaliserTest {

    private fun key(raw: String) = MerchantNormaliser.normalise(raw).value

    // --- Processor prefixes -----------------------------------------------------------

    @Test
    fun `strips square prefix`() {
        key("SQ *BLUE BOTTLE COFFEE") shouldBe "BLUE BOTTLE COFFEE"
    }

    @Test
    fun `strips toast and paypal prefixes`() {
        key("TST* CHIPOTLE") shouldBe "CHIPOTLE"
        key("PAYPAL *SPOTIFY") shouldBe "SPOTIFY"
    }

    @Test
    fun `strips stacked processor prefixes`() {
        key("PAYPAL *SQ *THE COFFEE SHOP") shouldBe "THE COFFEE SHOP"
    }

    // --- Transaction-type prefixes ----------------------------------------------------

    @Test
    fun `strips transaction type prefixes`() {
        key("POS DEBIT STARBUCKS") shouldBe "STARBUCKS"
        key("CHECKCARD PURCHASE TRADER JOES") shouldBe "TRADER JOES"
        key("ACH DEBIT COMCAST CABLE") shouldBe "COMCAST CABLE"
        key("RECURRING PAYMENT HULU") shouldBe "HULU"
    }

    @Test
    fun `strips wells fargo style authorisation prefix`() {
        key("PURCHASE AUTHORIZED ON 05/12 SAFEWAY") shouldBe "SAFEWAY"
    }

    // --- The core requirement: location and store noise must not split a merchant -----

    @Test
    fun `same merchant at different stores produces the same key`() {
        val a = key("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA")
        val b = key("SQ *BLUE BOTTLE COFFEE #12 SAN FRANCISCO CA")
        a shouldBe b
        a shouldBe "BLUE BOTTLE COFFEE"
    }

    @Test
    fun `store numbers and state codes are removed`() {
        key("SAFEWAY #1234 CA") shouldBe "SAFEWAY"
        key("STARBUCKS STORE 00567 WA") shouldBe "STARBUCKS"
    }

    @Test
    fun `phone numbers and dates are removed`() {
        key("NETFLIX.COM 866-579-7172 CA") shouldBe "NETFLIX"
        key("COMCAST 800-266-2278") shouldBe "COMCAST"
        key("SHELL OIL 05/12") shouldBe "SHELL OIL"
    }

    @Test
    fun `reference numbers are removed`() {
        key("AMZN Mktp US*2A3B4C5D6") shouldBe "AMAZON"
        key("ADOBE 123456789") shouldBe "ADOBE"
    }

    @Test
    fun `web addresses are reduced to the brand`() {
        key("WWW.NETFLIX.COM") shouldBe "NETFLIX"
        key("WALMART.COM") shouldBe "WALMART"
    }

    // --- Brand aliases ----------------------------------------------------------------

    @Test
    fun `known brand variants map to a canonical name`() {
        key("AMZN MKTP US") shouldBe "AMAZON"
        key("WHOLEFDS") shouldBe "WHOLE FOODS"
        key("WM SUPERCENTER") shouldBe "WALMART"
        key("SBUX") shouldBe "STARBUCKS"
    }

    // --- The dangerous direction: never merge distinct merchants ----------------------

    @Test
    fun `similar but distinct brands stay separate`() {
        // Over-merging silently corrupts data in a way the user may never notice,
        // so these are the most important assertions in this file.
        key("AMERICAN AIRLINES") shouldNotBe key("AMERICAN EXPRESS")
        key("BANK OF AMERICA") shouldNotBe key("BANK OF THE WEST")
        key("CHASE CREDIT CRD") shouldNotBe key("CHASE MORTGAGE")
        key("APPLE STORE") shouldNotBe key("APPLEBEES")
    }

    @Test
    fun `distinct services from the same vendor stay separate`() {
        // Cancelling one should not look like cancelling the other.
        key("AMAZON PRIME") shouldNotBe key("AMZN DIGITAL")
        key("UBER TRIP") shouldNotBe key("UBER EATS")
    }

    // --- Robustness -------------------------------------------------------------------

    @Test
    fun `normalisation is idempotent`() {
        // Feeding a key back in must not degrade it further, otherwise repeated
        // processing would slowly drift and break historical grouping.
        val raw = "SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA"
        val once = key(raw)
        MerchantNormaliser.normalise(once).value shouldBe once
    }

    @Test
    fun `never returns a blank key`() {
        // A descriptor that is nothing but a reference number still has to group
        // deterministically rather than vanishing.
        key("000000123456789").shouldNotBeBlank()
        key("#### ####").shouldNotBeBlank()
        key("XXXX1234").shouldNotBeBlank()
    }

    @Test
    fun `is case insensitive`() {
        key("netflix.com") shouldBe key("NETFLIX.COM")
        key("Trader Joe's") shouldBe key("TRADER JOES")
    }

    @Test
    fun `handles punctuation and extra whitespace`() {
        key("  TRADER  JOE'S   #123  ") shouldBe "TRADER JOES"
    }

    // --- Display names ----------------------------------------------------------------

    @Test
    fun `display name is human friendly`() {
        MerchantNormaliser.displayName("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA") shouldBe
            "Blue Bottle Coffee"
        MerchantNormaliser.displayName("NETFLIX.COM 866-579-7172 CA") shouldBe "Netflix"
    }

    @Test
    fun `display name keeps short acronyms upper case`() {
        MerchantNormaliser.displayName("BP FUEL") shouldBe "BP Fuel"
    }
}
