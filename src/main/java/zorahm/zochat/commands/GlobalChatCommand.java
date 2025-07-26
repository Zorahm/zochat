package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import zorahm.zochat.ChatConfig;
import zorahm.zochat.ChatPlugin;
import zorahm.zochat.MentionHandler;
import zorahm.zochat.MessageManager;

public class GlobalChatCommand implements CommandExecutor {
    private final ChatPlugin plugin;
    private final ChatConfig chatConfig;
    private final MessageManager messageManager;
    private final MentionHandler mentionHandler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GlobalChatCommand(ChatPlugin plugin, ChatConfig chatConfig, MessageManager messageManager) {
        this.plugin = plugin;
        this.chatConfig = chatConfig;
        this.messageManager = messageManager;
        this.mentionHandler = new MentionHandler(plugin, chatConfig);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(miniMessage.deserialize(messageManager.getMessage("errors.only-players")));
            return true;
        }

        Player player = (Player) sender;

        // Проверяем, указано ли сообщение
        if (args.length == 0) {
            player.sendMessage(miniMessage.deserialize("<red>Использование: /g <сообщение></red>"));
            return true;
        }

        // Формируем сообщение
        String message = String.join(" ", args);

        // Проверка запрещённых слов
        for (String word : message.split(" ")) {
            if (chatConfig.isBannedWord(word)) {
                player.sendMessage(miniMessage.deserialize("<red>Сообщение содержит запрещённые слова!</red>"));
                return true;
            }
        }

        // Обработка упоминаний
        MentionHandler.MentionResult mentionResult = mentionHandler.processMentions(message);
        String processedMessage = mentionResult.getProcessedMessage();
        mentionHandler.notifyMentionedPlayers(mentionResult.getMentionedPlayers());

        // Получаем префикс и суффикс из LuckPerms
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        // Форматируем сообщение
        String format = chatConfig.getGlobalChatFormat();
        String formattedMessage = format
                .replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{suffix}", suffix)
                .replace("{message}", processedMessage);

        Component parsedMessage = miniMessage.deserialize(formattedMessage);

        // Отправляем сообщение в глобальный чат
        Bukkit.getServer().broadcast(parsedMessage);

        return true;
    }
}