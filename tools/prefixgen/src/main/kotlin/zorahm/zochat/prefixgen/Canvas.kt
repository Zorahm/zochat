package zorahm.zochat.prefixgen

import java.awt.image.BufferedImage

class Canvas(val width: Int, val height: Int) {
    private val pixels = IntArray(width * height)

    fun contains(x: Int, y: Int) = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Int = if (contains(x, y)) pixels[y * width + x] else TRANSPARENT

    operator fun set(x: Int, y: Int, color: Int) {
        if (contains(x, y)) pixels[y * width + x] = color
    }

    fun stamp(mask: Mask, x0: Int, y0: Int, color: (x: Int, y: Int) -> Int) {
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            if (mask[x, y]) this[x0 + x, y0 + y] = color(x0 + x, y0 + y)
        }
    }

    // Nearest-neighbour only: any smoothing would blur the pixel art the resource pack relies on.
    fun toImage(scale: Int = 1): BufferedImage {
        val image = BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            image.setRGB(x, y, pixels[(y / scale) * width + x / scale])
        }
        return image
    }
}

class Mask(val width: Int, val height: Int) {
    private val bits = BooleanArray(width * height)

    operator fun get(x: Int, y: Int) = x in 0 until width && y in 0 until height && bits[y * width + x]

    operator fun set(x: Int, y: Int, on: Boolean) {
        if (x in 0 until width && y in 0 until height) bits[y * width + x] = on
    }
}
