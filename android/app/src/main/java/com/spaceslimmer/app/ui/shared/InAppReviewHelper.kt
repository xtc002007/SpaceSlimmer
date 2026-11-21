package com.spaceslimmer.app.ui.shared

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory
import com.spaceslimmer.core.AppLogger

object InAppReviewHelper {
    private const val PREFS = "spaceslimmer_review"
    private const val KEY_LAST_REVIEW_TIME = "last_review_prompt_time"
    private const val KEY_COMPRESS_SUCCESS_COUNT = "compress_success_count"

    fun onCompressionSuccess(context: Context, freedBytes: Long = 0L) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = sp.getInt(KEY_COMPRESS_SUCCESS_COUNT, 0) + 1
        sp.edit().putInt(KEY_COMPRESS_SUCCESS_COUNT, count).apply()

        val lastTime = sp.getLong(KEY_LAST_REVIEW_TIME, 0L)
        val now = System.currentTimeMillis()
        val daysSinceLast = (now - lastTime) / (1000L * 60 * 60 * 24)

        if ((count == 2 || count % 5 == 0 || freedBytes > 50 * 1024 * 1024) && (lastTime == 0L || daysSinceLast >= 90)) {
            val activity = findActivity(context) ?: return
            requestReview(activity)
            sp.edit().putLong(KEY_LAST_REVIEW_TIME, now).apply()
        }
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun requestReview(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    manager.launchReviewFlow(activity, reviewInfo)
                } else {
                    AppLogger.w("InAppReview", "Review flow request failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.w("InAppReview", "In-app review launch error: ${e.message}")
        }
    }
}
