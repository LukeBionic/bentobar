package com.meridian11.books.bentobar.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The strip's short forms and what a screen reader hears: the same time, written for the eye and said aloud in words. */
class FmtWordsTest {
    @After fun english() { Fmt.words = Fmt.Words() }

    @Test fun aLengthOfTimeIsSaidInWordsAndNeverAsTheBarWritesIt() {
        assertEquals("12 minutes", Fmt.spoken(12 * 60_000L))
        assertEquals("1 minute 5 seconds", Fmt.spoken(65_000L))
        assertEquals("1 hour 5 minutes", Fmt.spoken(65 * 60_000L))
        assertEquals("3 days 4 hours", Fmt.spoken((3 * 86_400L + 4 * 3600 + 120) * 1000))
        assertEquals("45 seconds", Fmt.spoken(45_000L))
        assertEquals("0 seconds", Fmt.spoken(0L))
        assertTrue("the compact form is still the bar's", Fmt.duration(12 * 60_000L) == "12m")
    }

    @Test fun whatIsSaidNamesAtMostTwoUnits() {
        // Hours and minutes, and the seconds left out; days and hours, with the minutes left out.
        assertEquals("2 hours 30 minutes", Fmt.spoken((2 * 3600 + 30 * 60 + 45) * 1000L))
    }

    @Test fun theWordsComeFromOneObjectSoALanguageIsATranslationAndNoCode() {
        Fmt.words = Fmt.Words(percent = { "%$it" }, minutes = { "${it}dk" }, byteUnit = { listOf("B", "ko", "Mo", "Go", "To")[it] })
        assertEquals("%85", Fmt.percent(85))
        assertEquals("%85", Fmt.percent(0.85))
        assertEquals("12dk", Fmt.duration(12 * 60_000L))
        assertEquals("1.2 Mo", Fmt.bytes(1_200_000.0))
    }

    @Test fun unitsInTheDefaultWordsAreTheOnesTheBarHasAlwaysDrawn() {
        assertEquals("85%", Fmt.percent(85))
        assertEquals("840K", Fmt.rate(840_000.0))
        assertEquals("840 KB", Fmt.bytes(840_000.0))
        assertEquals("1h 05m", Fmt.duration(65 * 60_000L))
    }
}
