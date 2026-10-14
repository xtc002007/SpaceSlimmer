package com.spaceslimmer.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

data class SupportedLanguage(
    val code: String,
    val bcp47: String?,
    val nativeName: String,
    val englishName: String,
)

object LocaleHelper {
    private const val PREFS = "spaceslimmer_locale"
    private const val KEY_LANGUAGE = "language"

    const val CODE_SYSTEM = "system"
    const val CODE_EN = "en"
    const val CODE_ZH_CN = "zh-CN"
    const val CODE_ZH_TW = "zh-TW"
    const val CODE_JA = "ja"
    const val CODE_KO = "ko"
    const val CODE_ES = "es"
    const val CODE_PT_BR = "pt-BR"
    const val CODE_DE = "de"
    const val CODE_FR = "fr"
    const val CODE_RU = "ru"

    val SUPPORTED_LANGUAGES: List<SupportedLanguage> = listOf(
        SupportedLanguage(CODE_SYSTEM, null, "跟随系统", "System Default"),
        SupportedLanguage(CODE_EN, "en", "English", "English"),
        SupportedLanguage(CODE_ZH_CN, "zh-CN", "简体中文", "Simplified Chinese"),
        SupportedLanguage(CODE_ZH_TW, "zh-TW", "繁體中文", "Traditional Chinese"),
        SupportedLanguage(CODE_JA, "ja", "日本語", "Japanese"),
        SupportedLanguage(CODE_KO, "ko", "한국어", "Korean"),
        SupportedLanguage(CODE_ES, "es", "Español", "Spanish"),
        SupportedLanguage(CODE_PT_BR, "pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
        SupportedLanguage(CODE_DE, "de", "Deutsch", "German"),
        SupportedLanguage(CODE_FR, "fr", "Français", "French"),
        SupportedLanguage(CODE_RU, "ru", "Русский", "Russian"),
    )

    fun storedLanguage(context: Context): String? =
        prefs(context)
            .getString(KEY_LANGUAGE, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun hasStoredLanguage(context: Context): Boolean = storedLanguage(context) != null

    fun currentLanguageCode(context: Context): String =
        storedLanguage(context)?.let { selectorCode(it) } ?: CODE_SYSTEM

    fun findLanguage(code: String): SupportedLanguage =
        SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.first { it.code == CODE_SYSTEM }

    fun persistLanguage(context: Context, language: String) {
        val code = selectorCode(language)
        if (code == CODE_SYSTEM) {
            prefs(context).edit().remove(KEY_LANGUAGE).commit()
        } else {
            prefs(context)
                .edit()
                .putString(KEY_LANGUAGE, code)
                .commit()
        }
    }

    fun selectorCode(language: String): String {
        val tag = language.trim().lowercase(Locale.ROOT)
        return when {
            tag == "system" || tag.isEmpty() -> CODE_SYSTEM
            tag.startsWith("zh-tw") || tag.startsWith("zh-hk") || tag.startsWith("zh-hant") || tag == "zh-r-tw" || tag == "zh_tw" -> CODE_ZH_TW
            tag.startsWith("zh") -> CODE_ZH_CN
            tag.startsWith("ja") -> CODE_JA
            tag.startsWith("ko") -> CODE_KO
            tag.startsWith("es") -> CODE_ES
            tag.startsWith("pt") -> CODE_PT_BR
            tag.startsWith("de") -> CODE_DE
            tag.startsWith("fr") -> CODE_FR
            tag.startsWith("ru") -> CODE_RU
            else -> CODE_EN
        }
    }

    fun selectorCode(context: Context): String {
        storedLanguage(context)?.let { return selectorCode(it) }
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return selectorCode(locale.toLanguageTag())
    }

    fun toBcp47(language: String): String {
        val code = selectorCode(language)
        return SUPPORTED_LANGUAGES.firstOrNull { it.code == code }?.bcp47 ?: "en"
    }

    fun wrap(base: Context): Context {
        val stored = storedLanguage(base) ?: return base
        if (stored == CODE_SYSTEM) return base
        val locale = localeFor(stored)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    fun applyLanguage(context: Context, language: String) {
        val selector = selectorCode(language)
        if (selector == CODE_SYSTEM) {
            followSystem(context)
            return
        }

        persistLanguage(context, selector)
        val tag = toBcp47(selector)
        val desired = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = runCatching {
                context.safeAppContext().getSystemService(LocaleManager::class.java)
            }.getOrNull()
            if (manager != null && manager.applicationLocales.toLanguageTags() != tag) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
            }
        }
    }

    fun followSystem(context: Context) {
        prefs(context).edit().remove(KEY_LANGUAGE).commit()
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.safeAppContext().getSystemService(LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.getEmptyLocaleList()
            }
        }
    }

    fun recreateActivity(context: Context) {
        findActivity(context)?.recreate()
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun localeFor(language: String): Locale {
        val tag = toBcp47(language)
        return Locale.forLanguageTag(tag)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.safeAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun Context.safeAppContext(): Context = applicationContext ?: this
}
