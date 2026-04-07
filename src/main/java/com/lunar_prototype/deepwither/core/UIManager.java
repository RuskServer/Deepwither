package com.lunar_prototype.deepwither.core;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.PlayerSettingsManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

@DependsOn({PlayerSettingsManager.class})
public class UIManager implements IManager {

    private final PlayerSettingsManager settingsManager;
    private static final String FILLED_BOX = "■";
    private static final String EMPTY_BOX = "□";

    public UIManager(PlayerSettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    /**
     * プレイヤーにメッセージを送信します（設定を確認します）。
     */
    public void sendMessage(Player player, PlayerSettingsManager.SettingType type, Component message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendMessage(message);
        }
    }

    /**
     * アクションバーにメッセージを送信します（設定を確認します）。
     */
    public void sendActionBar(Player player, PlayerSettingsManager.SettingType type, Component message) {
        if (settingsManager.isEnabled(player, type)) {
            player.sendActionBar(message);
        }
    }

    /**
     * アクションバーにメッセージを送信します（設定を確認しません）。
     */
    public void sendSimpleActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    /**
     * 特殊なアクション（クリティカル、スタン等）をアクションバーに通知します。
     */
    public void sendCombatAction(Player player, String actionName, NamedTextColor color) {
        Component message = Component.text("★ ", NamedTextColor.GOLD)
                .append(Component.text(actionName.toUpperCase(), color, TextDecoration.BOLD))
                .append(Component.text(" ★", NamedTextColor.GOLD));
        sendActionBar(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, message);
    }

    /**
     * 進捗バー（ボックス形式）をアクションバーに表示します。
     * 例: [ 斧コンボ ] ■■□
     */
    public void sendProgressBar(Player player, String label, int current, int max, NamedTextColor color) {
        TextComponent.Builder builder = Component.text().content("[ " + label + " ] ");
        
        for (int i = 0; i < max; i++) {
            if (i < current) {
                builder.append(Component.text(FILLED_BOX, color));
            } else {
                builder.append(Component.text(EMPTY_BOX, NamedTextColor.GRAY));
            }
        }

        sendActionBar(player, PlayerSettingsManager.SettingType.SHOW_SPECIAL_LOG, builder.build());
    }

    /**
     * プレイヤー固有のUI操作用ラッパーを取得します。
     */
    public PlayerUI of(Player player) {
        return new PlayerUI(player);
    }

    /**
     * 特定のプレイヤーに対するUI操作を簡略化するための内部クラス。
     */
    public class PlayerUI {
        private final Player player;

        private PlayerUI(Player player) {
            this.player = player;
        }

        public void message(PlayerSettingsManager.SettingType type, Component message) {
            sendMessage(player, type, message);
        }

        public void actionBar(PlayerSettingsManager.SettingType type, Component message) {
            sendActionBar(player, type, message);
        }

        public void simpleActionBar(Component message) {
            sendSimpleActionBar(player, message);
        }

        public void combatAction(String actionName, NamedTextColor color) {
            sendCombatAction(player, actionName, color);
        }

        public void progressBar(String label, int current, int max, NamedTextColor color) {
            sendProgressBar(player, label, current, max, color);
        }
    }
}
