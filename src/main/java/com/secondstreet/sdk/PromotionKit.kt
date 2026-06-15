package com.secondstreet.sdk

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import org.json.JSONObject

object PromotionKit {

    private var listener: PromotionListener? = null

    // ── Show promotion directly ───────────────────────────────────────────────
    fun show(
        activity: Activity,
        promoId: String,
        userId: String? = null,
        token: String? = null,
        baseUrl: String = "https://promos.secondstreet.com",
        heightPercent: Int = 100,
        listener: PromotionListener? = null
    ) {
        val config = PromotionConfig(
            promoId       = promoId,
            userId        = userId,
            token         = token,
            baseUrl       = baseUrl,
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
        userId: String? = null,
        token: String? = null,
        baseUrl: String = "https://promos.secondstreet.com",
        mode: PromotionMode.FloatingButton = PromotionMode.FloatingButton(),
        heightPercent: Int = 100,
        listener: PromotionListener? = null
    ) {
        val config = PromotionConfig(
            promoId       = promoId,
            userId        = userId,
            token         = token,
            baseUrl       = baseUrl,
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
    userId: String? = null,
    token: String? = null,
    baseUrl: String = "https://promos.secondstreet.com",
    heightDp: Int = 400,
    listener: PromotionListener? = null
    ): WebView {
    this.listener = listener

    val url = PromotionConfig(
        promoId = promoId,
        userId  = userId,
        token   = token,
        baseUrl = baseUrl
    ).buildUrl()

    val minHeightPx = (heightDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    val webView = WebView(context)
    webView.layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        minHeightPx
    )

    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true

    webView.addJavascriptInterface(object {
        @android.webkit.JavascriptInterface
        fun postMessage(message: String) {
            try {
                val json  = org.json.JSONObject(message)
                val event = json.getString("event")
                val data  = if (json.has("data")) json.getJSONObject("data") else null
                val map   = data?.keys()?.asSequence()?.associateWith { data.get(it) }

                when (event) {
                    "secondstreet:route:enter",
                    "promotion_ready"            -> listener?.onLoad(promoId)
                    "secondstreet:form:submitted",
                    "secondstreet:formpage:submitted",
                    "promotion_complete"         -> listener?.onComplete(promoId, map)
                    "secondstreet:form:abandoned",
                    "promotion_close"            -> listener?.onClose(promoId)
                    else                         -> listener?.onEvent(promoId, event, map)
                }
            } catch (e: Exception) {
                listener?.onError(promoId, PromotionError.Unknown(e.message ?: ""))
            }
        }

                @android.webkit.JavascriptInterface
                fun updateHeight(heightPx: Float) {
                        val contentHeightPx = heightPx.toInt().coerceAtLeast(minHeightPx)
                        webView.post {
                                val lp = webView.layoutParams
                                if (lp.height != contentHeightPx) {
                                        lp.height = contentHeightPx
                                        webView.layoutParams = lp
                                        webView.requestLayout()
                                }
                        }
                }
    }, "PromotionBridge")

    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(
                                """
                                (function() {
                                    function reportHeight() {
                                        var body = document.body;
                                        var doc = document.documentElement;
                                        var h = Math.max(
                                            body ? body.scrollHeight : 0,
                                            body ? body.offsetHeight : 0,
                                            doc ? doc.clientHeight : 0,
                                            doc ? doc.scrollHeight : 0,
                                            doc ? doc.offsetHeight : 0
                                        );
                                        if (window.PromotionBridge && window.PromotionBridge.updateHeight) {
                                            window.PromotionBridge.updateHeight(h);
                                        }
                                    }

                                    reportHeight();
                                    window.addEventListener('load', reportHeight);
                                    window.addEventListener('resize', reportHeight);

                                    if (window.ResizeObserver && document.documentElement) {
                                        var ro = new ResizeObserver(reportHeight);
                                        ro.observe(document.documentElement);
                                    }

                                    setTimeout(reportHeight, 300);
                                    setTimeout(reportHeight, 1000);
                                })();
                                """.trimIndent(),
                                null
                        )
            android.util.Log.d("SS-SDK", "Inline loaded: $url")
            listener?.onLoad(promoId)
        }
    }

    webView.loadUrl(url)
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
