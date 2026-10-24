package zorahm.zochat.prefixgen

import kotlin.math.roundToInt

// Colours are packed ARGB ints — the layout BufferedImage.TYPE_INT_ARGB uses — so a canvas is a plain IntArray.
const val TRANSPARENT = 0

fun rgb(hex: Int): Int = hex or (0xFF shl 24)
fun rgb(hex: String): Int = rgb(hex.removePrefix("#").toInt(16))
fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

val WHITE = rgb(0xFFFFFF)
val BLACK = rgb(0x000000)

val Int.alpha: Int get() = this ushr 24
val Int.red: Int get() = (this shr 16) and 0xFF
val Int.green: Int get() = (this shr 8) and 0xFF
val Int.blue: Int get() = this and 0xFF

fun Int.withAlpha(a: Int): Int = (this and 0xFFFFFF) or (a shl 24)

fun mix(from: Int, to: Int, t: Double): Int {
    val k = t.coerceIn(0.0, 1.0)
    fun channel(a: Int, b: Int) = (a + (b - a) * k).roundToInt()
    return argb(
        channel(from.alpha, to.alpha),
        channel(from.red, to.red),
        channel(from.green, to.green),
        channel(from.blue, to.blue),
    )
}

fun Int.lighten(t: Double): Int = mix(this, WHITE.withAlpha(alpha), t)
fun Int.darken(t: Double): Int = mix(this, BLACK.withAlpha(alpha), t)

// Rec. 709 weights on gamma-encoded channels: rough, but it only decides light vs dark ink.
fun Int.luminance(): Double = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
