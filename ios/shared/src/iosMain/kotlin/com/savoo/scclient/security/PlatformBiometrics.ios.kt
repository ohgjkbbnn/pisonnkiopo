package com.savoo.scclient.security

import platform.Foundation.base64EncodedStringWithOptions
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual object PlatformBiometrics {
    actual fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    actual fun enrollmentState(): String? {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) return null
        return context.evaluatedPolicyDomainState?.base64EncodedStringWithOptions(0u) ?: "enrolled"
    }

    actual fun authenticate(reason: String, fallbackTitle: String, onResult: (Boolean) -> Unit) {
        val context = LAContext()
        context.localizedCancelTitle = fallbackTitle
        context.localizedFallbackTitle = ""
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, reason) { success, _ ->
            dispatch_async(dispatch_get_main_queue()) { onResult(success) }
        }
    }
}
