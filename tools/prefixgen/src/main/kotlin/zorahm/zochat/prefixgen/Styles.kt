package zorahm.zochat.prefixgen

import kotlin.math.abs

// One chat line is 9px: a taller glyph would paint over the neighbouring lines' backgrounds.
const val BADGE_HEIGHT = 9
private const val TEXT_Y = 2
private const val ICON_Y = 1
private const val ICON_SLOT = Icon.SIZE + 2

data class RankColors(val base: Int, val accent: Int)

data class Badge(val text: String, val colors: RankColors, val icon: Icon? = null)

interface Style {
    val id: String
    val title: String
    val summary: String
    fun render(badge: Badge): Canvas
}

/** Glossy rounded plate: vertical gradient, bright top line, dark outline. */
object PlateStyle : Style {
    override val id = "plate"
    override val title = "Plate"
    override val summary = "Glossy rounded plate in the rank colour with a dark outline. The full-colour icon sits in a dark well, so a gold crown stays visible on a gold plate."

    override fun render(badge: Badge): Canvas {
        val base = badge.colors.base
        val text = PixelFont.render(badge.text)
        val iconWidth = if (badge.icon != null) ICON_SLOT + 1 else 0
        val width = 1 + iconWidth + 2 + text.width + 2 + 1
        val canvas = Canvas(width, BADGE_HEIGHT)

        val outline = base.darken(0.55)
        val top = base.lighten(0.25)
        for (y in 0 until BADGE_HEIGHT) for (x in 0 until width) {
            val edgeX = x == 0 || x == width - 1
            val edgeY = y == 0 || y == BADGE_HEIGHT - 1
            canvas[x, y] = when {
                edgeX && edgeY -> TRANSPARENT
                edgeX || edgeY -> outline
                y == 1 -> base.lighten(0.5)
                y == BADGE_HEIGHT - 2 -> base.darken(0.15)
                else -> mix(top, base, (y - 2) / 4.0)
            }
        }

        badge.icon?.let { icon ->
            // Full-colour icons get a dark well: a gold crown on a gold plate would disappear.
            val well = base.darken(0.5)
            for (y in 1..BADGE_HEIGHT - 2) {
                for (x in 1..ICON_SLOT) canvas[x, y] = if (y == 1) well.lighten(0.12) else well
                canvas[ICON_SLOT + 1, y] = outline
            }
            icon.draw(canvas, 2, ICON_Y)
        }

        val (ink, shadow) = inkOn(mix(top, base, 0.5), base)
        val textX = 1 + iconWidth + 2
        canvas.stamp(text, textX, TEXT_Y + 1) { _, _ -> shadow }
        canvas.stamp(text, textX, TEXT_Y) { _, _ -> ink }
        return canvas
    }

    // White 5px text drowns on gold/yellow plates, so bright plates get dark ink with a light emboss.
    private fun inkOn(background: Int, base: Int): Pair<Int, Int> =
        if (background.luminance() > 0.66) base.darken(0.72) to base.lighten(0.55)
        else WHITE to base.darken(0.5)
}

/** Dark glass with a bright 1px tube and tinted text — a neon sign on the dark chat background. */
object NeonStyle : Style {
    override val id = "neon"
    override val title = "Neon"
    override val summary = "Dark glass with a bright 1px tube. Text and icon are tinted in the rank colour, made for the dark chat background."
    private val glass = rgb(0x0E0E14)

    override fun render(badge: Badge): Canvas {
        val base = badge.colors.base
        val text = PixelFont.render(badge.text)
        val iconWidth = if (badge.icon != null) ICON_SLOT + 1 else 0
        val width = 1 + iconWidth + 2 + text.width + 2 + 1
        val canvas = Canvas(width, BADGE_HEIGHT)

        val tube = base.lighten(0.2)
        val fill = mix(glass, base, 0.16).withAlpha(235)
        val halo = mix(glass, base, 0.4).withAlpha(240)
        for (y in 0 until BADGE_HEIGHT) for (x in 0 until width) {
            val edgeX = x == 0 || x == width - 1
            val edgeY = y == 0 || y == BADGE_HEIGHT - 1
            val innerX = x == 1 || x == width - 2
            val innerY = y == 1 || y == BADGE_HEIGHT - 2
            canvas[x, y] = when {
                edgeX && edgeY -> TRANSPARENT
                edgeX || edgeY -> tube
                innerX || innerY -> halo
                else -> fill
            }
        }

        badge.icon?.let { icon ->
            for (y in TEXT_Y until TEXT_Y + PixelFont.HEIGHT) canvas[ICON_SLOT + 1, y] = halo
            icon.drawTinted(canvas, 2, ICON_Y, base.darken(0.15), base.lighten(0.75))
        }

        val textX = 1 + iconWidth + 2
        canvas.stamp(text, textX, TEXT_Y + 1) { _, _ -> mix(glass, base, 0.65) }
        canvas.stamp(text, textX, TEXT_Y) { _, _ -> base.lighten(0.6) }
        return canvas
    }
}

/** Vanilla GUI button: square black frame, light top-left / dark bottom-right bevel, block-like speckle. */
object CraftStyle : Style {
    override val id = "craft"
    override val title = "Craft"
    override val summary = "A vanilla GUI button: black frame, bevel, block-texture speckle and a diagonal text shadow. The icon sits in a grey inventory slot."
    private val slot = rgb(0x8B8B8B)

    override fun render(badge: Badge): Canvas {
        val base = badge.colors.base
        val text = PixelFont.render(badge.text)
        val buttonX = 1 + if (badge.icon != null) ICON_SLOT + 1 else 0
        // bevel + 1px gap on each side, plus one column for the vanilla-style diagonal text shadow
        val width = buttonX + text.width + 6
        val canvas = Canvas(width, BADGE_HEIGHT)

        val light = base.lighten(0.38)
        val dark = base.darken(0.38)
        val bottom = BADGE_HEIGHT - 2
        for (y in 0 until BADGE_HEIGHT) for (x in 0 until width) {
            canvas[x, y] = when {
                x == 0 || y == 0 || x == width - 1 || y == BADGE_HEIGHT - 1 -> BLACK
                x < buttonX - 1 -> slot
                x == buttonX - 1 -> BLACK
                (x == buttonX && y == bottom) || (x == width - 2 && y == 1) -> base
                x == buttonX || y == 1 -> light
                x == width - 2 || y == bottom -> dark
                else -> speckle(base, x, y)
            }
        }

        badge.icon?.draw(canvas, 2, ICON_Y)

        val textX = buttonX + 2
        canvas.stamp(text, textX + 1, TEXT_Y + 1) { _, _ -> base.darken(0.62) }
        canvas.stamp(text, textX, TEXT_Y) { _, _ -> WHITE }
        return canvas
    }

    // Hashed rather than random so re-running the generator never changes a published texture.
    private fun speckle(base: Int, x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return when ((h xor (h ushr 16)) and 7) {
            0 -> base.lighten(0.08)
            1 -> base.darken(0.08)
            else -> base
        }
    }
}

/** Ribbon with a swallowtail notch and an arrow tip, two-colour horizontal gradient. */
object RibbonStyle : Style {
    override val id = "ribbon"
    override val title = "Ribbon"
    override val summary = "Swallowtail ribbon with an arrow tip and a two-colour gradient per rank. The icon becomes a silhouette in the text colour."

    private fun notch(y: Int) = when (y) {
        3, 5 -> 1
        4 -> 2
        else -> 0
    }

    private fun tip(y: Int) = abs(y - BADGE_HEIGHT / 2)

    override fun render(badge: Badge): Canvas {
        val (base, accent) = badge.colors
        val text = PixelFont.render(badge.text)
        val textX = 4 + if (badge.icon != null) Icon.SIZE + 2 else 0
        // 4 columns on the right keep the text clear of the arrow's slanted edge
        val width = textX + text.width + 4
        val canvas = Canvas(width, BADGE_HEIGHT)

        fun inside(x: Int, y: Int) = y in 0 until BADGE_HEIGHT && x >= notch(y) && x <= width - 1 - tip(y)
        fun isEdge(x: Int, y: Int) = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
        fun tone(x: Int) = mix(base, accent, x / (width - 1.0))

        // No diagonal glint: at 5px any light streak behind the text merges into the letters (P read as B).
        for (y in 0 until BADGE_HEIGHT) for (x in 0 until width) {
            if (!inside(x, y)) continue
            val t = tone(x)
            canvas[x, y] = when {
                isEdge(x, y) -> t.darken(0.55)
                y == 1 -> t.lighten(0.4)
                y == BADGE_HEIGHT - 2 -> t.darken(0.18)
                else -> t.lighten(0.04 * (BADGE_HEIGHT - 2 - y))
            }
        }

        val bright = mix(base, accent, 0.5).luminance() > 0.66
        val ink = if (bright) base.darken(0.72) else WHITE
        fun shadow(x: Int) = if (bright) tone(x).lighten(0.5) else tone(x).darken(0.5)

        badge.icon?.let { icon ->
            val mask = icon.mask()
            for (y in 0 until Icon.SIZE) for (x in 0 until Icon.SIZE) {
                val sx = 4 + x
                val sy = ICON_Y + y + 1
                if (mask[x, y] && inside(sx, sy) && !isEdge(sx, sy)) canvas[sx, sy] = shadow(sx)
            }
            icon.drawTinted(canvas, 4, ICON_Y, mix(ink, base, 0.35), ink)
        }

        canvas.stamp(text, textX, TEXT_Y + 1) { x, _ -> shadow(x) }
        canvas.stamp(text, textX, TEXT_Y) { _, _ -> ink }
        return canvas
    }
}
