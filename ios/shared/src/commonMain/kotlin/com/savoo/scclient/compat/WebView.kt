package com.savoo.scclient.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class WebSettings internal constructor() {
    var javaScriptEnabled: Boolean = true
    var domStorageEnabled: Boolean = true
    var javaScriptCanOpenWindowsAutomatically: Boolean = false
    var userAgentString: String? = null
    internal var supportMultipleWindows = false
    fun setSupportMultipleWindows(support: Boolean) {
        supportMultipleWindows = support
    }
}

class WebResourceRequest internal constructor(
    val url: Uri,
    val isForMainFrame: Boolean,
    val requestHeaders: Map<String, String>?,
    val method: String = "GET",
)

class WebResourceResponse(val mimeType: String?, val encoding: String?, val data: InputStream?)

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    open fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = null
    open fun onPageFinished(view: WebView, url: String?) {}
}

class Message internal constructor(internal val onSend: (Message) -> Unit) {
    var obj: Any? = null
    fun sendToTarget() = onSend(this)
}

open class WebChromeClient {
    open fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false
    open fun onCloseWindow(window: WebView) {}
}

expect class WebView(context: Context) {
    val settings: WebSettings
    var webViewClient: WebViewClient
    var webChromeClient: WebChromeClient?
    var alpha: Float
    fun loadUrl(url: String)
    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?)
    fun reload()
    fun evaluateJavascript(script: String, resultCallback: ((String?) -> Unit)?)
    fun destroy()
    fun postDelayed(action: () -> Unit, delayMillis: Long): Boolean
}

class WebViewTransport {
    var webView: WebView? = null
}

expect class CookieManager {
    fun setCookie(url: String, value: String)
    fun getCookie(url: String): String?
    fun removeAllCookies(callback: ((Boolean) -> Unit)?)
    fun flush()

    companion object {
        fun getInstance(): CookieManager
    }
}

object WebStorage {
    fun getInstance(): WebStorage = this
    fun deleteAllData() = Platform.clearWebData()
}

@Composable
expect fun AndroidView(
    factory: (Context) -> WebView,
    modifier: Modifier = Modifier,
    update: (WebView) -> Unit = {},
    onRelease: (WebView) -> Unit = {},
)

class JSONTokener(private val source: String) {
    fun nextValue(): Any? {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(source)
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> if (element.isString) element.content else element.toString()
            is kotlinx.serialization.json.JsonObject -> JSONObject(element.toString())
            is kotlinx.serialization.json.JsonArray -> JSONArray(element.toString())
            else -> null
        }
    }
}
