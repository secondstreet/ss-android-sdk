package com.secondstreet.sdk

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import org.json.JSONObject

object PromotionKit {

    private var listener: PromotionListener? = null

    // ── Show promotion directly ───────────────────────────────────────────────
    fun show(
        activity: Activity,
        promoId: String,
        testServer: String? = null,
        heightPercent: Int = 100,
        listener: PromotionListener? = null
    ) {
        val config = PromotionConfig(
            promoId       = promoId,
            testServer    = testServer,
            mode          = PromotionMode.Direct,
            heightPercent = heightPercent
        )
        show(activity, config, listener)
    }

    fun show(
        activity: Activity,
        config: PromotionConfig,
        listener: PromotionListener? = null
    ) {
        this.listener = listener
        val intent = PromotionActivity.createIntent(activity, config)
        activity.startActivity(intent)
        activity.overridePendingTransition(android.R.anim.slide_in_left, 0)
    }

    // ── Attach floating button ────────────────────────────────────────────────
    fun attach(
        activity: Activity,
        promoId: String,
        testServer: String? = null,
        mode: PromotionMode.FloatingButton = PromotionMode.FloatingButton(),
        heightPercent: Int = 100,
        listener: PromotionListener? = null
    ) {
        val config = PromotionConfig(
            promoId       = promoId,
            testServer    = testServer,
            mode          = mode,
            heightPercent = heightPercent
        )
        attach(activity, config, listener)
    }

    fun attach(
        activity: Activity,
        config: PromotionConfig,
        listener: PromotionListener? = null
    ) {
        this.listener = listener

        val mode = config.mode
        if (mode !is PromotionMode.FloatingButton) {
            show(activity, config, listener)
            return
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val fab      = PromotionFloatingButton(activity)
        fab.configure(
            icon        = mode.icon,
            label       = mode.label,
            buttonColor = mode.buttonColor ?: config.accentColor
        )

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity      = Gravity.BOTTOM or Gravity.END
            bottomMargin = dpToPx(activity, 32)
            marginEnd    = dpToPx(activity, 24)
        }

        rootView.addView(fab, params)

        fab.onTap = {
            val intent = PromotionActivity.createIntent(activity, config)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            activity.startActivity(intent)
        }
    }

    fun inline(
        context: Context,
        promoId: String,
        testServer: String? = null,
        heightDp: Int = 400,
        customUrl: String? = null,
        listener: PromotionListener? = null
    ): WebView {
        this.listener = listener

        val url = if (customUrl != null) {
            // Construct URL with path structure: customUrl/embed/promotionId/
            val baseUrl = customUrl.trimEnd('/')
            "$baseUrl/embed/$promoId/"
        } else {
            PromotionConfig(
                promoId = promoId,
                testServer = testServer
            ).buildUrl()
        }

        val minHeightPx = (heightDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        val displayMetrics = context.resources.displayMetrics
        android.util.Log.d(
            "PromotionKit",
            "DisplayMetrics density=${displayMetrics.density}, densityDpi=${displayMetrics.densityDpi}, " +
                "scaledDensity=${displayMetrics.scaledDensity}, widthPx=${displayMetrics.widthPixels}, " +
                "heightPx=${displayMetrics.heightPixels}, heightDpParam=$heightDp, minHeightPx=$minHeightPx"
        )

        fun toInlineHeightPx(cssHeight: Float): Int {
            return (cssHeight * displayMetrics.density).toInt()
                .coerceAtLeast(minHeightPx)
        }

        fun applyInlineHeight(view: WebView, px: Int, onApplied: ((Int) -> Unit)? = null) {
            view.post {
                val existing = view.layoutParams
                if (existing != null) {
                    existing.height = px
                    view.layoutParams = existing
                } else {
                    val newParams = when (view.parent) {
                        is LinearLayout -> LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            px
                        )
                        is FrameLayout -> FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            px
                        )
                        is ViewGroup -> ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            px
                        )
                        else -> ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            px
                        )
                    }
                    view.layoutParams = newParams
                }
                view.requestLayout()
                val screenHeightPx = context.resources.displayMetrics.heightPixels
                val parentName = view.parent?.javaClass?.simpleName ?: "null"
                if (px > screenHeightPx) {
                    val parentScrollable = parentName.contains("ScrollView") || parentName.contains("RecyclerView")
                    if (!parentScrollable) {
                        android.util.Log.w(
                            "PromotionKit",
                            "Applied height ($px) is larger than screen height ($screenHeightPx) in non-scrollable parent ($parentName). " +
                                "Content may appear cut off unless the parent container scrolls or WebView scrolling is enabled."
                        )
                    }
                }
                onApplied?.invoke(px)
            }
        }

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Prefer auto-height, but keep WebView scroll as fallback for constrained parents.
        webView.isScrollContainer = true
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.minimumHeight = minHeightPx

        fun updateInlineHeight(view: WebView, onApplied: ((Int) -> Unit)? = null) {
            view.evaluateJavascript(
                """
                (function() {
                    var body = document.body;
                    var doc = document.documentElement;
                    return Math.max(
                        body ? body.scrollHeight : 0,
                        body ? body.offsetHeight : 0,
                        doc ? doc.scrollHeight : 0,
                        doc ? doc.offsetHeight : 0,
                        doc ? doc.clientHeight : 0
                    );
                })()
                """.trimIndent()
            ) { height ->
                val contentHeight = height
                    ?.replace("\"", "")
                    ?.toFloatOrNull()
                    ?: 0f
                if (contentHeight > 0) {
                    val px = toInlineHeightPx(contentHeight)
                    applyInlineHeight(view, px, onApplied)
                }
            }
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun postMessage(message: String?) {
                android.util.Log.d("PromotionKit", "🔵 postMessage called with: $message")
                if (message == null || message == "undefined") {
                    android.util.Log.d("PromotionKit", "🔴 Received null/undefined, ignoring")
                    return
                }
                try {
                    val json = JSONObject(message)
                    val event = json.getString("event")
                    val data = if (json.has("data")) json.getJSONObject("data") else null
                    val map = data?.keys()?.asSequence()?.associateWith { data.get(it) }

                    android.util.Log.d("PromotionKit", "🔵 Event type: $event")

                    when (event) {
                        "resize" -> {
                            android.util.Log.d("PromotionKit", "🟢 RESIZE event received. Raw data: $data")
                            android.util.Log.d(
                                "PromotionKit",
                                "Resize context density=${displayMetrics.density}, densityDpi=${displayMetrics.densityDpi}, " +
                                    "scaledDensity=${displayMetrics.scaledDensity}"
                            )
                            
                            // JavaScript height is already the effective rendered height for the page.
                            val heightCssPx = if (data?.has("height") == true) {
                                val heightVal = data.get("height")
                                android.util.Log.d("PromotionKit", "Height value: $heightVal (type: ${heightVal?.javaClass?.simpleName})")
                                when (heightVal) {
                                    is Int -> {
                                        android.util.Log.d("PromotionKit", "Height is Int: $heightVal")
                                        heightVal.toFloat()
                                    }
                                    is Double -> {
                                        val floatHeight = heightVal.toFloat()
                                        android.util.Log.d("PromotionKit", "Height is Double: $heightVal -> converted to Float: $floatHeight")
                                        floatHeight
                                    }
                                    is String -> {
                                        val floatHeight = heightVal.toFloatOrNull() ?: 0f
                                        android.util.Log.d("PromotionKit", "Height is String: $heightVal -> converted to Float: $floatHeight")
                                        floatHeight
                                    }
                                    else -> {
                                        android.util.Log.d("PromotionKit", "Height has unexpected type: ${heightVal?.javaClass?.simpleName}")
                                        0f
                                    }
                                }
                            } else {
                                android.util.Log.d("PromotionKit", "Height field not found in data")
                                0f
                            }

                            val requestedHeightPx = toInlineHeightPx(heightCssPx)
                            
                            android.util.Log.d(
                                "PromotionKit",
                                "Resize requested height: css=$heightCssPx -> requested=$requestedHeightPx"
                            )
                            
                            if (requestedHeightPx > 0) {
                                webView.post {
                                    // Re-measure from DOM and ensure height is at least the requested resize value.
                                    updateInlineHeight(webView) { appliedHeightPx ->
                                        val finalHeightPx = maxOf(appliedHeightPx, requestedHeightPx)
                                        if (finalHeightPx != appliedHeightPx) {
                                            android.util.Log.d(
                                                "PromotionKit",
                                                "Measured height under requested size, applying requested height. measured=$appliedHeightPx requested=$requestedHeightPx"
                                            )
                                            applyInlineHeight(webView, finalHeightPx) { adjustedHeightPx ->
                                                android.util.Log.d("PromotionKit", "Applied adjusted inline height: $adjustedHeightPx")
                                                webView.evaluateJavascript(
                                                    """javascript:window.PromotionBridge && window.PromotionBridge.postMessage(JSON.stringify({event: 'resize:ack', data: {height: $adjustedHeightPx}}))"""
                                                ) { result ->
                                                    android.util.Log.d("PromotionKit", "Sent resize:ack back to webapp. Result: $result")
                                                }
                                            }
                                        } else {
                                            android.util.Log.d("PromotionKit", "Applied measured inline height: $appliedHeightPx")
                                            webView.evaluateJavascript(
                                                """javascript:window.PromotionBridge && window.PromotionBridge.postMessage(JSON.stringify({event: 'resize:ack', data: {height: $appliedHeightPx}}))"""
                                            ) { result ->
                                                android.util.Log.d("PromotionKit", "Sent resize:ack back to webapp. Result: $result")
                                            }
                                        }
                                    }
                                }
                            } else {
                                android.util.Log.w("PromotionKit", "Invalid requestedHeightPx: $requestedHeightPx, skipping resize")
                            }
                        }
                        "secondstreet:route:enter",
                        "promotion_ready" -> listener?.onLoad(promoId)
                        "secondstreet:form:submitted",
                        "secondstreet:formpage:submitted",
                        "promotion_complete" -> listener?.onComplete(promoId, map)
                        "secondstreet:form:abandoned",
                        "promotion_close" -> listener?.onClose(promoId)
                        else -> listener?.onEvent(promoId, event, map)
                    }
                } catch (e: Exception) {
                    listener?.onError(promoId, PromotionError.Unknown(e.message ?: ""))
                }
            }
        }, "PromotionBridge")
        android.util.Log.d("PromotionKit", "🔵 JavaScript interface 'PromotionBridge' registered")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) {
                    updateInlineHeight(view)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                android.util.Log.d("PromotionKit", "🔵 onPageStarted: $url")
                
                // Wait a tiny bit for the interface to be ready, then inject wrapper
                view.postDelayed({
                    android.util.Log.d("PromotionKit", "Installing wrapper via evaluateJavascript...")
                    view.evaluateJavascript("""
                        (function() {
                            console.log('[WRAPPER] Starting wrapper installation');
                            if (!window.PromotionBridge) {
                                console.log('[WRAPPER] ERROR: PromotionBridge not available');
                                return 'NO_BRIDGE';
                            }
                            var original = window.PromotionBridge.postMessage;
                            console.log('[WRAPPER] Original type:', typeof original);
                            window.PromotionBridge.postMessage = function(msg) {
                                console.log('[WRAPPER] Intercepted:', JSON.stringify(msg));
                                if (msg) {
                                    original(JSON.stringify(msg));
                                }
                            };
                            console.log('[WRAPPER] Wrapper installed');
                            return 'WRAPPER_READY';
                        })();
                    """.trimIndent()) { result ->
                        android.util.Log.d("PromotionKit", "Wrapper install result: $result")
                    }
                }, 100)
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.d("PromotionKit", "🔵 onPageFinished: $url")
                updateInlineHeight(view)
                view.postDelayed({ updateInlineHeight(view) }, 250)
                view.postDelayed({ updateInlineHeight(view) }, 750)
                view.postDelayed({ updateInlineHeight(view) }, 1500)
                listener?.onLoad(promoId)
            }
        }

        android.util.Log.d("PromotionKit", "🔵 Inline view created. Loading URL: $url")
        webView.loadUrl(url)
        
        // Set initial layout params with the specified height
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            minHeightPx
        )
        webView.layoutParams = layoutParams
        
        return webView
    }

    // ── Event relay ───────────────────────────────────────────────────────────
    fun notifyLoad(promoId: String)                                             { listener?.onLoad(promoId) }
    fun notifyComplete(promoId: String, reward: Map<String, Any>?)              { listener?.onComplete(promoId, reward) }
    fun notifyClose(promoId: String)                                            { listener?.onClose(promoId) }
    fun notifyError(promoId: String, error: PromotionError)                     { listener?.onError(promoId, error) }
    fun notifyRawEvent(promoId: String, event: String, data: Map<String, Any>?) { listener?.onEvent(promoId, event, data) }

    private fun dpToPx(activity: Activity, dp: Int): Int =
        (dp * activity.resources.displayMetrics.density).toInt()
}
