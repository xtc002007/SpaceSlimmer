package com.spaceslimmer.app.ui.theme

enum class ThemeMode {
    Light,
    Dark,
    System,
    ;

    companion object {
        fun fromPref(value: String): ThemeMode = when (value) {
            "light" -> Light
            "dark" -> Dark
            else -> System
        }
    }

    fun toPref(): String = when (this) {
        Light -> "light"
        Dark -> "dark"
        System -> "system"
    }
}
