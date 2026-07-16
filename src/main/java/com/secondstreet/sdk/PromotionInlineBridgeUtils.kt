package com.secondstreet.sdk

import android.view.ViewGroup
import android.webkit.WebView
import org.json.JSONObject

internal object PromotionInlineBridgeUtils {

    data class ParsedBridgeMessage(
        val event: String,
        val data: JSONObject?,
        val map: Map<String, Any>?
    )

    fun parseBridgeMessage(message: String?): ParsedBridgeMessage? {
        if (message == null || message == "undefined") return null

        val json = JSONObject(message)
        val event = json.getString("event")
        val data = if (json.has("data")) json.getJSONObject("data") else null
        val map = data?.keys()?.asSequence()?.associateWith { key -> data.get(key) as Any }

        return ParsedBridgeMessage(event = event, data = data, map = map)
    }

    fun parseResizeCssHeight(data: JSONObject?): Float {
        if (data?.has("height") != true) return 0f

        val heightVal = data.get("height")
        return when (heightVal) {
            is Int -> heightVal.toFloat()
            is Double -> heightVal.toFloat()
            is String -> heightVal.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    fun toInlineHeightPx(cssHeight: Float, density: Float, minHeightPx: Int): Int {
        return (cssHeight * density).toInt().coerceAtLeast(minHeightPx)
    }

    fun applyInlineHeight(view: WebView, px: Int, onApplied: ((Int) -> Unit)? = null) {
        view.post {
            val existing = view.layoutParams
            if (existing != null) {
                existing.height = px
                view.layoutParams = existing
            } else {
                val newParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    px
                )
                view.layoutParams = newParams
            }
            view.requestLayout()
            onApplied?.invoke(px)
        }
    }

    fun sendResizeAck(view: WebView, appliedHeightPx: Int) {
        view.evaluateJavascript(
            """javascript:window.PromotionBridge && window.PromotionBridge.postMessage(JSON.stringify({event: 'resize:ack', data: {height: $appliedHeightPx}}))"""
        ) { }
    }
}
