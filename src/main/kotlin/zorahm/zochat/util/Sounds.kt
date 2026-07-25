package zorahm.zochat.util

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import java.util.Optional

object Sounds {

    // Sound.valueOf is marked for removal in 1.21+, so resolve via the registry.
    // Reverse map for legacy enum-constant config values (ENTITY_EXPERIENCE_ORB_PICKUP),
    // built from the real keys (entity.experience_orb.pickup) the same way the old enum names were.
    private val byLegacyName: Map<String, Sound> by lazy {
        Registry.SOUNDS.associateBy { sound ->
            (Registry.SOUNDS.getKey(sound)?.key ?: "").uppercase().replace('.', '_')
        }
    }

    @JvmStatic
    fun parse(name: String?): Optional<Sound> {
        if (name.isNullOrEmpty()) return Optional.empty()
        // Namespaced/dotted key form, e.g. "entity.experience_orb.pickup" or "minecraft:...".
        NamespacedKey.fromString(name.lowercase())?.let { key ->
            Registry.SOUNDS.get(key)?.let { return Optional.of(it) }
        }
        // Legacy enum-constant form kept for backwards-compatible configs.
        return Optional.ofNullable(byLegacyName[name.uppercase()])
    }
}
