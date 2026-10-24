package zorahm.zochat.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MovedTextsTest {

    // Shipped (Russian) defaults must stay behind — copying them would put Russian into
    // messages_en.yml; only what the admin actually changed is carried over.
    @Test
    fun onlyCustomisedTextsMove() {
        val config = YamlConfiguration()
        config.set("join-message.format", "<gradient:#55ff55:#aaffaa>{player} присоединился к игре!</gradient>")
        config.set("quit-message.format", "<red>{player} is gone</red>")
        config.set("mention.message", "<yellow>Ping!</yellow>")

        assertEquals(
            mapOf("formats.quit" to "<red>{player} is gone</red>", "mention.notify" to "<yellow>Ping!</yellow>"),
            ChatConfig.customisedMovedTexts(config)
        )
    }

    @Test
    fun nothingMovesFromAnAlreadyMigratedConfig() {
        assertEquals(emptyMap<String, String>(), ChatConfig.customisedMovedTexts(YamlConfiguration()))
    }
}
