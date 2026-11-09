package com.meridian11.books.bentobar

import com.meridian11.books.bentobar.SourceScan.code
import com.meridian11.books.bentobar.SourceScan.place
import com.meridian11.books.bentobar.SourceScan.sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The disclosure says BentoBar reads where windows are, never what is in them. What only reading the code can show is
 * that nothing else does: the accessibility API is named in two files, and the one title read is the one the
 * disclosure's reading explains. A change that reads another app's node or window text fails here, in the build.
 */
class WindowReadingTest {
    private val scanFiles = setOf("bar/BarService.kt", "bar/StatusBarScan.kt")

    @Test fun theAccessibilityNodeAndWindowApisAreNamedOnlyInBarServiceAndStatusBarScan() {
        // The service, its windows and their node trees. Nothing that holds a node or a window leaves bar/'s two scanning files.
        val apis = listOf(Regex("""\bAccessibilityService\b(?!\.GLOBAL_ACTION_)"""), Regex("""\bAccessibilityNodeInfo\b"""), Regex("""\bAccessibilityWindowInfo\b"""),
            Regex("""\bAccessibilityEvent\b"""), Regex("""\bfindFocus\b"""), Regex("""\brootInActiveWindow\b"""), Regex("""\bperformAction\b"""),
            Regex("""\bfindAccessibilityNodeInfo\w*"""), Regex("""\bgetRoot\b"""), Regex("""\.windows\b"""), Regex("""\btakeScreenshot\w*"""),
            Regex("""\bgetWindows\b"""), Regex("""\bgetChild\b"""), Regex("""\bAccessibilityNodeInfoCompat\b"""))
        for (f in sources) {
            val place = f.place()
            if (place in scanFiles) continue
            // The constants of the global actions (Back, Home, Recents...) are names, not access; so is the import that brings them.
            val text = code(f).replace(Regex("(?m)^import android.accessibilityservice.AccessibilityService$"), "")
            for (api in apis) assertTrue("$place uses ${api.pattern}: the accessibility API stays in ${scanFiles.joinToString()}", !api.containsMatchIn(text))
        }
    }

    @Test fun theGlobalActionsAreOneCallInBarService() {
        // Tools and Shortcut press Back, Home, Recents and the like through Env.global, which asks BarService.global.
        val users = sources.filter { Regex("""\bperformGlobalAction\b""").containsMatchIn(code(it)) }.map { it.place() }
        assertEquals(listOf("bar/BarService.kt"), users)
    }

    @Test fun itemsNeverHoldTheService() {
        // Items get Env.global and Env.running, not the service: nothing outside bar/ names its type or the connected one.
        for (f in sources) {
            val place = f.place()
            if (place.startsWith("bar/")) continue
            val text = code(f)
            assertTrue("$place names the service: items ask Env.running or Env.global", !Regex("""\bBarService\.current\b""").containsMatchIn(text) ||
                place in setOf("items/Env.kt", "ui/BarMenuActivity.kt"))
        }
    }

    @Test fun aWindowsTitleIsReadInOnePlaceAndOnlyForASystemWindow() {
        // `check` in BarService tells the screenshot preview from a shade by the title of a large TYPE_SYSTEM window, and
        // nothing else reads a title. The title is compared, never logged or kept. AGENTS.md names this check.
        val holders = sources.filter { "AccessibilityWindowInfo" in code(it) }
        assertEquals(scanFiles, holders.map { it.place() }.toSet())
        val titles = holders.flatMap { f -> code(f).lines().withIndex().filter { Regex("""\.title\b""").containsMatchIn(it.value) }.map { f.place() to it.value.trim() } }
            // The item type's own title (the tooltip's text) is not a window's.
            .filterNot { (_, line) -> "Items.of(" in line }
        assertEquals("the lines that read a window's title: $titles", 1, titles.size)
        val (place, line) = titles.single()
        assertEquals("bar/BarService.kt", place)
        assertTrue(line, "Screenshot" in line)
        // The same condition, a few lines up, is about TYPE_SYSTEM windows only.
        val lines = code(File("src/main/java/com/meridian11/books/bentobar/bar/BarService.kt")).lines()
        val at = lines.indexOfFirst { it.trim() == line }
        assertTrue("the title check is for a TYPE_SYSTEM window only", lines.subList(maxOf(0, at - 3), at + 1).any { "TYPE_SYSTEM" in it })
        // Not logged: no Log call names a title.
        for (f in sources) for (l in code(f).lines()) assertTrue("${f.place()} logs a title: $l", !(Regex("""\bLog\.""").containsMatchIn(l) && Regex("""\b(it|w|cover|window)\.title\b""").containsMatchIn(l)))
    }

    @Test fun theWindowListingHookIsInTheDebugBuildOnly() {
        // `./bento debug windows` lists every window's title. It is in src/debug, which no release build has.
        for (f in sources) assertTrue("${f.place()} has the windows hook: it belongs in src/debug", "\"windows\" ->" !in code(f))
        assertTrue(File("src/debug/java/com/meridian11/books/bentobar/util/DebugReceiver.kt").readText().contains("\"windows\" ->"))
    }

    @Test fun anIntentCannotNameAPermissionForTheSettingsWindowToAsk() {
        // MainActivity is exported, so any app can start it. A permission prompt is asked for in-process, never read from an extra.
        val main = code(File("src/main/java/com/meridian11/books/bentobar/ui/MainActivity.kt"))
        assertTrue("MainActivity reads a permission name from an intent", !Regex("""requestPermissions\([^)]*getStringExtra|getStringExtra\([^)]*(REQUEST|request)""").containsMatchIn(main))
        assertTrue(!main.contains("EXTRA_REQUEST"))
    }
}
