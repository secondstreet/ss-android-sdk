package com.secondstreet.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

internal class PromotionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMO_ID       = "promo_id"
        const val EXTRA_URL            = "promo_url"
        const val EXTRA_ACCENT         = "accent_color"
        const val EXTRA_HEIGHT_PERCENT = "height_percent"

        fun createIntent(context: Context, config: PromotionConfig): Intent {
            return Intent(context, PromotionActivity::class.java).apply {
                putExtra(EXTRA_PROMO_ID,       config.promoId)
                putExtra(EXTRA_URL,            config.buildUrl())
                putExtra(EXTRA_ACCENT,         config.accentColor)
                putExtra(EXTRA_HEIGHT_PERCENT, config.heightPercent)
            }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: TextView

    private lateinit var promoId: String
    private lateinit var promoUrl: String
    private var accentColor: Int   = Color.parseColor("#6200EE")
    private var heightPercent: Int = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        promoId       = intent.getStringExtra(EXTRA_PROMO_ID) ?: ""
        promoUrl      = intent.getStringExtra(EXTRA_URL) ?: ""
        accentColor   = intent.getIntExtra(EXTRA_ACCENT, Color.parseColor("#6200EE"))
        heightPercent = intent.getIntExtra(EXTRA_HEIGHT_PERCENT, 100)

        if (heightPercent < 100) {
            val displayHeight = resources.displayMetrics.heightPixels
            val targetHeight  = (displayHeight * heightPercent / 100)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight)
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        setupLayout()
        setupWebView()

        if (isNetworkAvailable()) loadPromotion()
        else showError("No internet connection.\nPlease check your network.")
    }

    private fun setupLayout() {
        val dp   = resources.displayMetrics.density
        val root = FrameLayout(this)

        if (heightPercent < 100) {
            root.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(32f, 32f, 32f, 32f, 0f, 0f, 0f, 0f)
            }
        } else {
            root.setBackgroundColor(Color.WHITE)
        }

        // WebView
        webView = WebView(this).apply { visibility = View.INVISIBLE }
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Spinner
        progressBar = ProgressBar(this)
        root.addView(progressBar, FrameLayout.LayoutParams(120, 120).apply {
            gravity = Gravity.CENTER
        })

        // Error
        errorView = TextView(this).apply {
            textSize      = 16f
            setTextColor(Color.GRAY)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            visibility    = View.GONE
        }
        root.addView(errorView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity     = Gravity.CENTER
            marginStart = (48 * dp).toInt()
            marginEnd   = (48 * dp).toInt()
        })

        // Close button — top right
        val closeSize   = (48 * dp).toInt()
        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(accentColor)
            setColorFilter(Color.WHITE)
            setPadding(
                (12 * dp).toInt(), (12 * dp).toInt(),
                (12 * dp).toInt(), (12 * dp).toInt()
            )
        }
        root.addView(closeButton, FrameLayout.LayoutParams(closeSize, closeSize).apply {
            gravity   = Gravity.TOP or Gravity.END
            topMargin = (16 * dp).toInt()
            marginEnd = (16 * dp).toInt()
        })
        closeButton.setOnClickListener { closePromotion() }
        closeButton.bringToFront()

        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(false)
        }

        webView.addJavascriptInterface(JsBridge(), "PromotionBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    webView.visibility     = View.VISIBLE
                    PromotionKit.notifyLoad(promoId)
                }
            }
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showError("Could not load promotion.\nPlease try again later.")
                    PromotionKit.notifyError(
                        promoId,
                        PromotionError.NetworkError(error.description.toString())
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                android.util.Log.d("SS-SDK", "${message.message()} -- ${message.sourceId()}:${message.lineNumber()}")
                return true
            }
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadPromotion() {
        android.util.Log.d("SS-SDK", "Loading URL: $promoUrl")
        webView.loadUrl(promoUrl)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            runOnUiThread {
                try {
                    val json  = JSONObject(message)
                    val event = json.getString("event")
                    val data  = if (json.has("data")) json.getJSONObject("data") else null
                    val map   = data?.let { parseJsonObjectToMap(it) }

                    when (event) {
                        "secondstreet:route:enter",
                        "promotion_ready"             -> PromotionKit.notifyLoad(promoId)
                        "secondstreet:form:submitted",
                        "secondstreet:formpage:submitted",
                        "promotion_complete"          -> {
                            PromotionKit.notifyComplete(promoId, map)
                            closePromotion()
                        }
                        "secondstreet:form:abandoned",
                        "promotion_close"             -> closePromotion()
                        "secondstreet:formpage:error",
                        "promotion_error"             -> PromotionKit.notifyError(
                            promoId, PromotionError.Unknown(event)
                        )
                        else -> PromotionKit.notifyRawEvent(promoId, event, map)
                    }
                } catch (e: Exception) {
                    PromotionKit.notifyError(promoId, PromotionError.Unknown(e.message ?: ""))
                }
            }
        }
    }

    private fun closePromotion() {
        PromotionKit.notifyClose(promoId)
        moveTaskToBack(true)
        overridePendingTransition(0, android.R.anim.slide_out_right)
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        webView.visibility     = View.GONE
        errorView.text         = message
        errorView.visibility   = View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm      = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun parseJsonObjectToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key -> map[key] = json.get(key) }
        return map
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else closePromotion()
    }
}
