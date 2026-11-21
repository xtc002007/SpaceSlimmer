package com.spaceslimmer.app.billing

import android.content.Context
import com.spaceslimmer.app.BuildConfig

object BillingManager {
    fun isBillingEnabled(): Boolean = BuildConfig.FEATURE_IAP

    fun isProUnlocked(context: Context): Boolean {
        if (!isBillingEnabled()) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO, false)
    }

    fun canUseProFeature(context: Context, feature: ProFeature): Boolean {
        if (!isBillingEnabled()) return true
        return isProUnlocked(context) || feature == ProFeature.BASIC
    }

    fun getPlanType(context: Context): String {
        if (!isBillingEnabled()) return "free"
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("plan_type", "free") ?: "free"
    }

    enum class ProFeature { BASIC, BATCH, TARGET_SIZE, EFFICIENT }

    private const val PREFS = "spaceslimmer_billing"
    private const val KEY_PRO = "pro_unlocked"
}
