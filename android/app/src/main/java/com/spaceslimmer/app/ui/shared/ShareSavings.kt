package com.spaceslimmer.app.ui.shared

import android.content.Context
import android.content.Intent
import com.spaceslimmer.app.R

fun shareSavedBytes(context: Context, savedBytes: Long) {
    val text = context.getString(R.string.share_savings_text, formatBytes(context, savedBytes))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_savings_subject))
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_savings_chooser)))
}
