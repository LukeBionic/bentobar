package com.meridian11.books.bentobar.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Short messages. Without an action it's a toast, which fades on its own. With one (Undo, Open
 * settings) it's the settings window's one snackbar ([rememberNotices]), shown for a fixed time: a snackbar with an action
 * and no duration stays until dismissed. A new message replaces the one that is up rather than waiting behind it: a third
 * quick Add's Undo must not arrive twenty seconds later and undo something that has changed since.
 */
object Notice {
    class Message(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null, val onGone: (() -> Unit)? = null)

    val messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)

    /** [onGone]: when the message has run its time or was acted on. Not when it is replaced or the window goes (a recreated window may offer it again). */
    fun post(text: String, action: String? = null, onAction: (() -> Unit)? = null, onGone: (() -> Unit)? = null) {
        if (action == null) android.widget.Toast.makeText(com.meridian11.books.bentobar.items.Env.app, text, android.widget.Toast.LENGTH_SHORT).show()
        else messages.tryEmit(Message(text, action, onAction, onGone))
    }
}

/** The snackbar state the settings window's one host draws, with every [Notice] going to it. Call it above what posts them: a message is seen only by someone already listening. */
@Composable
fun rememberNotices(): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Notice.messages.collectLatest { m ->
            if (snackbar.showSnackbar(m.text, m.action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) m.onAction?.invoke()
            m.onGone?.invoke()
        }
    }
    return snackbar
}
