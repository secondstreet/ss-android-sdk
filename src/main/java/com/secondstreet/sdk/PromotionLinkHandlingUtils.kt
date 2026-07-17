package com.secondstreet.sdk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

internal object PromotionLinkHandlingUtils {

    fun openIfExternal(context: Context, uri: Uri, promotionUrl: String): Boolean {
        return if (shouldOpenExternally(uri, promotionUrl)) openExternally(context, uri) else false
    }

    fun handleCreateWindow(context: Context, view: WebView, resultMsg: Message): Boolean {
        val hitTestUrl = view.hitTestResult?.extra
        if (!hitTestUrl.isNullOrBlank() && openExternally(context, Uri.parse(hitTestUrl))) {
            return false
        }

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popupWebView = WebView(view.context)
        popupWebView.settings.javaScriptEnabled = true
        popupWebView.settings.domStorageEnabled = true
        popupWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        popupWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return openExternally(context, request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return openExternally(context, Uri.parse(url))
            }
        }
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }

    private fun shouldOpenExternally(uri: Uri, promotionUrl: String): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return true

        val targetHost = uri.host?.lowercase() ?: return false
        val promoHost = Uri.parse(promotionUrl).host?.lowercase() ?: return false
        return targetHost != promoHost && !targetHost.endsWith(".$promoHost")
    }

    private fun openExternally(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
