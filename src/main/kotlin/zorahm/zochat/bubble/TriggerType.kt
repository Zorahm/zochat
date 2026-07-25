package zorahm.zochat.bubble

enum class TriggerType {
    CHAT,
    COMMAND,
    CHAT_COMMAND;

    fun onChat(): Boolean = this == CHAT || this == CHAT_COMMAND
    fun onCommand(): Boolean = this == COMMAND || this == CHAT_COMMAND

    companion object {
        // Unknown value falls back to CHAT rather than disabling the feature silently.
        fun fromString(value: String?): TriggerType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CHAT
    }
}
