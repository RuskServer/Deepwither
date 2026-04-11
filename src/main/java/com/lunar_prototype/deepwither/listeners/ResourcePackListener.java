package com.lunar_prototype.deepwither.listeners;

import com.lunar_prototype.deepwither.Deepwither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class ResourcePackListener implements Listener {

    private final Deepwither plugin;
    private byte[] resourcePackHash = null;

    public ResourcePackListener(Deepwither plugin) {
        this.plugin = plugin;
    }

    public void setResourcePackHash(byte[] hash) {
        this.resourcePackHash = hash;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String url = plugin.getConfig().getString("resource-pack.url");

        if (url == null || url.isEmpty()) return;

        String promptStr = plugin.getConfig().getString("resource-pack.prompt", "&aApply resource pack");
        Component prompt = LegacyComponentSerializer.legacyAmpersand().deserialize(promptStr);
        boolean force = plugin.getConfig().getBoolean("resource-pack.force", false);

        // 1.17以降推奨のプロンプト付き適用メソッド
        // ハッシュ値があると、更新時に再ダウンロードを促せます
        if (resourcePackHash != null) {
            player.setResourcePack(UUID.nameUUIDFromBytes(url.getBytes()), url, resourcePackHash, prompt, force);
        } else {
            player.setResourcePack(url);
        }
    }
}
