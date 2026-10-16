package com.savoo.scclient.ui.haptics

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

actual object PlatformHaptics {
    private val generators by lazy {
        listOf(
            UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft,
            UIImpactFeedbackStyle.UIImpactFeedbackStyleLight,
            UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium,
            UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy,
        ).map { UIImpactFeedbackGenerator(style = it).apply { prepare() } }
    }

    actual fun impact(style: Int, intensity: Float, delayMs: Long) {
        val fire = {
            val generator = generators[style.coerceIn(0, 3)]
            generator.impactOccurredWithIntensity(intensity.toDouble())
            generator.prepare()
        }
        if (delayMs <= 0) dispatch_async(dispatch_get_main_queue(), fire)
        else dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayMs * 1_000_000), dispatch_get_main_queue(), fire)
    }
}
