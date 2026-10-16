package com.savoo.scclient.debug

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult

class ScreenshotModeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (ScreenshotModeState.enabled.value) {
            return ErrorResult(
                image = null,
                request = chain.request,
                throwable = ScreenshotModeSuppressedException(),
            )
        }
        return chain.proceed()
    }
}

class ScreenshotModeSuppressedException : Exception("Screenshot mode: artwork loading suppressed")
