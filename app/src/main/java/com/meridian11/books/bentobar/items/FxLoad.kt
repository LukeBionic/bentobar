package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.items.FxRules.FxTable
import com.meridian11.books.bentobar.net.Host
import com.meridian11.books.bentobar.net.Http
import com.meridian11.books.bentobar.net.Reply
import com.meridian11.books.bentobar.net.Request
import com.meridian11.books.bentobar.net.Why

// Requests and loading for the Exchange rates item (Frankfurter / ECB). No Android, and no logging.

object FxLoad {
    fun request(nowMs: Long) = Request(Host.FRANKFURTER, "/v1/${FxRules.startDate(nowMs)}..", listOf("base" to "EUR"))

    fun load(last: FxTable?, now: Long): FxTable? {
        val reply = Http.get(request(now))
        val good = (reply as? Reply.Ok)?.let { FxRules.read(it.text, now) }
        if (good != null) return good
        val failed = reply as? Reply.Failed
        if (failed?.why == Why.OFF) return null
        val failure = when {
            failed?.why == Why.OFFLINE -> Failure.OFFLINE
            failed?.status == 429 -> Failure.SLOW_DOWN
            else -> Failure.NO_ANSWER
        }
        val known = last ?: FxTable()
        return known.copy(failure = failure)
    }
}
