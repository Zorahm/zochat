package zorahm.zochat.prefixgen

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** The server resource pack: one bitmap glyph per badge in the zochat:badges font. */
object ResourcePack {
    const val NAMESPACE = "zochat"
    const val FONT = "$NAMESPACE:badges"

    // Vanilla glyphs are 8 px tall with ascent 7, so they end 1 px below the baseline. Ascent 8 puts
    // the 9 px badge's bottom on that same row: it fills the 9 px chat line without spilling over.
    private const val ASCENT = 8

    // Minecraft 1.21.9-1.21.10; from 1.21.9 on min_format/max_format replace pack_format.
    private const val PACK_FORMAT = 69

    private fun texture(rank: Rank, variant: Variant) = "font/badges/${rank.id}-${variant.dir}.png"

    /** Folder and zip name of a style's pack; all styles share the same font and characters. */
    fun name(style: Style) = "zochat-badges-${style.id}"

    fun mcmeta(): String = """
        |{
        |  "pack": {
        |    "description": "zoChat rank badges",
        |    "min_format": $PACK_FORMAT,
        |    "max_format": $PACK_FORMAT
        |  }
        |}
        |""".trimMargin()

    fun fontJson(ranks: List<Rank>): String {
        val providers = ranks.flatMap { rank ->
            Variant.entries.map { variant ->
                """    {"type": "bitmap", "file": "$NAMESPACE:${texture(rank, variant)}", "ascent": $ASCENT, """ +
                    """"height": $BADGE_HEIGHT, "chars": ["\u${"%04x".format(rank.codepoint(variant))}"]}"""
            }
        }
        return "{\n  \"providers\": [\n${providers.joinToString(",\n")}\n  ]\n}\n"
    }

    /** Writes the pack folder and a zip of it next to it; returns the zip. */
    fun write(dir: File, byVariant: Map<Variant, Map<Rank, Canvas>>): File {
        dir.deleteRecursively()
        File(dir, "pack.mcmeta").also { it.parentFile.mkdirs() }.writeText(mcmeta())
        File(dir, "assets/$NAMESPACE/font/badges.json").also { it.parentFile.mkdirs() }.writeText(fontJson(ranks))
        for ((variant, byRank) in byVariant) for ((rank, canvas) in byRank) {
            val png = File(dir, "assets/$NAMESPACE/textures/${texture(rank, variant)}")
            png.parentFile.mkdirs()
            ImageIO.write(canvas.toImage(), "png", png)
        }
        val zip = File(dir.parentFile, "${dir.name}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                // Zip entries need forward slashes; File.relativeTo uses '\' on Windows.
                out.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    fun sha1(file: File): String =
        MessageDigest.getInstance("SHA-1").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
