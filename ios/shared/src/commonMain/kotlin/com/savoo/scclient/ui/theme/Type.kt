package com.savoo.scclient.ui.theme

import com.savoo.scclient.compat.*
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import androidx.compose.ui.unit.sp
import com.savoo.scclient.resources.*

@Composable
private fun family(staticRes: FontResource, variableRes: FontResource, weight: Int, roundness: Int?): FontFamily {
    val fontWeight = FontWeight(weight)
    val font = if (roundness != null) {
        Font(
            variableRes,
            fontWeight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("ROND", roundness.toFloat()),
            ),
        )
    } else {
        Font(staticRes, fontWeight)
    }
    return FontFamily(font)
}

private data class ScFonts(
    val light: FontFamily,
    val regular: FontFamily,
    val medium: FontFamily,
    val semiBold: FontFamily,
    val bold: FontFamily,
)

@Composable
fun rememberSCTypography(fontRoundness: Int? = null): Typography {
    val fonts = ScFonts(
        light = family(Res.font.resona_light, Res.font.resona_variable_light, 300, fontRoundness),
        regular = family(Res.font.resona_regular, Res.font.resona_variable_regular, 400, fontRoundness),
        medium = family(Res.font.resona_medium, Res.font.resona_variable_medium, 500, fontRoundness),
        semiBold = family(Res.font.resona_semibold, Res.font.resona_variable_semibold, 600, fontRoundness),
        bold = family(Res.font.resona_bold, Res.font.resona_variable_bold, 700, fontRoundness),
    )
    return remember(fonts) {
        Typography(
            displayLarge = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp),
            displayMedium = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp),
            displaySmall = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
            headlineLarge = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = fonts.regular, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
        )
    }
}
