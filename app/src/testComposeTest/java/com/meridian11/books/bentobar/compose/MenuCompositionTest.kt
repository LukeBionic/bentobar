package com.meridian11.books.bentobar.compose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.bar.ItemContextMenu
import com.meridian11.books.bentobar.bar.ItemMenu
import com.meridian11.books.bentobar.bar.BentoBarMenu
import com.meridian11.books.bentobar.bar.MenuSurface
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Section
import com.meridian11.books.bentobar.data.Store
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemType
import com.meridian11.books.bentobar.items.Items
import com.meridian11.books.bentobar.items.MenuHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose has no error boundary, and a menu runs in the accessibility service's own process: one exception while a menu composes
 * ends the service, the wake lock and any load in flight (finding F105). So every registered item type's menu is composed here,
 * under each state its debug hook can stage ([ItemType.stages]), in the real [MenuSurface]. A new item is covered by being in
 * [Items.all], a new staged state by being named in its type's `stages`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class MenuCompositionTest {
    @get:Rule val rule = createComposeRule()

    private object Host : MenuHost {
        override fun close() {}
        override fun openItemSettings(id: String) {}
        override fun afterClose(action: () -> Unit) {}
    }

    private data class Case(val type: ItemType, val item: ItemConfig, val stage: String?, val context: Boolean = false) {
        override fun toString() = (if (context) "the right-click menu of " else "the menu of ") + type.type + (stage?.let { " under `$it`" } ?: "")
    }

    @Test fun everyItemMenuComposesUnderEveryStagedState() {
        Env.init(ApplicationProvider.getApplicationContext<Application>())
        // One item of every type, as a layout has them: a staged sample is shown by its type's first item outside Off.
        val items = Items.all.map { ItemConfig("compose-${it.type}", it.type, Section.SHOWN, options = it.defaultOptions()) }
        Store.update { it.copy(items = items) }
        var shown by mutableStateOf<Case?>(null)
        var version by mutableStateOf(0)
        rule.setContent {
            val c = shown
            if (c != null) key(version) {
                MenuSurface(c.type.menuWidthDp.dp, 2000.dp, null) {
                    if (c.context) ItemContextMenu(c.item.id, Host) {} else ItemMenu(c.item.id, Host)
                }
            }
        }
        var composed = 0
        for (item in items) {
            val type = Items.of(item.type)!!
            for (stage in listOf<String?>(null) + type.stages) {
                val case = Case(type, item, stage)
                compose(case) { shown = case; version++ }
                composed++
            }
            compose(Case(type, item, null, context = true)) { shown = Case(type, item, null, context = true); version++ }
            type.debug(listOf("off"))
        }
        assertTrue("composed only $composed cases", composed > 100)
    }

    private fun compose(case: Case, show: () -> Unit) {
        try {
            case.stage?.let { case.type.debug(it.split(" ")) }
            rule.runOnUiThread { show() }
            rule.waitForIdle()
        } catch (t: Throwable) {
            throw AssertionError("$case threw while it composed: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }
}
