package com.secondstreet.sdk

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt

internal class PromotionFloatingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onTap: (() -> Unit)? = null

    private val fabButton: TextView
    private val fabLabel: TextView

    init {
        orientation  = VERTICAL
        gravity      = Gravity.CENTER_HORIZONTAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        fabButton = TextView(context).apply {
            textSize     = 26f
            gravity      = Gravity.CENTER
            typeface     = Typeface.DEFAULT_BOLD
            val size     = dpToPx(56)
            layoutParams = LayoutParams(size, size)
            elevation    = dpToPx(6).toFloat()
        }
        addView(fabButton)

        fabLabel = TextView(context).apply {
            textSize = 11f
            gravity  = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
            visibility = GONE
        }
        addView(fabLabel)

        setOnClickListener {
            animateTap()
            onTap?.invoke()
        }
    }

    fun configure(icon: String, label: String?, @ColorInt buttonColor: Int?) {
        fabButton.text       = icon
        fabButton.background = createCircleBackground(buttonColor ?: Color.parseColor("#6200EE"))
        if (label != null) {
            fabLabel.text      = label
            fabLabel.visibility = VISIBLE
        } else {
            fabLabel.visibility = GONE
        }
    }

    private fun animateTap() {
        val scaleDownX = ObjectAnimator.ofFloat(fabButton, "scaleX", 1f, 0.88f).apply { duration = 80 }
        val scaleDownY = ObjectAnimator.ofFloat(fabButton, "scaleY", 1f, 0.88f).apply { duration = 80 }
        val scaleUpX   = ObjectAnimator.ofFloat(fabButton, "scaleX", 0.88f, 1f).apply { duration = 80 }
        val scaleUpY   = ObjectAnimator.ofFloat(fabButton, "scaleY", 0.88f, 1f).apply { duration = 80 }
        val down = AnimatorSet().apply { playTogether(scaleDownX, scaleDownY) }
        val up   = AnimatorSet().apply { playTogether(scaleUpX, scaleUpY) }
        AnimatorSet().apply { playSequentially(down, up); start() }
    }

    private fun createCircleBackground(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
