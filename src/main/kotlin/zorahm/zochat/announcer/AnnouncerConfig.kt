package zorahm.zochat.announcer

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import zorahm.zochat.config.ConfigFiles
import java.io.File

/** One announcement definition referenced by ID from an [Announcer]. */
data class Announcement(
    val lines: List<String>,
    val sound: String,
    val permission: String,
    val worlds: List<String>,
)

/** A single announcer: its own timer, selection mode and the announcement IDs it cycles through. */
data class Announcer(
    val enabled: Boolean,
    val intervalSeconds: Int,
    val selectionType: SelectionType,
    val announcementIds: List<String>,
)

class AnnouncerConfig(private val plugin: Plugin) {

    var isEnabled: Boolean = false
        private set

    private var announcers: List<Announcer> = emptyList()
    private var announcements: Map<String, Announcement> = emptyMap()

    init {
        reload()
    }

    fun reload() {
        val resourcePath = "announcer.yml"
        val target = File(plugin.dataFolder, resourcePath)
        ConfigFiles.saveDefaultIfAbsent(plugin, resourcePath, target)
        // Deliberately no ConfigFiles.syncWithDefaults here: announcement IDs are user-authored, so
        // the "remove keys not in the default schema" pass would delete every custom announcement.
        val cfg = YamlConfiguration.loadConfiguration(target)

        isEnabled = cfg.getBoolean("enabled", true)

        announcers = cfg.getMapList("announcers").map { raw ->
            Announcer(
                enabled = raw["enabled"] as? Boolean ?: true,
                intervalSeconds = (raw["interval"] as? Number)?.toInt() ?: 0,
                selectionType = SelectionType.fromString(raw["selection-type"] as? String),
                announcementIds = (raw["announcements"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            )
        }

        val section = cfg.getConfigurationSection("announcements")
        announcements = section?.getKeys(false)?.mapNotNull { id ->
            val s = section.getConfigurationSection(id) ?: return@mapNotNull null
            id to Announcement(
                lines = s.getStringList("lines"),
                sound = s.getString("sound", "") ?: "",
                permission = s.getString("permission", "") ?: "",
                worlds = s.getStringList("worlds"),
            )
        }?.toMap() ?: emptyMap()
    }

    fun announcers(): List<Announcer> = announcers

    fun announcement(id: String): Announcement? = announcements[id]
}
