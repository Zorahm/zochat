package zorahm.zochat.badge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

/**
 * The `<badge:id>` / `<badge:id:variant>` MiniMessage tag: a rank badge from the zoChat resource
 * pack. Variants: en, en-icon, ru, ru-icon; without one, [Settings] decide.
 */
class Badges(private val table: BadgeTable, private val settings: () -> Settings) {

    data class Settings(val enabled: Boolean, val russian: Boolean, val icons: Boolean)

    /** For players: the glyph from the resource pack, or the text label while badges are off. */
    val forPlayers: TagResolver = resolver(console = false)

    /** For the console, which never has the resource pack — a glyph would print as a box there. */
    val forConsole: TagResolver = resolver(console = true)

    // Bitmap glyphs are multiplied by the text colour and get chat's drop shadow, and bold would
    // smear them — so the badge resets all of it to look exactly like its PNG wherever it lands.
    private val glyphStyle: Style = Style.style { style ->
        style.font(table.font)
        style.color(NamedTextColor.WHITE)
        style.shadowColor(ShadowColor.none())
        TextDecoration.entries.forEach { style.decoration(it, false) }
    }

    private fun resolver(console: Boolean): TagResolver = TagResolver.resolver("badge") { args, context ->
        val id = args.popOr("<badge> needs a badge id, e.g. <badge:admin>").value()
        val entry = table[id] ?: throw context.newException("Unknown badge '$id'", args)
        val current = settings()
        val variant = if (args.hasNext()) args.pop().value().lowercase() else null
        if (variant != null && variant !in VARIANTS) {
            throw context.newException("Unknown badge variant '$variant', expected one of $VARIANTS", args)
        }
        val russian = variant?.startsWith("ru") ?: current.russian
        val icon = variant?.endsWith("-icon") ?: current.icons
        val glyph = entry.glyph(russian, icon)
        val component = if (console || !current.enabled || glyph == null) {
            Component.text("[" + (if (russian) entry.labelRu else entry.labelEn) + "]")
        } else {
            Component.text(glyph.toString(), glyphStyle)
        }
        Tag.selfClosingInserting(component)
    }

    private companion object {
        val VARIANTS = listOf("en", "en-icon", "ru", "ru-icon")
    }
}
