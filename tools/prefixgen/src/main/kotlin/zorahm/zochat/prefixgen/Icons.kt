package zorahm.zochat.prefixgen

class Icon(val id: String, art: String, private val palette: Map<Char, Int>) {
    private val rows = art.split('|')

    init {
        require(rows.size == SIZE && rows.all { it.length == SIZE }) { "Icon '$id' must be ${SIZE}x$SIZE" }
    }

    fun color(x: Int, y: Int): Int = palette[rows[y][x]] ?: TRANSPARENT

    fun mask(): Mask = Mask(SIZE, SIZE).also { mask ->
        for (y in 0 until SIZE) for (x in 0 until SIZE) mask[x, y] = color(x, y) != TRANSPARENT
    }

    fun draw(canvas: Canvas, x0: Int, y0: Int) {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val c = color(x, y)
            if (c != TRANSPARENT) canvas[x0 + x, y0 + y] = c
        }
    }

    // Keeps the icon's own shading but remaps it onto the badge's colours, so one drawing serves every style.
    fun drawTinted(canvas: Canvas, x0: Int, y0: Int, dark: Int, light: Int) {
        val lums = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> color(x, y) } }
            .filter { it != TRANSPARENT }
            .map { it.luminance() }
        val low = lums.min()
        val span = (lums.max() - low).coerceAtLeast(1e-6)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val c = color(x, y)
            if (c != TRANSPARENT) canvas[x0 + x, y0 + y] = mix(dark, light, (c.luminance() - low) / span)
        }
    }

    companion object {
        const val SIZE = 7
    }
}

object Icons {
    val CROWN = Icon(
        "crown",
        ".......|H..H..H|HM.H.MD|HHMMMMD|HMMMMMD|MAMBMAD|DDDDDDD",
        mapOf('H' to rgb("#FFF59D"), 'M' to rgb("#FFC107"), 'D' to rgb("#C77C00"), 'A' to rgb("#E53935"), 'B' to rgb("#42A5F5")),
    )
    val SHIELD = Icon(
        "shield",
        "DDDDDDD|DHHAMMD|DHAAAMD|DHHAMMD|.DHAMD.|..DMD..|...D...",
        mapOf('D' to rgb("#37474F"), 'H' to rgb("#ECEFF1"), 'M' to rgb("#B0BEC5"), 'A' to rgb("#E53935")),
    )
    val SWORD = Icon(
        "sword",
        ".....HH|....HMD|...HMD.|.GHMD..|..G....|.B.G...|B......",
        mapOf('H' to rgb("#FAFAFA"), 'M' to rgb("#BDBDBD"), 'D' to rgb("#757575"), 'G' to rgb("#5D4037"), 'B' to rgb("#8D6E63")),
    )
    val HEART = Icon(
        "heart",
        ".......|.RR.RR.|RWRRRRR|RRRRRRD|.RRRRD.|..RRD..|...D...",
        mapOf('R' to rgb("#F44336"), 'W' to rgb("#FFCDD2"), 'D' to rgb("#B71C1C")),
    )
    val TERMINAL = Icon(
        "terminal",
        ".......|GG.....|.GG....|..GG...|.GG....|GG..WWW|.......",
        mapOf('G' to rgb("#69F0AE"), 'W' to rgb("#FFFFFF")),
    )
    val PICKAXE = Icon(
        "pickaxe",
        "..HHH..|....HM.|....BMD|...B..D|..B...D|.B.....|b......",
        mapOf('H' to rgb("#B2EBF2"), 'M' to rgb("#4DD0E1"), 'D' to rgb("#00838F"), 'B' to rgb("#8D6E63"), 'b' to rgb("#5D4037")),
    )
    val PLAY = Icon(
        "play",
        ".AAAAA.|AAWAAAa|AAWWAAa|AAWWWAa|AAWWAAa|AAWAAAa|.aaaaa.",
        mapOf('A' to rgb("#FF1744"), 'a' to rgb("#C4001D"), 'W' to rgb("#FFFFFF")),
    )
    val FLAME = Icon(
        "flame",
        "...R...|..RR.R.|.RRORR.|.ROOOR.|RROYORR|ROYYYOR|.ROYOR.",
        mapOf('R' to rgb("#E64A19"), 'O' to rgb("#FF9800"), 'Y' to rgb("#FFEB3B")),
    )
    val GEM = Icon(
        "gem",
        ".......|.WHMMD.|WHHMMDD|.HMMDD.|..MMD..|...D...|.......",
        mapOf('W' to rgb("#FFFFFF"), 'H' to rgb("#84FFFF"), 'M' to rgb("#00B8D4"), 'D' to rgb("#006064")),
    )
    val STAR = Icon(
        "star",
        "...H...|..HHM..|HHHHMMM|.HHMMD.|..HMD..|.HM.MD.|.M...D.",
        mapOf('H' to rgb("#FFF59D"), 'M' to rgb("#FFD600"), 'D' to rgb("#FF8F00")),
    )
    // A monitor with a ">_" prompt — the console rank; DEV already owns the bare terminal prompt.
    val MONITOR = Icon(
        "monitor",
        "DDDDDDD|DGSSSSD|DSGSSSD|DGSWWSD|DDDDDDD|...D...|.DDDDD.",
        mapOf('D' to rgb("#78909C"), 'S' to rgb("#102027"), 'G' to rgb("#69F0AE"), 'W' to rgb("#FFFFFF")),
    )
    val GRASS = Icon(
        "grass",
        "GGgGGGg|gGGgGGG|DgDDgDg|DDdDDDD|dDDDdDD|DDdDDDd|DDDDDdD",
        mapOf('G' to rgb("#8BC34A"), 'g' to rgb("#558B2F"), 'D' to rgb("#8D6E63"), 'd' to rgb("#6D4C41")),
    )
}
