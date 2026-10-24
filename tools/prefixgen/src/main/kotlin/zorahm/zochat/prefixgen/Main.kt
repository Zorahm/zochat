package zorahm.zochat.prefixgen

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** [slot] is the rank's permanent place in the font — see [codepoint]. Never reuse or renumber one. */
data class Rank(val id: String, val slot: Int, val en: String, val ru: String, val colors: RankColors, val icon: Icon)

/** [offset] is the variant's permanent position inside a rank's block of glyphs — see [codepoint]. */
enum class Variant(val dir: String, val title: String, val russian: Boolean, val withIcon: Boolean, val offset: Int) {
    EN("en", "EN", false, false, 0),
    EN_ICON("en-icon", "EN + ICON", false, true, 1),
    RU("ru", "RU", true, false, 2),
    RU_ICON("ru-icon", "RU + ICON", true, true, 3),
}

private const val FIRST_CODEPOINT = 0xE000
private const val GLYPHS_PER_RANK = 4

// Private Use Area, one block of four per rank. Derived from the fixed slot and offset rather than
// list positions, so adding or reordering ranks never moves a badge that zoChat and deployed packs use.
fun Rank.codepoint(variant: Variant): Int = FIRST_CODEPOINT + slot * GLYPHS_PER_RANK + variant.offset

private fun colors(base: String, accent: String) = RankColors(rgb(base), rgb(accent))

val ranks = listOf(
    Rank("owner", 0, "OWNER", "ВЛАДЕЛЕЦ", colors("#FFC400", "#FF8F00"), Icons.CROWN),
    Rank("admin", 1, "ADMIN", "АДМИН", colors("#E53935", "#FF7043"), Icons.SHIELD),
    Rank("mod", 2, "MOD", "МОДЕР", colors("#1E88E5", "#4FC3F7"), Icons.SWORD),
    Rank("helper", 3, "HELPER", "ХЕЛПЕР", colors("#00A88F", "#5ED6B4"), Icons.HEART),
    Rank("dev", 4, "DEV", "РАЗРАБ", colors("#7E57C2", "#B388FF"), Icons.TERMINAL),
    Rank("builder", 5, "BUILDER", "СТРОИТЕЛЬ", colors("#B26A35", "#E8A45C"), Icons.PICKAXE),
    Rank("media", 6, "MEDIA", "МЕДИА", colors("#EC407A", "#FF8A80"), Icons.PLAY),
    Rank("legend", 7, "LEGEND", "ЛЕГЕНДА", colors("#AA00FF", "#FF6D00"), Icons.FLAME),
    Rank("premium", 8, "PREMIUM", "ПРЕМИУМ", colors("#00B8D4", "#7C4DFF"), Icons.GEM),
    Rank("vip", 9, "VIP", "ВИП", colors("#43A047", "#C0CA33"), Icons.STAR),
    Rank("player", 10, "PLAYER", "ИГРОК", colors("#607D8B", "#90A4AE"), Icons.GRASS),
    Rank("console", 11, "CONSOLE", "КОНСОЛЬ", colors("#CFD8DC", "#69F0AE"), Icons.MONITOR),
)

val styles: List<Style> = listOf(PlateStyle, NeonStyle, CraftStyle, RibbonStyle)

fun Rank.badge(variant: Variant) = Badge(if (variant.russian) ru else en, colors, icon.takeIf { variant.withIcon })

typealias Rendered = Map<Style, Map<Variant, Map<Rank, Canvas>>>

/**
 * --packs    folder for the resource packs: zochat-badges-<style>/ and its .zip, one per style.
 *            They are interchangeable (same font, same characters) — serve whichever style you like.
 * --table    badges.yml for zoChat (character table + labels)
 * --preview  sheets and an HTML preview of every style
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key.removePrefix("--") to value }

    val rendered: Rendered = styles.associateWith { style ->
        Variant.entries.associateWith { variant -> ranks.associateWith { rank -> style.render(rank.badge(variant)) } }
    }

    options["preview"]?.let { writePreviews(rendered, File(it)) }
    options["packs"]?.let { path ->
        for ((style, byVariant) in rendered) {
            val zip = ResourcePack.write(File(path, ResourcePack.name(style)), byVariant)
            // server.properties takes the SHA-1 next to the pack URL so clients can cache it.
            println("Resource pack: ${zip.absolutePath} (resource-pack-sha1=${ResourcePack.sha1(zip)})")
        }
    }
    options["table"]?.let { path ->
        File(path).writeText(BadgeTable.yaml(ranks))
        println("Badge table: $path")
    }
}

private fun writePreviews(rendered: Rendered, out: File) {
    for ((style, byVariant) in rendered) {
        for ((variant, byRank) in byVariant) for ((rank, canvas) in byRank) {
            val file = File(out, "${style.id}/${variant.dir}/${rank.id}.png")
            file.parentFile.mkdirs()
            ImageIO.write(canvas.toImage(), "png", file)
        }
        writeSheet(style, byVariant, File(out, "sheet-${style.id}.png"))
    }
    writePreview(rendered, File(out, "preview.html"))
    println("Preview: ${styles.size * Variant.entries.size * ranks.size} badges in ${out.absolutePath}")
}

private const val SHEET_UNIT = 4

private fun writeSheet(style: Style, byVariant: Map<Variant, Map<Rank, Canvas>>, file: File) {
    val margin = 6
    val gap = 8
    val rowStep = BADGE_HEIGHT + 4
    val headerY = margin + 2 * PixelFont.HEIGHT + 5
    val top = headerY + PixelFont.HEIGHT + 4
    val columnWidths = Variant.entries.map { v -> byVariant.getValue(v).values.maxOf { it.width } }
    val width = margin * 2 + columnWidths.sum() + gap * (columnWidths.size - 1)
    val height = top + ranks.size * rowStep + margin

    val image = BufferedImage(width * SHEET_UNIT, height * SHEET_UNIT, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = Color(0x1A1B21)
    g.fillRect(0, 0, image.width, image.height)
    fun put(canvas: Canvas, x: Int, y: Int, scale: Int = 1) {
        g.drawImage(canvas.toImage(SHEET_UNIT * scale), x * SHEET_UNIT, y * SHEET_UNIT, null)
    }

    put(label(style.id.uppercase(), WHITE), margin, margin, 2)
    var x = margin
    Variant.entries.forEachIndexed { i, variant ->
        put(label(variant.title, rgb(0x8A8C99)), x, headerY)
        ranks.forEachIndexed { row, rank -> put(byVariant.getValue(variant).getValue(rank), x, top + row * rowStep) }
        x += columnWidths[i] + gap
    }
    g.dispose()
    ImageIO.write(image, "png", file)
}

private fun label(text: String, color: Int): Canvas {
    val mask = PixelFont.render(text)
    return Canvas(mask.width, mask.height).also { it.stamp(mask, 0, 0) { _, _ -> color } }
}
