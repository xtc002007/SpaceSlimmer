package com.spaceslimmer.app.billing

import android.content.Context

object BillingManager {
    fun isBillingEnabled(): Boolean = true

    fun isProUnlocked(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO, false)
    }

    fun canUseProFeature(context: Context, feature: ProFeature): Boolean {
        return isProUnlocked(context) || feature == ProFeature.BASIC
    }

    fun getPlanType(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("plan_type", "free") ?: "free"
    }

    enum class ProFeature { BASIC, BATCH, TARGET_SIZE, EFFICIENT }

    private const val PREFS = "spaceslimmer_billing"
    private const val KEY_PRO = "pro_unlocked"
}
