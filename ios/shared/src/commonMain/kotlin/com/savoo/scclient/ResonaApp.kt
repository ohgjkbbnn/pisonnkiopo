package com.savoo.scclient

import com.savoo.scclient.ui.components.LocalLiquidGlass
import com.savoo.scclient.ui.components.LocalDockBackground
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.savoo.scclient.compat.AppRestart
import com.savoo.scclient.compat.Platform
import com.savoo.scclient.compat.Strings
import com.savoo.scclient.compat.ToastHost
import com.savoo.scclient.data.remote.DataDomeChallenge
import com.savoo.scclient.data.repository.AppBackgroundMode
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.DataDomeChallengeSheet
import com.savoo.scclient.ui.components.LocalAnimatedArtwork
import com.savoo.scclient.ui.components.LocalArtworkGlowSource
import com.savoo.scclient.ui.components.LocalDividerStyle
import com.savoo.scclient.ui.components.LocalMarqueeTitles
import com.savoo.scclient.ui.haptics.LocalHapticsEnabled
import com.savoo.scclient.ui.haptics.LocalHapticsIntensity
import com.savoo.scclient.ui.navigation.DeepLinkTarget
import com.savoo.scclient.ui.navigation.RootScreen
import com.savoo.scclient.ui.screens.onboarding.EulaGateScreen
import com.savoo.scclient.ui.screens.onboarding.OnboardingScreen
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.ui.theme.ResonaTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ResonaApp(deepLinkTarget: DeepLinkTarget = DeepLinkTarget.None, deepLinkKey: Int = 0) {
    val settingsRepository = koinInject<SettingsRepository>()
    val playerController = koinInject<PlayerController>()
    val dataDomeChallenge = koinInject<DataDomeChallenge>()
    val restartTick by AppRestart.tick.collectAsState()

    val settings = settingsRepository.settings.collectAsState(initial = null).value
    val trackSeedColor by playerController.seedColor.collectAsState()
    val systemDark = isSystemInDarkTheme()

    if (settings == null) {
        Surface(color = if (systemDark) Color.Black else Color.White, modifier = Modifier.fillMaxSize()) {}
        return
    }

    val languageTag = settings.language.locale?.language
    var appliedLanguage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(languageTag) {
        Platform.applyLanguage(languageTag)
        Strings.preload()
        appliedLanguage = languageTag ?: ""
    }
    if (appliedLanguage != (languageTag ?: "")) {
        Surface(color = if (systemDark) Color.Black else Color.White, modifier = Modifier.fillMaxSize()) {}
        return
    }

    val isDark = when (settings.darkMode) {
        DarkModeOption.SYSTEM -> systemDark
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    val backgroundMode = settings.backgroundMode
    val pixelWash = backgroundMode == AppBackgroundMode.DYNAMIC && trackSeedColor != null
    val customBackground = settings.backgroundCustomColor.takeIf { backgroundMode == AppBackgroundMode.CUSTOM }

    val effectiveTheme = if (settings.dynamicFromTrack && trackSeedColor != null) {
        AppColorTheme.DYNAMIC_TRACK
    } else {
        settings.colorTheme
    }

    key(restartTick, languageTag) {
        ResonaTheme(
            colorTheme = effectiveTheme,
            darkTheme = isDark,
            overrideSeedColor = when {
                settings.dynamicFromTrack -> trackSeedColor
                settings.colorTheme == AppColorTheme.CUSTOM -> settings.customSeedColor
                else -> null
            },
            tintSurfaces = pixelWash,
            customBackground = customBackground,
            fontRoundness = settings.fontRoundness.takeIf { settings.fontRoundnessEnabled },
        ) {
            CompositionLocalProvider(
                LocalHapticsEnabled provides settings.hapticsEnabled,
                LocalHapticsIntensity provides settings.hapticsIntensity,
                LocalDividerStyle provides settings.dividerStyle,
                LocalMarqueeTitles provides settings.marqueeTitles,
                LocalArtworkGlowSource provides settings.artworkGlowSource,
                LocalAnimatedArtwork provides settings.animatedArtwork,
                LocalLiquidGlass provides settings.liquidGlass,
                LocalDockBackground provides settings.dockBackground,
            ) {
                var eulaDismissed by remember { mutableStateOf(false) }
                var onboardingDismissed by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    when {
                        !settings.eulaAccepted && !eulaDismissed -> EulaGateScreen(
                            onAccept = {
                                eulaDismissed = true
                                scope.launch { settingsRepository.setEulaAccepted(true) }
                            },
                            onDecline = { eulaDismissed = false },
                        )
                        !settings.onboardingCompleted && !onboardingDismissed -> OnboardingScreen(
                            onFinish = {
                                onboardingDismissed = true
                                scope.launch { settingsRepository.setOnboardingCompleted(true) }
                            },
                        )
                        else -> RootScreen(initialDeepLink = deepLinkTarget, deepLinkKey = deepLinkKey)
                    }
                    val challenge by dataDomeChallenge.pending.collectAsState()
                    challenge?.let { pending ->
                        key(pending.url) {
                            DataDomeChallengeSheet(
                                challenge = pending,
                                onPassed = { dataDomeChallenge.onPassed(it) },
                                onDismiss = { dataDomeChallenge.onDismissed() },
                            )
                        }
                    }
                    ToastHost.Overlay()
                }
            }
        }
    }
}
