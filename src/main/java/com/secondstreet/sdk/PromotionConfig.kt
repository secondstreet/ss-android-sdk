package com.secondstreet.sdk

import android.graphics.Color
import androidx.annotation.ColorInt

data class PromotionConfig(
    val promoId: String,
    val userId: String? = null,
    val token: String? = null,
    val baseUrl: String = "https://promos.secondstreet.com",
    val mode: PromotionMode = PromotionMode.Direct,
    val heightPercent: Int = 100,
    @ColorInt val accentColor: Int = Color.parseColor("#6200EE")
) {
    fun buildUrl(): String {
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
