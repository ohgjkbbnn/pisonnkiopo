package com.savoo.scclient.compat

expect object Platform {
    val filesDirPath: String
    val cacheDirPath: String
    val bundleId: String
    val buildNumber: Int
    val versionName: String
    val isDebug: Boolean
    val languageTag: String
    fun openUrl(url: String): Boolean
    fun share(text: String)
    fun shareFile(path: String, mimeType: String)
    fun copyToClipboard(text: String)
    fun showToast(text: String, long: Boolean)
    fun setWebCookie(url: String, cookie: String)
    fun clearWebData()
    fun applyLanguage(tag: String?)
}

object BuildConfig {
    val DEBUG: Boolean get() = Platform.isDebug
    val VERSION_NAME: String get() = Platform.versionName
    val VERSION_CODE: Int get() = Platform.buildNumber
    const val APPLICATION_ID = "com.savoo.resona"
    const val BUILD_TYPE = "ios"
    const val SC_CLIENT_ID_FALLBACK = ""
    const val TELEGRAM_API_ID = "0"
    const val TELEGRAM_API_HASH = ""
}

expect class SharedPreferencesImpl : SharedPreferences {
    companion object {
        fun get(name: String): SharedPreferences
    }
}
