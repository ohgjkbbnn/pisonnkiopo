package com.savoo.scclient.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.HTTPMethod
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.timeIntervalSinceDate
import platform.UIKit.UIColor
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

private const val AUTH_HANDLER = "resonaAuth"

private val AUTH_HOOK = """
    (function() {
        if (window.__resonaHooked) return;
        window.__resonaHooked = true;
        function report(url, value) {
            try {
                if (value && String(value).indexOf('OAuth ') === 0) {
                    window.webkit.messageHandlers.$AUTH_HANDLER.postMessage({url: String(url), auth: String(value)});
                }
            } catch (e) {}
        }
        var origFetch = window.fetch;
        if (origFetch) {
            window.fetch = function(input, init) {
                try {
                    var url = (typeof input === 'string') ? input : (input && input.url);
                    var h = init && init.headers;
                    var auth = null;
                    if (h) {
                        if (typeof h.get === 'function') auth = h.get('Authorization');
                        else auth = h['Authorization'] || h['authorization'];
                    }
                    if (!auth && input && input.headers && typeof input.headers.get === 'function') auth = input.headers.get('Authorization');
                    report(url, auth);
                } catch (e) {}
                return origFetch.apply(this, arguments);
            };
        }
        var origOpen = XMLHttpRequest.prototype.open;
        var origSet = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.__resonaUrl = url;
            return origOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
            if (String(name).toLowerCase() === 'authorization') report(this.__resonaUrl, value);
            return origSet.apply(this, arguments);
        };
    })();
""".trimIndent()

internal object PendingWindowConfiguration {
    var value: WKWebViewConfiguration? = null
}

actual class WebView actual constructor(context: Context) {
    actual val settings: WebSettings = WebSettings()
    actual var webViewClient: WebViewClient = WebViewClient()
    actual var webChromeClient: WebChromeClient? = null

    actual var alpha: Float = 1f
        set(value) {
            field = value
            created?.alpha = value.toDouble()
        }

    private val inheritedConfiguration: WKWebViewConfiguration? = PendingWindowConfiguration.value.also {
        PendingWindowConfiguration.value = null
    }
    private var created: WKWebView? = null
    private var destroyed = false
    private val delegate = Delegate()

    val wk: WKWebView
        get() = created ?: build().also { created = it }

    private fun build(): WKWebView {
        val config = inheritedConfiguration ?: WKWebViewConfiguration().apply {
            websiteDataStore = WKWebsiteDataStore.defaultDataStore()
        }
        config.preferences.javaScriptCanOpenWindowsAutomatically = settings.javaScriptCanOpenWindowsAutomatically
        val controller = config.userContentController
        controller.removeScriptMessageHandlerForName(AUTH_HANDLER)
        controller.addScriptMessageHandler(delegate, AUTH_HANDLER)
        if (inheritedConfiguration == null) {
            controller.addUserScript(
                WKUserScript(
                    source = AUTH_HOOK,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = false,
                )
            )
        }
        val view = WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)
        view.navigationDelegate = delegate
        view.UIDelegate = delegate
        view.customUserAgent = settings.userAgentString
        view.alpha = alpha.toDouble()
        view.opaque = false
        view.backgroundColor = UIColor.clearColor
        return view
    }

    actual fun loadUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        wk.loadRequest(NSURLRequest.requestWithURL(nsUrl))
    }

    actual fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        wk.loadHTMLString(data, baseURL = baseUrl?.let { NSURL.URLWithString(it) })
    }

    actual fun reload() {
        wk.reload()
    }

    actual fun evaluateJavascript(script: String, resultCallback: ((String?) -> Unit)?) {
        if (destroyed) {
            resultCallback?.invoke(null)
            return
        }
        wk.evaluateJavaScript(script) { result, _ ->
            resultCallback?.invoke(encode(result))
        }
    }

    actual fun destroy() {
        if (destroyed) return
        destroyed = true
        created?.let {
            it.stopLoading()
            it.configuration.userContentController.removeScriptMessageHandlerForName(AUTH_HANDLER)
            it.navigationDelegate = null
            it.UIDelegate = null
            it.removeFromSuperview()
        }
    }

    actual fun postDelayed(action: () -> Unit, delayMillis: Long): Boolean {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayMillis * 1_000_000), dispatch_get_main_queue()) {
            if (!destroyed) action()
        }
        return true
    }

    private fun encode(result: Any?): String? = when (result) {
        null -> "null"
        is String -> JSONObject.quote(result)
        is NSString -> JSONObject.quote(result.toString())
        is NSNumber -> result.stringValue
        is Boolean -> result.toString()
        is Number -> result.toString()
        else -> result.toString()
    }

    private fun request(action: WKNavigationAction): WebResourceRequest {
        val url = action.request.URL?.absoluteString.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val headers = action.request.allHTTPHeaderFields as? Map<String, String>
        return WebResourceRequest(
            url = Uri.parse(url),
            isForMainFrame = action.targetFrame?.isMainFrame() ?: true,
            requestHeaders = headers,
            method = action.request.HTTPMethod ?: "GET",
        )
    }

    private inner class Delegate : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol, WKScriptMessageHandlerProtocol {

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val req = request(decidePolicyForNavigationAction)
            if (req.url.scheme == "about" || req.url.scheme == "data" || !req.isForMainFrame) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                return
            }
            webViewClient.shouldInterceptRequest(this@WebView, req)
            val block = webViewClient.shouldOverrideUrlLoading(this@WebView, req)
            decisionHandler(
                if (block) WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                else WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            )
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            CookieManager.getInstance().refresh()
            webViewClient.onPageFinished(this@WebView, webView.URL?.absoluteString)
        }

        override fun webView(
            webView: WKWebView,
            createWebViewWithConfiguration: WKWebViewConfiguration,
            forNavigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures,
        ): WKWebView? {
            val chrome = webChromeClient ?: return null
            if (!settings.supportMultipleWindows) {
                forNavigationAction.request.URL?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
                return null
            }
            PendingWindowConfiguration.value = createWebViewWithConfiguration
            var popup: WebView? = null
            val message = Message { msg -> popup = (msg.obj as? WebViewTransport)?.webView }
            message.obj = WebViewTransport()
            val handled = chrome.onCreateWindow(this@WebView, false, true, message)
            PendingWindowConfiguration.value = null
            return if (handled) popup?.wk else null
        }

        override fun webViewDidClose(webView: WKWebView) {
            webChromeClient?.onCloseWindow(this@WebView)
        }

        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
        ) {
            val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
            val url = body["url"]?.toString().orEmpty()
            val auth = body["auth"]?.toString() ?: return
            CookieManager.getInstance().refresh()
            webViewClient.shouldInterceptRequest(
                this@WebView,
                WebResourceRequest(Uri.parse(url), isForMainFrame = false, requestHeaders = mapOf("Authorization" to auth)),
            )
        }
    }
}

actual class CookieManager private constructor() {
    private val store: WKHTTPCookieStore get() = WKWebsiteDataStore.defaultDataStore().httpCookieStore
    private val cache = LinkedHashMap<String, NSHTTPCookie>()

    private val observer = object : NSObject(), WKHTTPCookieStoreObserverProtocol {
        override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) = refresh()
    }

    init {
        store.addObserver(observer)
        refresh()
    }

    internal fun refresh() {
        store.getAllCookies { cookies ->
            cache.clear()
            cookies?.forEach { c -> (c as? NSHTTPCookie)?.let { cache[key(it)] = it } }
        }
    }

    private fun key(c: NSHTTPCookie) = "${c.domain}|${c.path}|${c.name}"

    actual fun setCookie(url: String, value: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val parsed = NSHTTPCookie.cookiesWithResponseHeaderFields(mapOf("Set-Cookie" to value), nsUrl)
        parsed.forEach { any ->
            val cookie = any as? NSHTTPCookie ?: return@forEach
            cache[key(cookie)] = cookie
            store.setCookie(cookie, null)
        }
    }

    actual fun getCookie(url: String): String? {
        val host = (NSURL.URLWithString(if (url.contains("://")) url else "https://$url")?.host ?: url).lowercase()
        val now = NSDate()
        val matching = cache.values.filter { c ->
            val domain = c.domain.lowercase().removePrefix(".")
            (host == domain || host.endsWith(".$domain")) && (c.expiresDate?.let { it.timeIntervalSinceDate(now) > 0 } ?: true)
        }
        return matching.joinToString("; ") { "${it.name}=${it.value}" }.ifEmpty { null }
    }

    actual fun removeAllCookies(callback: ((Boolean) -> Unit)?) {
        val all = cache.values.toList()
        cache.clear()
        all.forEach { store.deleteCookie(it, null) }
        callback?.invoke(true)
    }

    actual fun flush() = Unit

    actual companion object {
        private val instance by lazy { CookieManager() }
        actual fun getInstance(): CookieManager = instance
    }
}

@Composable
actual fun AndroidView(
    factory: (Context) -> WebView,
    modifier: Modifier,
    update: (WebView) -> Unit,
    onRelease: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val view = remember { factory(context) }
    UIKitView(
        factory = { view.wk },
        modifier = modifier,
        update = { update(view) },
        onRelease = { onRelease(view) },
    )
}
