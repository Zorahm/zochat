package zorahm.zochat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import zorahm.zochat.config.Messages;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderService {
    private final Plugin plugin;
    private final PlaceholderConfig config;
    private final Messages messages;
    private final PapiHook papi;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
    private final DateTimeFormatter realtimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Map<String, PlaceholderDef> byAlias = new HashMap<>();
    private Pattern pattern;

    public PlaceholderService(Plugin plugin, PlaceholderConfig config, Messages messages, PapiHook papi) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.papi = papi;
        rebuild();
    }

    // Reload placeholders.yml and rebuild the match pattern so /chat reload picks up new aliases/customs.
    public void reload() {
        config.reload();
        rebuild();
    }

    private void rebuild() {
        byAlias.clear();
        List<String> tokens = new ArrayList<>();
        for (PlaceholderDef def : config.definitions()) {
            if (!def.getEnabled()) {
                continue;
            }
            register(def.getName(), def, tokens);
            for (String alias : def.getAliases()) {
                register(alias.toLowerCase(), def, tokens);
            }
        }
        pattern = buildPattern(tokens);
    }

    // Package-private + static so the boundary behaviour is unit-testable without a live server.
    static Pattern buildPattern(List<String> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }
        // Longest first so "^world" wins over the alias "^w"; the trailing lookahead stops "^w" from
        // matching inside "^world"/"^wave" — fixes the old substring-replace that ate longer tokens.
        tokens.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder sb = new StringBuilder("\\^(");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(tokens.get(i)));
        }
        sb.append(")(?![\\p{L}\\p{N}])");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private void register(String alias, PlaceholderDef def, List<String> tokens) {
        if (alias == null || alias.isEmpty() || byAlias.containsKey(alias)) {
            return;
        }
        byAlias.put(alias, def);
        tokens.add(alias);
    }

    public Component processPlaceholders(Player player, String message) {
        // Escape the player's text so their literal '<...>' is never parsed as MiniMessage — otherwise
        // anyone could inject colours, gradients or <click:run_command:...> that runs as the viewer.
        // Only trusted, config-defined placeholder values (substituted below) are meant to carry tags.
        return processEscaped(player, miniMessage.escapeTags(message));
    }

    // The caller has ALREADY tag-escaped the player text. ChatService escapes once and then splices
    // trusted mention tags into the string — escaping again here would neuter those tags.
    public Component processEscaped(Player player, String escaped) {
        if (!config.isEnabled() || pattern == null) {
            return miniMessage.deserialize(escaped);
        }

        Matcher matcher = pattern.matcher(escaped);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            PlaceholderDef def = byAlias.get(matcher.group(1).toLowerCase());
            String replacement = null;
            if (def != null
                    && (def.getPermission().isEmpty() || player.hasPermission(def.getPermission()))) {
                replacement = def.getBuiltin() ? builtin(player, def) : custom(player, def);
            }
            // Unknown alias / no permission / unresolved → keep the literal token as the player typed it.
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(out);
        return miniMessage.deserialize(out.toString());
    }

    // Custom placeholders resolve their PlaceholderAPI 'value' per player and splice it into 'format'.
    private String custom(Player player, PlaceholderDef def) {
        String value = papi.apply(player, def.getValue());
        return def.getFormat().replace("{value}", value);
    }

    private String builtin(Player player, PlaceholderDef def) {
        String format = def.getFormat();
        switch (def.getName()) {
            case "loc":
                return format
                        .replace("{x}", String.format("%.0f", player.getLocation().getX()))
                        .replace("{y}", String.format("%.0f", player.getLocation().getY()))
                        .replace("{z}", String.format("%.0f", player.getLocation().getZ()))
                        .replace("{world}", config.worldName(player.getWorld().getName()));

            case "world":
                return format.replace("{world}", config.worldName(player.getWorld().getName()));

            case "time":
                long worldTime = player.getWorld().getTime();
                long hours = (worldTime / 1000 + 6) % 24;
                long minutes = (worldTime % 1000) * 60 / 1000;
                String gameTime = String.format("%02d:%02d", hours, minutes);
                String realTime = realtimeFormatter.format(Instant.now());
                return format
                        .replace("{time}", gameTime)
                        .replace("{realtime}", realTime);

            case "health":
                double health = player.getHealth();
                var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                return format
                        .replace("{health}", String.format("%.1f", health))
                        .replace("{maxhealth}", String.format("%.1f", maxHealth));

            case "ping":
                return format.replace("{ping}", String.valueOf(player.getPing()));

            case "biome":
                // Biome.name() (legacy enum) is marked for removal — use the key value from the registry instead.
                String biome = player.getLocation().getBlock().getBiome().getKey().getKey()
                        .replace("_", " ");
                return format.replace("{biome}", biome);

            case "item":
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    return format
                            .replace("{item}", messages.get("placeholders.empty-hand"))
                            .replace("{amount}", "0");
                }
                ItemMeta meta = item.getItemMeta();
                // displayName() returns a Component; extract plain text from it, otherwise legacy color
                // codes of a custom name would end up as raw characters in MiniMessage format (like issue #3).
                String itemName = (meta != null && meta.hasDisplayName())
                        ? plainText.serialize(meta.displayName())
                        : item.getType().name().toLowerCase().replace("_", " ");
                return format
                        .replace("{item}", itemName)
                        .replace("{amount}", String.valueOf(item.getAmount()));

            default:
                // A built-in entry whose name isn't one we know how to resolve — leave it literal.
                return null;
        }
    }
}
