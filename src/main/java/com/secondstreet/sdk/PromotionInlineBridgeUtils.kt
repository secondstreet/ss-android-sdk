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

    fun parseScrollOffset(data: JSONObject?): Float? {
        if (data?.has("offset") != true) return null

        val offsetVal = data.get("offset")
        val value = when (offsetVal) {
            is Int -> offsetVal.toFloat()
            is Double -> offsetVal.toFloat()
            is String -> offsetVal.toFloatOrNull()
            else -> null
        } ?: return null

        return value.coerceAtLeast(0f)
    }

    fun parseScrollBehavior(data: JSONObject?): String {
        val raw = data?.optString("behavior", "smooth") ?: "smooth"
        return if (raw.equals("auto", ignoreCase = true)) "auto" else "smooth"
    }

    fun isViewportRelativeOffset(data: JSONObject?): Boolean {
        val offsetType = data?.optString("offsetType", "") ?: ""
        if (offsetType.equals("element", ignoreCase = true) ||
            offsetType.equals("viewport", ignoreCase = true)) {
            return true
        }

        val relative = data?.opt("relativeToCurrent")
        return when (relative) {
            is Boolean -> relative
            is String -> relative.equals("true", ignoreCase = true)
            is Int -> relative != 0
            is Double -> relative != 0.0
            else -> false
        }
    }

    fun applyScroll(
        view: WebView,
        top: Float,
        behavior: String,
        relativeToCurrent: Boolean = false
    ) {
        val normalizedBehavior = if (behavior.equals("auto", ignoreCase = true)) "auto" else "smooth"
        view.evaluateJavascript(
            """javascript:(function(){var offset=$top;var rel=$relativeToCurrent;var behavior='$normalizedBehavior';var current=(window.scrollY||window.pageYOffset||0);var target=rel?(current+offset):offset;var docEl=document.documentElement||{};var body=document.body||{};var scrollingEl=document.scrollingElement||docEl||body;var scrollHeight=(scrollingEl.scrollHeight||docEl.scrollHeight||body.scrollHeight||0);var maxScrollable=Math.max(0,scrollHeight-(window.innerHeight||0));if(maxScrollable>0){try{window.scrollTo({top:target,behavior:behavior});}catch(e){window.scrollTo(0,target);}}else{var nodes=document.querySelectorAll('*');var best=null;var bestDelta=0;for(var i=0;i<nodes.length;i++){var el=nodes[i];var style=window.getComputedStyle(el);var overflowY=style?style.overflowY:'';var delta=(el.scrollHeight||0)-(el.clientHeight||0);if(delta>1&&(overflowY==='auto'||overflowY==='scroll'||overflowY==='overlay')){if(delta>bestDelta){bestDelta=delta;best=el;}}}if(best){var containerTarget=rel?((best.scrollTop||0)+offset):offset;try{if(best.scrollTo){best.scrollTo({top:containerTarget,behavior:behavior});}else{best.scrollTop=containerTarget;}}catch(e){best.scrollTop=containerTarget;}}}})();"""
        ) { }
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
