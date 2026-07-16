package com.secondstreet.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri

internal object PromotionFileChooserUtils {
    fun extractResultUris(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null

        val clipData = data.clipData
        if (clipData != null) {
            return Array(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri
            }
        }

        val singleUri = data.data ?: return null
        return arrayOf(singleUri)
    }
}
