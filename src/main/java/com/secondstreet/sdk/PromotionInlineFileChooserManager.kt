package com.secondstreet.sdk

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

internal object PromotionInlineFileChooserManager {
    private const val FRAGMENT_TAG = "ss_inline_file_chooser_fragment"

    fun launch(
        activity: FragmentActivity,
        pickerIntent: Intent,
        callback: ValueCallback<Array<Uri>>
    ): Boolean {
        val fragment = getOrCreateFragment(activity) ?: return false
        return fragment.launch(pickerIntent, callback)
    }

    private fun getOrCreateFragment(activity: FragmentActivity): PromotionInlineFileChooserFragment? {
        val existing = activity.supportFragmentManager
            .findFragmentByTag(FRAGMENT_TAG) as? PromotionInlineFileChooserFragment
        if (existing != null) return existing

        if (activity.supportFragmentManager.isStateSaved) return null

        val fragment = PromotionInlineFileChooserFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, FRAGMENT_TAG)
            .commitNow()
        return fragment
    }
}

internal class PromotionInlineFileChooserFragment : Fragment() {
    private var callback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val FILE_PICKER_REQUEST_CODE = 7002
    }

    fun launch(pickerIntent: Intent, fileCallback: ValueCallback<Array<Uri>>): Boolean {
        callback?.onReceiveValue(null)
        callback = fileCallback

        return try {
            startActivityForResult(pickerIntent, FILE_PICKER_REQUEST_CODE)
            true
        } catch (e: ActivityNotFoundException) {
            callback?.onReceiveValue(null)
            callback = null
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_PICKER_REQUEST_CODE) return

        val resultUris = PromotionFileChooserUtils.extractResultUris(resultCode, data)
        callback?.onReceiveValue(resultUris)
        callback = null
    }

    override fun onDestroy() {
        super.onDestroy()
        callback?.onReceiveValue(null)
        callback = null
    }
}
