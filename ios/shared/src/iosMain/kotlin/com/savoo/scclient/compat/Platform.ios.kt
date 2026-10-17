package com.savoo.scclient.compat

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.distantPast
import platform.WebKit.WKWebsiteDataStore
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

actual object Platform {
    actual val documentsDirPath: String by lazy {
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String)
    }

    actual val filesDirPath: String by lazy {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        val manager = NSFileManager.defaultManager
        manager.createDirectoryAtPath(support, true, null, null)
        manager.contentsOfDirectoryAtPath(documentsDirPath, null).orEmpty()
            .mapNotNull { it as? String }
            .filterNot { it in VISIBLE_DOCUMENTS }
            .forEach { name ->
                val target = "$support/$name"
                if (!manager.fileExistsAtPath(target)) manager.moveItemAtPath("$documentsDirPath/$name", target, null)
            }
        support
    }

    actual val cacheDirPath: String by lazy {
        (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String)
    }

    actual val bundleId: String get() = NSBundle.mainBundle.bundleIdentifier ?: "com.savoo.resona"

    @OptIn(ExperimentalForeignApi::class)
    actual val deviceModel: String by lazy {
        NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"]?.let { return@lazy "$it (Simulator)" }
        memScoped {
            val info = alloc<utsname>()
            uname(info.ptr)
            info.machine.toKString()
        }
    }

    actual val osVersion: String by lazy {
        "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
    }

    actual val buildNumber: Int by lazy {
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 0
    }

    actual val versionName: String by lazy {
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
    }

    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean get() = kotlin.native.Platform.isDebugBinary

    actual val languageTag: String
        get() = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

    actual fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
        return true
    }

    actual fun share(text: String) = present(UIActivityViewController(listOf(text), null))

    actual fun shareFile(path: String, mimeType: String) =
        present(UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null))

    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    actual fun showToast(text: String, long: Boolean) {
        ToastHost.show(text, long)
    }

    actual fun setWebCookie(url: String, cookie: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val parsed = NSHTTPCookie.cookiesWithResponseHeaderFields(mapOf("Set-Cookie" to cookie), nsUrl)
        val store = WKWebsiteDataStore.defaultDataStore().httpCookieStore
        parsed.forEach { (it as? NSHTTPCookie)?.let { c -> store.setCookie(c, null) } }
    }

    actual fun clearWebData() {
        WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
            WKWebsiteDataStore.allWebsiteDataTypes(),
            NSDate.distantPast,
        ) {}
    }

    actual fun applyLanguage(tag: String?) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (tag == null) defaults.removeObjectForKey("AppleLanguages")
        else defaults.setObject(listOf(tag), "AppleLanguages")
        Locale.setDefault(Locale.forLanguageTag(tag ?: languageTag))
    }

    private val VISIBLE_DOCUMENTS = setOf("Backups", "Exports", ".Trash")

    private fun present(controller: UIViewController) {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        while (top.presentedViewController != null) top = top.presentedViewController!!
        top.presentViewController(controller, animated = true, completion = null)
    }
}
