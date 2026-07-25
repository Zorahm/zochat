package zorahm.zochat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigFiles {
    private ConfigFiles() {
    }

    public static void saveDefaultIfAbsent(Plugin plugin, String resourcePath, File target) {
        if (!target.exists()) {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            plugin.saveResource(resourcePath, false);
        }
    }

    // Sync the user's file to the bundled default schema: add keys present in the default but
    // missing in the user file (keeping the user's existing values), and remove obsolete keys no
    // longer in the default (e.g. settings of removed features). zoChat configs have a fixed
    // schema, so any key not in the default is dead — this replaces the old version-based migration.
    public static boolean syncWithDefaults(Plugin plugin, String resourcePath, File target) {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return false;
        }
        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        FileConfiguration current = YamlConfiguration.loadConfiguration(target);

        boolean changed = false;
        // Add missing keys, keeping existing user values.
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!current.contains(key)) {
                current.set(key, defaults.get(key));
                changed = true;
            }
        }
        // Remove obsolete keys not present in the default schema.
        for (String key : current.getKeys(true)) {
            if (!defaults.contains(key)) {
                current.set(key, null);
                changed = true;
            }
        }
        if (changed) {
            try {
                current.save(target);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to sync " + target.getName() + " with defaults: " + e.getMessage());
            }
        }
        return changed;
    }
}
