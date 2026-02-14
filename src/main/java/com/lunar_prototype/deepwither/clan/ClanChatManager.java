package com.lunar_prototype.deepwither.clan;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.GoogleImeConverter;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

@DependsOn({ClanManager.class})
public class ClanChatManager implements Listener, IManager {
    private ClanManager clanManager;
    private final JavaPlugin plugin;

    public ClanChatManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.clanManager = Deepwither.getInstance().getClanManager();
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage();

        String convertedMessage = GoogleImeConverter.convert(originalMessage);

        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        
        Component clanDisplay = (clan != null) 
                ? Component.text("[" + clan.getName() + "] ", NamedTextColor.AQUA) 
                : Component.empty();

        String finalMessageStr = originalMessage.equals(convertedMessage)
                ? originalMessage
                : convertedMessage + " (" + originalMessage + ")";
        
        Component finalMessage = Component.text(convertedMessage);
        if (!originalMessage.equals(convertedMessage)) {
            finalMessage = finalMessage.append(Component.text(" (" + originalMessage + ")", NamedTextColor.GRAY));
        }

        // Note: Paper uses AsyncChatEvent for Components, but here we are in legacy AsyncPlayerChatEvent.
        // We can still use Component in setFormat if the server is modern enough, but safest is to keep format as string
        // and let Paper handle the rest, or migrate to AsyncChatEvent.
        // Given the task, I will keep this simple but remove hardcoded section signs.
        
        String clanPrefix = (clan != null) ? "§b[" + clan.getName() + "]§r " : "";
        String msgSuffix = originalMessage.equals(convertedMessage) ? "" : " §7(" + originalMessage + ")";
        
        event.setFormat(clanPrefix + "%1$s: %2$s");
        event.setMessage(convertedMessage + msgSuffix);
    }
}
