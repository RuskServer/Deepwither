package com.lunar_prototype.deepwither.api.skill.aura;

import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとの動的なバフ（オーラ）を管理するマネージャー。
 */
public class AuraManager implements IManager {
    private final Map<UUID, Map<String, Long>> auras = new ConcurrentHashMap<>();

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        auras.clear();
    }

    /**
     * オーラを付与します。
     * @param player 対象プレイヤー
     * @param auraId オーラID
     * @param durationTicks 持続時間（tick）
     */
    public void addAura(Player player, String auraId, int durationTicks) {
        long endTime = System.currentTimeMillis() + (durationTicks * 50L);
        auras.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
             .put(auraId, endTime);
    }

    /**
     * オーラを削除します。
     * @param player 対象プレイヤー
     * @param auraId オーラID
     */
    public void removeAura(Player player, String auraId) {
        Map<String, Long> userAuras = auras.get(player.getUniqueId());
        if (userAuras != null) {
            userAuras.remove(auraId);
        }
    }

    /**
     * プレイヤーが特定のオーラを持っているかチェックします。
     * 期限切れのオーラはこのタイミングで自動削除されます。
     */
    public boolean hasAura(Player player, String auraId) {
        Map<String, Long> userAuras = auras.get(player.getUniqueId());
        if (userAuras == null) return false;
        
        Long endTime = userAuras.get(auraId);
        if (endTime == null) return false;
        
        if (System.currentTimeMillis() > endTime) {
            userAuras.remove(auraId);
            return false;
        }
        return true;
    }

    /**
     * プレイヤーの全オーラを解除します。
     */
    public void clearAuras(Player player) {
        auras.remove(player.getUniqueId());
    }
}
