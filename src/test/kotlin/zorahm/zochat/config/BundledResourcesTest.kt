package zorahm.zochat.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BundledResourcesTest {

    // loadFromString throws on broken YAML; YamlConfiguration.loadConfiguration (what the plugin
    // uses) would only log it and hand back an empty config, wiping every message on the server.
    private fun load(path: String): YamlConfiguration {
        val text = javaClass.classLoader.getResourceAsStream(path)!!.reader(Charsets.UTF_8).use { it.readText() }
        return YamlConfiguration().apply { loadFromString(text) }
    }

    @Test
    fun everyBundledYamlParses() {
        listOf(
            "config.yml", "placeholders.yml", "announcer.yml", "bubble.yml", "commands.yml", "badges.yml",
            "messages/messages_ru.yml", "messages/messages_en.yml", "welcome_messages/welcome_messages.yml",
        ).forEach { load(it) }
    }

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val ru = load("messages/messages_ru.yml").getKeys(true)
        val en = load("messages/messages_en.yml").getKeys(true)
        assertEquals(ru - en, emptySet<String>(), "only in messages_ru.yml")
        assertEquals(en - ru, emptySet<String>(), "only in messages_en.yml")
    }
}
