package zorahm.zochat.announcer

enum class SelectionType {
    SEQUENTIAL,
    RANDOM;

    companion object {
        // An unknown value falls back to SEQUENTIAL instead of failing the whole announcer.
        fun fromString(value: String?): SelectionType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: SEQUENTIAL
    }
}
