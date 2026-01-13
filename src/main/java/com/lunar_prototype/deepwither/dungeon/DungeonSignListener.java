package com.lunar_prototype.deepwither.dungeon;

import com.lunar_prototype.deepwither.Deepwither;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;

public class DungeonSignListener implements Listener {

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        // 1行目に [Dungeon] と書かれたら反応
        if (event.getLine(0).equalsIgnoreCase("[Dungeon]")) {
            // 見た目を整える
            event.setLine(0, ChatColor.DARK_PURPLE + "[" + ChatColor.LIGHT_PURPLE + "Dungeon" + ChatColor.DARK_PURPLE + "]");
            // 2行目にダンジョン名を入れる想定
            String dungeonName = event.getLine(1);
            if (dungeonName == null || dungeonName.isEmpty()) {
                event.setLine(1, ChatColor.RED + "Dungeon Name?");
            } else {
                event.setLine(1, ChatColor.GOLD + dungeonName);
            }

            event.getPlayer().sendMessage(ChatColor.GREEN + "ダンジョン看板を作成しました！");
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign)) return;

        Sign sign = (Sign) event.getClickedBlock().getState();

        // 1行目が作成した看板の形式かチェック
        if (sign.getLine(0).contains("Dungeon")) {
            Player player = event.getPlayer();
            String dungeonId = ChatColor.stripColor(sign.getLine(1)); // 色コードを除去

            // ConfigをロードしてGUIを起動
            File dungeonFile = new File(Deepwither.getInstance().getDataFolder(), "dungeons/" + dungeonId + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(dungeonFile);

            new DungeonDifficultyGUI(dungeonId, config).open(player);

        }
    }
}