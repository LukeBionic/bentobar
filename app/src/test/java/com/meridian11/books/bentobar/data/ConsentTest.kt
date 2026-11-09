package com.meridian11.books.bentobar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The answer to the accessibility disclosure belongs to one install: it lives in the no-backup directory, not the backed-up prefs. */
class ConsentTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun anAnswerSavedInTheNoBackupDirectoryIsReadBack() {
        val dir = folder.newFolder()
        File(dir, "consent").writeText("DECLINED")
        assertEquals(Store.Consent.DECLINED, Store.loadConsent(dir, legacy = null))
    }

    @Test fun nothingSavedMeansNotAsked() {
        assertEquals(Store.Consent.NOT_ASKED, Store.loadConsent(folder.newFolder(), legacy = null))
        assertEquals(Store.Consent.NOT_ASKED, Store.loadConsent(folder.newFolder(), legacy = "no such answer"))
    }

    @Test fun anAnswerFromTheBackedUpPrefsIsMovedOverOnce() {
        val dir = folder.newFolder()
        assertEquals(Store.Consent.AGREED, Store.loadConsent(dir, legacy = "AGREED"))
        assertEquals("AGREED", File(dir, "consent").readText())
        // The file is the truth from then on, whatever the prefs still say.
        assertEquals(Store.Consent.AGREED, Store.loadConsent(dir, legacy = "DECLINED"))
    }

    @Test fun aFileThatCannotBeReadCountsAsNoAnswer() {
        val dir = folder.newFolder()
        File(dir, "consent").writeText("garbage")
        assertEquals(Store.Consent.NOT_ASKED, Store.loadConsent(dir, legacy = null))
        assertFalse(File(dir, "consent").readText().isEmpty())
    }
}
