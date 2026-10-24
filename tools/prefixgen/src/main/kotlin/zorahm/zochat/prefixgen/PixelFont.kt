package zorahm.zochat.prefixgen

// Caps-only 5px font. A badge is 9px tall (one chat line), which leaves exactly 1px border + 1px padding
// above and below the text — the vanilla 7px font would not fit inside a frame.
object PixelFont {
    const val HEIGHT = 5
    private const val SPACING = 1
    private const val SPACE_WIDTH = 2

    private val art: Map<Char, String> = mapOf(
        'A' to ".##.|#..#|####|#..#|#..#",
        'B' to "###.|#..#|###.|#..#|###.",
        'C' to ".###|#...|#...|#...|.###",
        'D' to "###.|#..#|#..#|#..#|###.",
        'E' to "####|#...|###.|#...|####",
        'F' to "####|#...|###.|#...|#...",
        'G' to ".###|#...|#.##|#..#|.###",
        'H' to "#..#|#..#|####|#..#|#..#",
        'I' to "###|.#.|.#.|.#.|###",
        'J' to "...#|...#|...#|#..#|.##.",
        'K' to "#..#|#.#.|##..|#.#.|#..#",
        'L' to "#...|#...|#...|#...|####",
        'M' to "#...#|##.##|#.#.#|#...#|#...#",
        'N' to "#..#|##.#|#.##|#..#|#..#",
        'O' to ".##.|#..#|#..#|#..#|.##.",
        'P' to "###.|#..#|###.|#...|#...",
        'Q' to ".##.|#..#|#..#|#.#.|.#.#",
        'R' to "###.|#..#|###.|#.#.|#..#",
        'S' to ".###|#...|.##.|...#|###.",
        'T' to "###|.#.|.#.|.#.|.#.",
        'U' to "#..#|#..#|#..#|#..#|.##.",
        'V' to "#...#|#...#|#...#|.#.#.|..#..",
        'W' to "#...#|#...#|#.#.#|##.##|#...#",
        'X' to "#..#|#..#|.##.|#..#|#..#",
        'Y' to "#...#|.#.#.|..#..|..#..|..#..",
        'Z' to "####|...#|.##.|#...|####",

        'Б' to "####|#...|###.|#..#|###.",
        'Г' to "####|#...|#...|#...|#...",
        // 5 wide on purpose: the base must stick out past the body, or at 5px Д reads as A
        'Д' to ".###.|.#.#.|.#.#.|#####|#...#",
        'Ж' to "#.#.#|#.#.#|.###.|#.#.#|#.#.#",
        'З' to "###.|...#|.##.|...#|###.",
        'И' to "#..#|#.##|##.#|#..#|#..#",
        'Й' to ".##.|#..#|#.##|##.#|#..#",
        'Л' to ".###|.#.#|.#.#|.#.#|#..#",
        'П' to "####|#..#|#..#|#..#|#..#",
        'У' to "#..#|#..#|.###|...#|.##.",
        'Ф' to ".###.|#.#.#|#.#.#|.###.|..#..",
        'Ц' to "#..#.|#..#.|#..#.|#####|....#",
        'Ч' to "#..#|#..#|.###|...#|...#",
        'Ш' to "#.#.#|#.#.#|#.#.#|#.#.#|#####",
        'Щ' to "#.#.#.|#.#.#.|#.#.#.|######|.....#",
        'Ъ' to "##..|.#..|.###|.#.#|.###",
        'Ы' to "#...#|#...#|###.#|#.#.#|###.#",
        'Ь' to "#...|#...|###.|#..#|###.",
        'Э' to "###.|...#|.###|...#|###.",
        'Ю' to "#..#.|#.#.#|###.#|#.#.#|#..#.",
        'Я' to ".###|#..#|.###|.#.#|#..#",

        '0' to "###|#.#|#.#|#.#|###",
        '1' to ".#.|##.|.#.|.#.|###",
        '2' to "###|..#|###|#..|###",
        '3' to "###|..#|.##|..#|###",
        '4' to "#.#|#.#|###|..#|..#",
        '5' to "###|#..|###|..#|###",
        '6' to "###|#..|###|#.#|###",
        '7' to "###|..#|.#.|.#.|.#.",
        '8' to "###|#.#|###|#.#|###",
        '9' to "###|#.#|###|..#|###",

        '+' to "...|.#.|###|.#.|...",
        '-' to "...|...|###|...|...",
        '!' to "#|#|#|.|#",
        '.' to ".|.|.|.|#",
        '?' to "###.|...#|.##.|....|.#..",
    )

    // Cyrillic letters that are drawn exactly like a Latin one; Ё has no room for its dots at 5px.
    private val lookalikes = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'Ё' to 'E', 'К' to 'K', 'М' to 'M',
        'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X',
    )

    private val glyphs: Map<Char, List<String>> = art.mapValues { (char, rows) ->
        rows.split('|').also { lines ->
            require(lines.size == HEIGHT && lines.all { it.length == lines[0].length }) { "Glyph '$char' is malformed" }
        }
    }

    // Fails loudly: a silently skipped letter would ship a broken prefix to every player.
    private fun glyph(c: Char): List<String> =
        glyphs[lookalikes[c] ?: c] ?: throw IllegalArgumentException("No pixel glyph for '$c'")

    private fun advance(c: Char) = (if (c == ' ') SPACE_WIDTH else glyph(c)[0].length) + SPACING

    fun render(text: String): Mask {
        require(text.isNotBlank()) { "Badge text is empty" }
        val chars = text.uppercase()
        val mask = Mask(chars.sumOf { advance(it) } - SPACING, HEIGHT)
        var x = 0
        for (c in chars) {
            if (c != ' ') {
                val rows = glyph(c)
                for (y in 0 until HEIGHT) for (gx in rows[y].indices) {
                    if (rows[y][gx] == '#') mask[x + gx, y] = true
                }
            }
            x += advance(c)
        }
        return mask
    }
}
