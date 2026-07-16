package com.secondstreet.sdk

import android.graphics.Color
import androidx.annotation.ColorInt

data class PromotionConfig(
    val promoId: String,
    val testServer: String? = null,
    val mode: PromotionMode = PromotionMode.Direct,
    val heightPercent: Int = 100,
    @ColorInt val accentColor: Int = Color.parseColor("#6200EE")
) {
    fun buildUrl(): String {
        val baseUrl = if (testServer != null) {
            "https://consumer-qa-${testServer}.secondstreetapp.com"
        } else {
            "https://consumer.secondstreetapp.com"
        }

        return "$baseUrl/embed/$promoId/"
    }
}

sealed class PromotionMode {
    object Direct : PromotionMode()
    data class FloatingButton(
        val icon: String = "🎁",
        val label: String? = null,
        @ColorInt val buttonColor: Int? = null
    ) : PromotionMode()
}
