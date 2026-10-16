package com.savoo.scclient.ui.screens.home

import org.jetbrains.compose.resources.StringArrayResource
import com.savoo.scclient.compat.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.savoo.scclient.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GreetingPeriod(val arrayRes: StringArrayResource) {
    MORNING(Res.array.home_greeting_morning),
    AFTERNOON(Res.array.home_greeting_afternoon),
    EVENING(Res.array.home_greeting_evening),
    NIGHT(Res.array.home_greeting_night),
}

fun greetingPeriodForHour(hour: Int): GreetingPeriod = when (hour) {
    in 5..11 -> GreetingPeriod.MORNING
    in 12..16 -> GreetingPeriod.AFTERNOON
    in 17..21 -> GreetingPeriod.EVENING
    else -> GreetingPeriod.NIGHT
}

fun greetingIndexForDate(period: GreetingPeriod, date: LocalDate, phraseCount: Int): Int {
    if (phraseCount <= 0) return 0
    val seed = date.toEpochDay() + period.ordinal * 7L
    return seed.mod(phraseCount.toLong()).toInt()
}

object GreetingDebugState {
    private val _overridePeriod = MutableStateFlow<GreetingPeriod?>(null)
    val overridePeriod = _overridePeriod.asStateFlow()

    private val _overrideIndex = MutableStateFlow<Int?>(null)
    val overrideIndex = _overrideIndex.asStateFlow()

    fun setOverride(period: GreetingPeriod?, index: Int?) {
        _overridePeriod.value = period
        _overrideIndex.value = index
    }

    fun clear() = setOverride(null, null)
}

@Composable
fun rememberHomeGreeting(): String {
    val overridePeriod by GreetingDebugState.overridePeriod.collectAsState()
    val overrideIndex by GreetingDebugState.overrideIndex.collectAsState()
    val period = overridePeriod ?: greetingPeriodForHour(LocalTime.now().hour)
    val phrases = stringArrayResource(period.arrayRes)
    val index = overrideIndex?.mod(phrases.size)
        ?: greetingIndexForDate(period, LocalDate.now(), phrases.size)
    return phrases.getOrElse(index) { phrases.firstOrNull().orEmpty() }
}

@Composable
fun AutoSizeGreetingText(text: String, color: Color, modifier: Modifier = Modifier) {
    val maxFontSize = MaterialTheme.typography.displayLarge.fontSize
    val minFontSize = MaterialTheme.typography.headlineMedium.fontSize
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var maxLines by remember(text) { mutableIntStateOf(GREETING_LINES) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = if (readyToDraw) TextOverflow.Ellipsis else TextOverflow.Clip,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, lineHeight = fontSize * 1.05f),
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            when {
                readyToDraw -> Unit
                !result.didOverflowHeight -> readyToDraw = true
                fontSize > minFontSize -> fontSize = (fontSize.value - 2).coerceAtLeast(minFontSize.value).sp
                maxLines < GREETING_MAX_LINES -> {
                    maxLines++
                    fontSize = maxFontSize
                }
                else -> readyToDraw = true
            }
        },
    )
}

private const val GREETING_LINES = 2
private const val GREETING_MAX_LINES = 3
