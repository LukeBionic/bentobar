package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.data.ItemConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.meridian11.books.bentobar.net.FakeHttp
import com.meridian11.books.bentobar.net.Host
import java.util.Locale

class FxRulesTest {
    private val german = Locale.GERMANY
    private val french = Locale.FRANCE
    private val indian = Locale("en", "IN")

    private val seriesJson = """
        {
          "amount": 1.0,
          "base": "EUR",
          "start_date": "2026-04-10",
          "end_date": "2026-04-15",
          "rates": {
            "2026-04-14": {
              "USD": 1.0850,
              "GBP": 0.8540,
              "JPY": 162.10
            },
            "2026-04-15": {
              "USD": 1.0920,
              "GBP": 0.8520,
              "JPY": 163.40
            }
          }
        }
    """.trimIndent()

    @Test fun parsesTimeSeriesAndComputesCrossRatesAndDailyChange() {
        val table = FxRules.read(seriesJson, now = 50_000L)
        assertNotNull(table)
        table!!
        assertEquals("2026-04-15", table.date)
        assertTrue(table.hasRates)

        val eurUsd = FxRules.quoteFor(FxRules.FxPair("EUR", "USD"), table, Locale.US)!!
        assertEquals(1.0920, eurUsd.rate, 0.0001)
        assertTrue(eurUsd.changePct > 0.6)
        assertEquals("1.0920", eurUsd.formattedRate)

        val usdJpy = FxRules.quoteFor(FxRules.FxPair("USD", "JPY"), table)!!
        assertEquals(163.40 / 1.0920, usdJpy.rate, 0.01)
    }

    @Test fun rejectsMalformedJson() {
        assertNull(FxRules.read("not json", 10_000L))
        assertNull(FxRules.read("""{"base":"EUR","rates":{}}""", 10_000L))
    }

    @Test fun parsesAndManagesFollowedCurrencyPairs() {
        assertEquals(FxRules.FxPair("EUR", "USD"), FxRules.parsePair(" eur / usd "))
        assertEquals(FxRules.FxPair("GBP", "JPY"), FxRules.parsePair("gbp-jpy"))
        assertNull(FxRules.parsePair("USD/USD"))
        assertNull(FxRules.parsePair("ABC/USD"))

        var item = ItemConfig("f1", "fx", options = mapOf("pairs" to "EUR/USD"))
        item = FxRules.addPair(item, FxRules.FxPair("USD", "JPY"))
        assertEquals(listOf(FxRules.FxPair("EUR", "USD"), FxRules.FxPair("USD", "JPY")), FxRules.pairs(item))

        item = FxRules.movePair(item, FxRules.FxPair("USD", "JPY"), -1)
        assertEquals(listOf(FxRules.FxPair("USD", "JPY"), FxRules.FxPair("EUR", "USD")), FxRules.pairs(item))

        item = FxRules.removePair(item, FxRules.FxPair("EUR", "USD"))
        assertEquals(listOf(FxRules.FxPair("USD", "JPY")), FxRules.pairs(item))
    }

    @Test fun formatsConvertedAmountsCleanly() {
        assertEquals("109.20", FxRules.formatConverted(100.0, 1.092, Locale.US))
        assertEquals("16,340.00", FxRules.formatConverted(100.0, 163.4, Locale.US))
        assertEquals(250.0, FxRules.parseAmount(" 250 ", Locale.US)!!, 0.001)
        assertNull(FxRules.parseAmount("-5", Locale.US))
    }

    @Test fun figuresAreWrittenInTheDevicesOwnWay() {
        assertEquals("109,20", FxRules.formatConverted(100.0, 1.092, german))
        assertEquals("16.340,00", FxRules.formatConverted(100.0, 163.4, german))
        assertEquals("1,0920", FxRules.formatRate(1.092, german))
        assertEquals("1.0920", FxRules.formatRate(1.092, Locale.US))
        assertEquals("0,65", FxRules.formatPct(-0.65, german))
        assertEquals("100,5", FxRules.formatAmount(100.5, german))
        assertEquals("100", FxRules.formatAmount(100.0, german))
        assertEquals("100.5", FxRules.formatAmount(100.5, Locale.US))
    }

    @Test fun aDecimalCommaIsADecimalComma() {
        assertEquals(100.5, FxRules.parseAmount("100,50", german)!!, 0.001)
        assertEquals(1000.5, FxRules.parseAmount("1.000,50", german)!!, 0.001)
    }

    @Test fun whereTheCommaGroupsThousandsItOnlyDoesThat() {
        assertEquals(1000.5, FxRules.parseAmount("1,000.50", Locale.US)!!, 0.001)
        assertEquals(1000.0, FxRules.parseAmount("1,000", Locale.US)!!, 0.001)
        // A comma that can't be grouping is no number here: better no figure than one a hundred times too large.
        assertNull(FxRules.parseAmount("100,50", Locale.US))
        assertNull(FxRules.parseAmount("1,0000", Locale.US))
        assertEquals(100000.5, FxRules.parseAmount("1,00,000.50", indian)!!, 0.001)
    }

    @Test fun whereTheDotGroupsThousandsItIsNoDecimalPoint() {
        assertNull(FxRules.parseAmount("100.50", german))
        assertEquals(1000.0, FxRules.parseAmount("1.000", german)!!, 0.001)
    }

    @Test fun aSpaceGroupsThousandsWhereTheLocaleDoesSo() {
        for (space in listOf(" ", "\u00A0", "\u202F")) assertEquals(space, 1000.5, FxRules.parseAmount("1${space}000,50", french)!!, 0.001)
        assertNull(FxRules.parseAmount("1 000.50", Locale.US))
    }

    @Test fun textThatIsNotWhollyANumberIsNotAnAmount() {
        for (text in listOf("", " ", "abc", "12abc", "1e5", "0x10", "NaN", "Infinity", ".", ",", "1,2,3", "0", "1000000001"))
            assertNull(text, FxRules.parseAmount(text, Locale.US))
        // Halfway through typing, a figure stays.
        assertEquals(100.0, FxRules.parseAmount("100.", Locale.US)!!, 0.001)
        assertEquals(0.5, FxRules.parseAmount(".5", Locale.US)!!, 0.001)
    }

    private val now = 1_776_000_000_000L

    @Test fun theRatesRequestIsOneTheTransportWillSend() {
        // No install could load a rate in 1.6 to 1.7.1: the transport refused the path and only a real device could tell.
        val request = FxLoad.request(now)
        assertEquals("/v1/2026-04-02..", request.path)
        assertNotNull(com.meridian11.books.bentobar.net.HttpTransport.address(request))
    }

    @Test fun ratesLoadThroughTheTransportsOwnRule() = FakeHttp.use { net ->
        net.reply(Host.FRANKFURTER, FxLoad.request(now).path, seriesJson)
        val table = FxLoad.load(null, now)
        assertEquals("EUR", net.query("base"))
        assertTrue(table!!.hasRates)
        assertNull(table.failure)
    }
}
