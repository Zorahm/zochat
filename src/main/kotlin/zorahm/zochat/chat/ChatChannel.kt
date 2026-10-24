package zorahm.zochat.chat

enum class ChatChannel(val permission: String) {
    LOCAL("zochat.local"),
    GLOBAL("zochat.global")
}
