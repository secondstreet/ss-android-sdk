package com.secondstreet.sdk

import android.graphics.Rect
import android.webkit.WebView

internal object PromotionInlineScrollUtils {

    data class PendingScroll(
        var offset: Float? = null,
        var behavior: String? = null,
        var relativeToCurrent: Boolean = false
    )

    fun handleScrollTo(
        webView: WebView,
        offset: Float,
        behavior: String,
        relativeToCurrent: Boolean,
        density: Float,
        pending: PendingScroll
    ) {
        pending.offset = offset
        pending.behavior = behavior
        pending.relativeToCurrent = relativeToCurrent

        PromotionInlineBridgeUtils.applyScroll(
            view = webView,
            top = offset,
            behavior = behavior,
            relativeToCurrent = relativeToCurrent
        )

        revealOffsetInParent(webView, offset, density)
    }

    fun handleScrollToTop(webView: WebView, behavior: String, pending: PendingScroll) {
        clearPending(pending)
        PromotionInlineBridgeUtils.applyScroll(
            view = webView,
            top = 0f,
            behavior = behavior
        )
    }

    fun replayPendingAfterResize(webView: WebView, density: Float, pending: PendingScroll) {
        val replayOffset = pending.offset
        val replayBehavior = pending.behavior

        if (replayOffset != null && replayBehavior != null) {
            PromotionInlineBridgeUtils.applyScroll(
                view = webView,
                top = replayOffset,
                behavior = replayBehavior,
                relativeToCurrent = pending.relativeToCurrent
            )
            revealOffsetInParent(webView, replayOffset, density)
            clearPending(pending)
        }
    }

    private fun clearPending(pending: PendingScroll) {
        pending.offset = null
        pending.behavior = null
        pending.relativeToCurrent = false
    }

    private fun revealOffsetInParent(webView: WebView, offsetCssPx: Float, density: Float) {
        val yPx = (offsetCssPx * density).toInt().coerceAtLeast(0)
        val width = webView.width.takeIf { it > 0 } ?: 1
        val targetRect = Rect(0, yPx, width, yPx + 1)
        webView.requestRectangleOnScreen(targetRect, true)
    }
}
