package zorahm.zochat.config

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfigFilesTest {

    @TempDir
    lateinit var dir: File

    // say.console-name became console.name; syncWithDefaults would otherwise drop the admin's value.
    @Test
    fun renamedKeyKeepsTheAdminsValue() {
        val file = File(dir, "config.yml").apply { writeText("say:\n  console-name: \"Overlord\"\n") }

        ConfigFiles.renameKeys(file, mapOf("say.console-name" to "console.name"))

        val config = YamlConfiguration.loadConfiguration(file)
        assertEquals("Overlord", config.getString("console.name"))
        assertFalse(config.contains("say.console-name"))
    }

    @Test
    fun anExistingNewKeyIsNotOverwritten() {
        val file = File(dir, "config.yml").apply {
            writeText("say:\n  console-name: \"Old\"\nconsole:\n  name: \"New\"\n")
        }

        ConfigFiles.renameKeys(file, mapOf("say.console-name" to "console.name"))

        assertEquals("New", YamlConfiguration.loadConfiguration(file).getString("console.name"))
    }
}
