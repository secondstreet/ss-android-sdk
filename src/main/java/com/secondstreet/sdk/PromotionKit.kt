package com.secondstreet.sdk

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout

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

    // ── Event relay ───────────────────────────────────────────────────────────
    fun notifyLoad(promoId: String)                                             { listener?.onLoad(promoId) }
    fun notifyComplete(promoId: String, reward: Map<String, Any>?)              { listener?.onComplete(promoId, reward) }
    fun notifyClose(promoId: String)                                            { listener?.onClose(promoId) }
    fun notifyError(promoId: String, error: PromotionError)                     { listener?.onError(promoId, error) }
    fun notifyRawEvent(promoId: String, event: String, data: Map<String, Any>?) { listener?.onEvent(promoId, event, data) }

    private fun dpToPx(activity: Activity, dp: Int): Int =
        (dp * activity.resources.displayMetrics.density).toInt()
}
