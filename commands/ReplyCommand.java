package zorahm.zochat.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MsgCommand msgCommand;

    public ReplyCommand(MsgCommand msgCommand) {
        this.msgCommand = msgCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /reply <сообщение>");
            return true;
        }

        Player player = (Player) sender;
        HashMap<UUID, UUID> lastMessages = msgCommand.getLastMessages();
        UUID targetUUID = lastMessages.get(player.getUniqueId());

        if (targetUUID == null) {
            player.sendMessage(miniMessage.deserialize("<red>Некому отвечать.</red>"));
            return true;
        }

        Player target = player.getServer().getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            player.sendMessage(miniMessage.deserialize("<red>Игрок, которому вы пытаетесь ответить, не в сети.</red>"));
            return true;
        }

        String message = String.join(" ", args);

        String incomingFormat = "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{player} <#c0c0c0>›</#c0c0c0> {message}";
        String outgoingFormat = "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>Вы <#c0c0c0>›</#c0c0c0> {message}";

        Component incomingMessage = miniMessage.deserialize(incomingFormat
                .replace("{player}", player.getName())
                .replace("{message}", message));

        Component outgoingMessage = miniMessage.deserialize(outgoingFormat
                .replace("{player}", target.getName())
                .replace("{message}", message));

        target.sendMessage(incomingMessage);
        player.sendMessage(outgoingMessage);

        return true;
    }
}
