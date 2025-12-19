package com.lunar_prototype.deepwither.api.event;

import org.antlr.v4.runtime.misc.NotNull;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class OpenSkillassignment extends Event {
    // 1. HandlerListの静的定義 (必須ボイラープレート)
    private static final HandlerList handlers = new HandlerList();

    // 2. イベントで提供したい情報
    private final Player player;

    // 3. コンストラクタで情報を渡す
    public OpenSkillassignment(@NotNull Player player) {
        this.player = player;
    }

    // 4. 情報を取り出すゲッター
    @NotNull
    public Player getPlayer() {
        return player;
    }

    // 5. 必須: HandlerListを返すメソッド (定型句)
    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    // 6. 必須: HandlerListの静的ゲッター (定型句)
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
