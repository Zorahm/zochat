package zorahm.zochat.prefixgen

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

private const val GRID_SCALE = 4

private data class ChatLine(val rank: String, val variant: Variant, val player: String, val message: String)

private val chatLines = listOf(
    ChatLine("owner", Variant.RU_ICON, "Zorahm", "Всем привет! Вайп в субботу"),
    ChatLine("admin", Variant.EN, "Kriodex", "Restart in 5 minutes"),
    ChatLine("mod", Variant.RU_ICON, "Lumi_", "Без капса, пожалуйста"),
    ChatLine("helper", Variant.EN_ICON, "Nyako", "type /help for commands"),
    ChatLine("dev", Variant.RU, "byteFox", "починил /msg, перезайдите"),
    ChatLine("builder", Variant.RU_ICON, "Kirpich", "спавн почти готов!"),
    ChatLine("media", Variant.EN_ICON, "StreamCat", "live on twitch rn"),
    ChatLine("legend", Variant.RU_ICON, "DarkLord", "кто на арену?"),
    ChatLine("premium", Variant.EN, "Aurora", "gg wp"),
    ChatLine("vip", Variant.RU_ICON, "Pchelka", "ура, купила вип"),
    ChatLine("player", Variant.RU, "Steve", "а как сюда попасть?"),
)

private fun dataUri(canvas: Canvas): String {
    val bytes = ByteArrayOutputStream().also { ImageIO.write(canvas.toImage(), "png", it) }.toByteArray()
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// Chat badges are sized in CSS from --s (the simulated GUI scale) so the scene can shrink on phones.
private fun chatImg(canvas: Canvas, alt: String) =
    """<img class="px" src="${dataUri(canvas)}" style="--w:${canvas.width}" alt="${esc(alt)}">"""

private fun gridImg(canvas: Canvas, alt: String) =
    """<img class="px" src="${dataUri(canvas)}" width="${canvas.width * GRID_SCALE}" height="${canvas.height * GRID_SCALE}" alt="${esc(alt)}">"""

private const val HEAD = """<title>zoChat Prefix Styles</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@500;700&family=Press+Start+2P&family=Rubik:wght@400;500&display=swap">
<style>
/* Layout: one column; each style is a live chat scene followed by its full badge matrix. Dark only: badges live on the dark chat. */
:root {
  color-scheme: dark;
  --bg: #111318;
  --surface: #181b22;
  --line: #272b35;
  --fg: #e7e9f0;
  --muted: #8d92a3;
  --accent: #f0c24b;
  --chat-bg: rgba(0, 0, 0, .5);
  --chat-fg: #ffffff;
  --chat-dim: #aaaaaa;
  --chat-shadow: #3f3f3f;
  --sky: #6f9df0;
  --sky-low: #a9c6f6;
  --grass: #5f9b45;
  --dirt: #7d5a3a;
  --font-display: "Pixelify Sans", "Press Start 2P", system-ui, sans-serif;
  --font-body: "Rubik", system-ui, "Segoe UI", sans-serif;
  --font-mc: "Press Start 2P", ui-monospace, "Consolas", monospace;
  --s: 3;
}
body { background: var(--bg); color: var(--fg); font: 15px/1.55 var(--font-body); }
main { max-width: 1180px; margin: 0 auto; display: grid; gap: 56px;
       padding-block: 28px 72px;
       padding-left: max(16px, env(safe-area-inset-left, 0px));
       padding-right: max(16px, env(safe-area-inset-right, 0px)); }
header, section, .head { display: grid; min-width: 0; }
header { gap: 14px; }
section { gap: 16px; scroll-margin-top: 16px; }
.head { gap: 4px; }
h1 { font: 700 34px/1.1 var(--font-display); margin: 0; text-wrap: balance; }
h2 { font: 700 26px/1.2 var(--font-display); margin: 0; text-wrap: balance; }
p { margin: 0; color: var(--muted); max-width: 65ch; }
.spec { display: flex; flex-wrap: wrap; gap: 8px; margin: 0; padding: 0; list-style: none; }
.spec li { font-size: 12px; letter-spacing: .05em; text-transform: uppercase; color: var(--muted);
           border: 1px solid var(--line); border-radius: 4px; padding: 3px 8px; font-variant-numeric: tabular-nums; }
.spec b { color: var(--accent); font-weight: 500; }
nav { display: flex; flex-wrap: wrap; gap: 8px; }
nav a { color: var(--fg); text-decoration: none; border: 1px solid var(--line); border-radius: 4px;
        padding: 6px 14px; font: 500 16px/1.2 var(--font-display); }
nav a:hover, nav a:focus-visible { border-color: var(--accent); color: var(--accent); outline: none; }
.world { background: linear-gradient(var(--sky), var(--sky-low) 74%, var(--grass) 74%, var(--grass) 79%, var(--dirt) 79%);
         border-radius: 6px; padding: 20px 16px; overflow-x: auto; }
.chat { display: inline-flex; flex-direction: column; }
.line { display: flex; align-items: center; gap: calc(var(--s) * 4px); height: calc(var(--s) * 9px);
        padding: 0 calc(var(--s) * 2px); background: var(--chat-bg); color: var(--chat-fg); white-space: nowrap;
        font: calc(var(--s) * 8px)/1 var(--font-mc);
        text-shadow: calc(var(--s) * 1px) calc(var(--s) * 1px) 0 var(--chat-shadow); }
.line i { font-style: normal; color: var(--chat-dim); }
.line img { width: calc(var(--w) * var(--s) * 1px); height: calc(var(--s) * 9px); }
img.px { image-rendering: pixelated; display: block; max-width: none; flex-shrink: 0; }
.matrix { overflow-x: auto; background: var(--surface); border: 1px solid var(--line); border-radius: 6px; padding: 16px; }
table { border-collapse: collapse; }
th { text-align: left; font: 500 12px/1 var(--font-body); letter-spacing: .06em; text-transform: uppercase;
     color: var(--muted); padding: 0 32px 12px 0; white-space: nowrap; }
td { padding: 6px 32px 6px 0; }
@media (max-width: 640px) { :root { --s: 2; } h1 { font-size: 27px; } }
</style>
"""

fun writePreview(rendered: Rendered, file: File) {
    val byId = ranks.associateBy { it.id }
    val html = buildString {
        append(HEAD)
        append("<main><header><h1>zoChat Prefix Styles</h1>")
        append(
            "<p>Four badge styles for rank prefixes, each in English and Russian, with and without an icon. " +
                "They ship as glyphs in the server resource pack, so they stay pixel-exact at every GUI scale.</p>",
        )
        append("<ul class=\"spec\">")
        append("<li>Glyph <b>9 px</b> · one chat line</li>")
        append("<li>Text <b>5 px</b> caps · Latin + Cyrillic</li>")
        append("<li>Icons <b>7×7</b></li>")
        append("<li>Chat at GUI scale <b>3</b></li>")
        append("<li>Matrix at <b>${GRID_SCALE}×</b></li>")
        append("</ul><nav>")
        for (style in rendered.keys) append("<a href=\"#${style.id}\">${style.title}</a>")
        append("</nav></header>")

        for ((style, byVariant) in rendered) {
            append("<section id=\"${style.id}\"><div class=\"head\"><h2>${style.title}</h2><p>${esc(style.summary)}</p></div>")
            append("<div class=\"world\"><div class=\"chat\">")
            for (line in chatLines) {
                val rank = byId.getValue(line.rank)
                val canvas = byVariant.getValue(line.variant).getValue(rank)
                append("<div class=\"line\">${chatImg(canvas, rank.badge(line.variant).text)}")
                append("<span>${esc(line.player)}<i>:</i> ${esc(line.message)}</span></div>")
            }
            append("</div></div><div class=\"matrix\"><table><tr>")
            for (variant in Variant.entries) append("<th>${variant.title}</th>")
            append("</tr>")
            for (rank in ranks) {
                append("<tr>")
                for (variant in Variant.entries) {
                    append("<td>${gridImg(byVariant.getValue(variant).getValue(rank), rank.badge(variant).text)}</td>")
                }
                append("</tr>")
            }
            append("</table></div></section>")
        }
        append("</main>")
    }
    file.writeText(html)
}
