package com.secondstreet.sdk

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.fragment.app.FragmentActivity

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
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun postMessage(message: String?) {
                try {
                    val parsed = PromotionInlineBridgeUtils.parseBridgeMessage(message) ?: return
                    val event = parsed.event
                    val data = parsed.data
                    val map = parsed.map

                    when (event) {
                        "resize" -> {
                            val heightCssPx = PromotionInlineBridgeUtils.parseResizeCssHeight(data)
                            val requestedHeightPx = PromotionInlineBridgeUtils.toInlineHeightPx(
                                cssHeight = heightCssPx,
                                density = displayMetrics.density,
                                minHeightPx = minHeightPx
                            )

                            if (requestedHeightPx > 0) {
                                PromotionInlineBridgeUtils.applyInlineHeight(webView, requestedHeightPx) { appliedHeightPx ->
                                    PromotionInlineBridgeUtils.sendResizeAck(webView, appliedHeightPx)
                                }
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                listener?.onLoad(promoId)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val hostActivity = findHostActivity(context)
                if (hostActivity !is FragmentActivity) {
                    filePathCallback.onReceiveValue(null)
                    Toast.makeText(context, "Inline file chooser requires an Activity context", Toast.LENGTH_SHORT).show()
                    return false
                }

                val launched = PromotionInlineFileChooserManager.launch(
                    activity = hostActivity,
                    pickerIntent = fileChooserParams.createIntent(),
                    callback = filePathCallback
                )

                if (!launched) {
                    Toast.makeText(context, "Cannot open file chooser", Toast.LENGTH_SHORT).show()
                    return false
                }

                return true
            }
        }

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

    private tailrec fun findHostActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findHostActivity(context.baseContext)
        else -> null
    }
}
