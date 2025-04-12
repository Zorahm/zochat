package zorahm.zochat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionHandler {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public MentionHandler(ChatPlugin plugin, ChatConfig chatConfig) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
    }

    public static class MentionResult {
        private final String processedMessage;
        private final List<Player> mentionedPlayers;

        public MentionResult(String processedMessage, List<Player> mentionedPlayers) {
            this.processedMessage = processedMessage;
            this.mentionedPlayers = mentionedPlayers;
        }

        public String getProcessedMessage() {
            return processedMessage;
        }

        public List<Player> getMentionedPlayers() {
            return mentionedPlayers;
        }
    }

    public MentionResult processMentions(String message) {
        Matcher matcher = MENTION_PATTERN.matcher(message);
        String processedMessage = message;
        List<Player> mentionedPlayers = new ArrayList<>();

        while (matcher.find()) {
            String mentionedName = matcher.group(1);
            Player mentionedPlayer = Bukkit.getPlayerExact(mentionedName);
            if (mentionedPlayer != null && !mentionedPlayers.contains(mentionedPlayer)) {
                String mentionFormat = chatConfig.getMentionFormat().replace("{player}", mentionedPlayer.getName());
                processedMessage = processedMessage.replace("@" + mentionedName, mentionFormat);
                mentionedPlayers.add(mentionedPlayer);
            }
        }

        return new MentionResult(processedMessage, mentionedPlayers);
    }

    public void notifyMentionedPlayers(List<Player> mentionedPlayers) {
        for (Player mentionedPlayer : mentionedPlayers) {
            try {
                Sound sound = Sound.valueOf(chatConfig.getMentionSound().toUpperCase());
                mentionedPlayer.playSound(mentionedPlayer.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Звук '" + chatConfig.getMentionSound() + "' не найден!");
            }
            mentionedPlayer.sendActionBar(miniMessage.deserialize(chatConfig.getMentionMessage()));
        }
    }
}