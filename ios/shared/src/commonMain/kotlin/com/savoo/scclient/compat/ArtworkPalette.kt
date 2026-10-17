package com.savoo.scclient.compat

import coil3.Image
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

expect fun imageArgbPixels(image: Image): IntArray?

class PaletteBitmap(val pixels: IntArray, val width: Int, val height: Int)

suspend fun loadPaletteBitmap(url: String, size: Int? = null): PaletteBitmap? {
    val request = ImageRequest.Builder(PlatformContext.INSTANCE)
        .data(url)
        .apply { if (size != null) size(Size(size, size)) else size(Size.ORIGINAL) }
        .build()
    val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request) as? SuccessResult ?: return null
    val pixels = imageArgbPixels(result.image) ?: return null
    return PaletteBitmap(pixels, result.image.width, result.image.height)
}

class Palette private constructor(
    val swatches: List<Swatch>,
    private val targets: List<Target>,
) {
    private val selected = HashMap<Target, Swatch?>()
    private val usedColors = HashSet<Int>()
    val dominantSwatch: Swatch? = swatches.fold(null as Swatch?) { best, s -> if (best == null || s.population > best.population) s else best }

    val lightVibrantSwatch: Swatch? get() = selected[Target.LIGHT_VIBRANT]
    val vibrantSwatch: Swatch? get() = selected[Target.VIBRANT]
    val darkVibrantSwatch: Swatch? get() = selected[Target.DARK_VIBRANT]
    val lightMutedSwatch: Swatch? get() = selected[Target.LIGHT_MUTED]
    val mutedSwatch: Swatch? get() = selected[Target.MUTED]
    val darkMutedSwatch: Swatch? get() = selected[Target.DARK_MUTED]

    private fun generate() {
        for (target in targets) {
            val swatch = maxScoredSwatch(target)
            if (swatch != null && target.exclusive) usedColors.add(swatch.rgb)
            selected[target] = swatch
        }
        usedColors.clear()
    }

    private fun maxScoredSwatch(target: Target): Swatch? {
        var maxScore = 0f
        var best: Swatch? = null
        for (swatch in swatches) {
            if (!shouldBeScored(swatch, target)) continue
            val score = score(swatch, target)
            if (best == null || score > maxScore) {
                best = swatch
                maxScore = score
            }
        }
        return best
    }

    private fun shouldBeScored(swatch: Swatch, target: Target): Boolean {
        val hsl = swatch.hsl
        return hsl[1] >= target.minSaturation && hsl[1] <= target.maxSaturation &&
            hsl[2] >= target.minLightness && hsl[2] <= target.maxLightness &&
            swatch.rgb !in usedColors
    }

    private fun score(swatch: Swatch, target: Target): Float {
        val hsl = swatch.hsl
        val maxPopulation = dominantSwatch?.population ?: 1
        val total = target.saturationWeight + target.lightnessWeight + target.populationWeight
        val saturationScore = target.saturationWeight / total * (1 - abs(hsl[1] - target.targetSaturation))
        val lightnessScore = target.lightnessWeight / total * (1 - abs(hsl[2] - target.targetLightness))
        val populationScore = target.populationWeight / total * (swatch.population / maxPopulation.toFloat())
        return saturationScore + lightnessScore + populationScore
    }

    class Swatch(val rgb: Int, val population: Int) {
        val hsl: FloatArray by lazy { colorToHsl(rgb) }
    }

    class Target internal constructor(
        val minSaturation: Float, val targetSaturation: Float, val maxSaturation: Float,
        val minLightness: Float, val targetLightness: Float, val maxLightness: Float,
    ) {
        val saturationWeight = 0.24f
        val lightnessWeight = 0.52f
        val populationWeight = 0.24f
        val exclusive = true

        companion object {
            val LIGHT_VIBRANT = Target(0.35f, 1f, 1f, 0.55f, 0.74f, 1f)
            val VIBRANT = Target(0.35f, 1f, 1f, 0.3f, 0.5f, 0.7f)
            val DARK_VIBRANT = Target(0.35f, 1f, 1f, 0f, 0.26f, 0.45f)
            val LIGHT_MUTED = Target(0f, 0.3f, 0.4f, 0.55f, 0.74f, 1f)
            val MUTED = Target(0f, 0.3f, 0.4f, 0.3f, 0.5f, 0.7f)
            val DARK_MUTED = Target(0f, 0.3f, 0.4f, 0f, 0.26f, 0.45f)
        }
    }

    class Builder internal constructor(private val bitmap: PaletteBitmap) {
        fun generate(): Palette {
            val pixels = scaleDown(bitmap)
            val swatches = ColorCutQuantizer(pixels, MAX_COLORS).quantizedColors
            return Palette(swatches, DEFAULT_TARGETS).also { it.generate() }
        }
    }

    companion object {
        private const val MAX_COLORS = 16
        private const val RESIZE_AREA = 112 * 112
        private val DEFAULT_TARGETS = listOf(
            Target.LIGHT_VIBRANT, Target.VIBRANT, Target.DARK_VIBRANT,
            Target.LIGHT_MUTED, Target.MUTED, Target.DARK_MUTED,
        )

        fun from(bitmap: PaletteBitmap): Builder = Builder(bitmap)

        private fun scaleDown(bitmap: PaletteBitmap): IntArray {
            val area = bitmap.width * bitmap.height
            if (area <= RESIZE_AREA) return bitmap.pixels.copyOf()
            val ratio = sqrt(RESIZE_AREA / area.toDouble())
            val w = ceil(bitmap.width * ratio).toInt().coerceAtLeast(1)
            val h = ceil(bitmap.height * ratio).toInt().coerceAtLeast(1)
            return IntArray(w * h) { i ->
                val sx = (((i % w) + 0.5) * bitmap.width / w).toInt().coerceIn(0, bitmap.width - 1)
                val sy = (((i / w) + 0.5) * bitmap.height / h).toInt().coerceIn(0, bitmap.height - 1)
                bitmap.pixels[sy * bitmap.width + sx]
            }
        }

        internal fun colorToHsl(rgb: Int): FloatArray {
            val rf = ((rgb shr 16) and 0xFF) / 255f
            val gf = ((rgb shr 8) and 0xFF) / 255f
            val bf = (rgb and 0xFF) / 255f
            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            val delta = max - min
            val l = (max + min) / 2f
            var h: Float
            val s: Float
            if (max == min) {
                h = 0f
                s = 0f
            } else {
                h = when (max) {
                    rf -> ((gf - bf) / delta) % 6f
                    gf -> ((bf - rf) / delta) + 2f
                    else -> ((rf - gf) / delta) + 4f
                }
                s = delta / (1f - abs(2f * l - 1f))
            }
            h = (h * 60f) % 360f
            if (h < 0) h += 360f
            return floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        }

        internal fun isAllowed(rgb: Int): Boolean {
            val hsl = colorToHsl(rgb)
            val black = hsl[2] <= 0.05f
            val white = hsl[2] >= 0.95f
            val nearRedILine = hsl[0] >= 10f && hsl[0] <= 37f && hsl[1] <= 0.82f
            return !black && !white && !nearRedILine
        }
    }
}

private class ColorCutQuantizer(pixels: IntArray, maxColors: Int) {
    private val histogram = IntArray(1 shl (QUANTIZE_WORD_WIDTH * 3))
    private val colors: IntArray
    val quantizedColors: List<Palette.Swatch>

    init {
        for (i in pixels.indices) {
            val quantized = quantizeFromRgb888(pixels[i])
            pixels[i] = quantized
            histogram[quantized]++
        }
        var distinct = 0
        for (color in histogram.indices) {
            if (histogram[color] > 0 && !Palette.isAllowed(approximateToRgb888(color))) histogram[color] = 0
            if (histogram[color] > 0) distinct++
        }
        colors = IntArray(distinct)
        var index = 0
        for (color in histogram.indices) if (histogram[color] > 0) colors[index++] = color
        quantizedColors = if (distinct <= maxColors) {
            colors.map { Palette.Swatch(approximateToRgb888(it), histogram[it]) }
        } else {
            quantizePixels(maxColors)
        }
    }

    private fun quantizePixels(maxColors: Int): List<Palette.Swatch> {
        val queue = VboxQueue(maxColors)
        queue.offer(Vbox(0, colors.size - 1))
        while (queue.size < maxColors) {
            val vbox = queue.poll()
            if (vbox != null && vbox.canSplit()) {
                queue.offer(vbox.splitBox())
                queue.offer(vbox)
            } else {
                break
            }
        }
        return queue.items().map { it.averageColor() }.filter { Palette.isAllowed(it.rgb) }
    }

    private inner class Vbox(private val lowerIndex: Int, var upperIndex: Int) {
        var population = 0
        private var minRed = 0
        private var maxRed = 0
        private var minGreen = 0
        private var maxGreen = 0
        private var minBlue = 0
        private var maxBlue = 0

        init { fitBox() }

        val volume: Int get() = (maxRed - minRed + 1) * (maxGreen - minGreen + 1) * (maxBlue - minBlue + 1)

        fun canSplit(): Boolean = upperIndex + 1 - lowerIndex > 1

        fun fitBox() {
            minRed = Int.MAX_VALUE; minGreen = Int.MAX_VALUE; minBlue = Int.MAX_VALUE
            maxRed = Int.MIN_VALUE; maxGreen = Int.MIN_VALUE; maxBlue = Int.MIN_VALUE
            var count = 0
            for (i in lowerIndex..upperIndex) {
                val color = colors[i]
                count += histogram[color]
                val r = quantizedRed(color)
                val g = quantizedGreen(color)
                val b = quantizedBlue(color)
                if (r > maxRed) maxRed = r
                if (r < minRed) minRed = r
                if (g > maxGreen) maxGreen = g
                if (g < minGreen) minGreen = g
                if (b > maxBlue) maxBlue = b
                if (b < minBlue) minBlue = b
            }
            population = count
        }

        fun splitBox(): Vbox {
            val splitPoint = findSplitPoint()
            val newBox = Vbox(splitPoint + 1, upperIndex)
            upperIndex = splitPoint
            fitBox()
            return newBox
        }

        private fun longestDimension(): Int {
            val redLength = maxRed - minRed
            val greenLength = maxGreen - minGreen
            val blueLength = maxBlue - minBlue
            return when {
                redLength >= greenLength && redLength >= blueLength -> COMPONENT_RED
                greenLength >= redLength && greenLength >= blueLength -> COMPONENT_GREEN
                else -> COMPONENT_BLUE
            }
        }

        private fun findSplitPoint(): Int {
            val dimension = longestDimension()
            modifySignificantOctet(dimension, lowerIndex, upperIndex)
            colors.sort(lowerIndex, upperIndex + 1)
            modifySignificantOctet(dimension, lowerIndex, upperIndex)
            val midPoint = population / 2
            var count = 0
            for (i in lowerIndex..upperIndex) {
                count += histogram[colors[i]]
                if (count >= midPoint) return minOf(upperIndex - 1, i)
            }
            return lowerIndex
        }

        fun averageColor(): Palette.Swatch {
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var totalPopulation = 0
            for (i in lowerIndex..upperIndex) {
                val color = colors[i]
                val pop = histogram[color]
                totalPopulation += pop
                redSum += pop * quantizedRed(color)
                greenSum += pop * quantizedGreen(color)
                blueSum += pop * quantizedBlue(color)
            }
            val redMean = (redSum / totalPopulation.toFloat()).roundToInt()
            val greenMean = (greenSum / totalPopulation.toFloat()).roundToInt()
            val blueMean = (blueSum / totalPopulation.toFloat()).roundToInt()
            return Palette.Swatch(approximateToRgb888(redMean, greenMean, blueMean), totalPopulation)
        }
    }

    private fun modifySignificantOctet(dimension: Int, lower: Int, upper: Int) {
        when (dimension) {
            COMPONENT_GREEN -> for (i in lower..upper) {
                val color = colors[i]
                colors[i] = (quantizedGreen(color) shl (QUANTIZE_WORD_WIDTH * 2)) or
                    (quantizedRed(color) shl QUANTIZE_WORD_WIDTH) or quantizedBlue(color)
            }
            COMPONENT_BLUE -> for (i in lower..upper) {
                val color = colors[i]
                colors[i] = (quantizedBlue(color) shl (QUANTIZE_WORD_WIDTH * 2)) or
                    (quantizedGreen(color) shl QUANTIZE_WORD_WIDTH) or quantizedRed(color)
            }
        }
    }

    private inner class VboxQueue(capacity: Int) {
        private var queue = arrayOfNulls<Vbox>(maxOf(capacity, 1))
        var size = 0
            private set

        private fun compare(a: Vbox, b: Vbox): Int = b.volume - a.volume

        fun items(): List<Vbox> = (0 until size).map { queue[it]!! }

        fun offer(x: Vbox) {
            if (size >= queue.size) queue = queue.copyOf(queue.size * 2)
            var k = size++
            while (k > 0) {
                val parent = (k - 1) ushr 1
                val e = queue[parent]!!
                if (compare(x, e) >= 0) break
                queue[k] = e
                k = parent
            }
            queue[k] = x
        }

        fun poll(): Vbox? {
            if (size == 0) return null
            val result = queue[0]
            val n = --size
            val x = queue[n]!!
            queue[n] = null
            if (n > 0) {
                var k = 0
                val half = n ushr 1
                while (k < half) {
                    var child = 2 * k + 1
                    var c = queue[child]!!
                    val right = child + 1
                    if (right < n && compare(c, queue[right]!!) > 0) {
                        child = right
                        c = queue[child]!!
                    }
                    if (compare(x, c) <= 0) break
                    queue[k] = c
                    k = child
                }
                queue[k] = x
            }
            return result
        }
    }

    companion object {
        private const val COMPONENT_RED = -3
        private const val COMPONENT_GREEN = -2
        private const val COMPONENT_BLUE = -1
        private const val QUANTIZE_WORD_WIDTH = 5
        private const val QUANTIZE_WORD_MASK = (1 shl QUANTIZE_WORD_WIDTH) - 1

        private fun quantizeFromRgb888(color: Int): Int {
            val r = modifyWordWidth((color shr 16) and 0xFF, 8, QUANTIZE_WORD_WIDTH)
            val g = modifyWordWidth((color shr 8) and 0xFF, 8, QUANTIZE_WORD_WIDTH)
            val b = modifyWordWidth(color and 0xFF, 8, QUANTIZE_WORD_WIDTH)
            return (r shl (QUANTIZE_WORD_WIDTH * 2)) or (g shl QUANTIZE_WORD_WIDTH) or b
        }

        private fun approximateToRgb888(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or
                (modifyWordWidth(r, QUANTIZE_WORD_WIDTH, 8) shl 16) or
                (modifyWordWidth(g, QUANTIZE_WORD_WIDTH, 8) shl 8) or
                modifyWordWidth(b, QUANTIZE_WORD_WIDTH, 8)

        private fun approximateToRgb888(color: Int): Int =
            approximateToRgb888(quantizedRed(color), quantizedGreen(color), quantizedBlue(color))

        private fun quantizedRed(color: Int): Int = (color shr (QUANTIZE_WORD_WIDTH * 2)) and QUANTIZE_WORD_MASK
        private fun quantizedGreen(color: Int): Int = (color shr QUANTIZE_WORD_WIDTH) and QUANTIZE_WORD_MASK
        private fun quantizedBlue(color: Int): Int = color and QUANTIZE_WORD_MASK

        private fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int {
            val newValue = if (targetWidth > currentWidth) {
                value shl (targetWidth - currentWidth)
            } else {
                value shr (currentWidth - targetWidth)
            }
            return newValue and ((1 shl targetWidth) - 1)
        }
    }
}
