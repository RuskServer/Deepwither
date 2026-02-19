package com.lunar_prototype.deepwither.core.playerdata;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.core.PlayerCache;
import com.lunar_prototype.deepwither.data.DailyTaskData;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * プレイヤーデータの読み込み・保存・移行を一括管理するマネージャー。
 */
@DependsOn({DatabaseManager.class, CacheManager.class})
public class PlayerDataManager implements IManager {

    private final Deepwither plugin;
    private final CacheManager cache;

    public PlayerDataManager(Deepwither plugin, CacheManager cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public void init() throws Exception {
        // 必要なら新テーブルの作成などをここで行う
    }

    /**
     * プレイヤーデータを読み込みます。既存の各マネージャーのloadロジックを呼び出し、
     * 一つのPlayerDataオブジェクトに統合します。
     */
    public CompletableFuture<PlayerData> loadData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // 各マネージャーの既存ロードロジックを順番に実行し、結果をPlayerDataに集約する
            // 注意: 現状の各マネージャーのload(UUID)は同期的にDBアクセスしているため、
            // ここで一括で呼び出すことで、PlayerDataが完成した状態を作ります。
            
            plugin.getLevelManager().load(uuid);
            plugin.getAttributeManager().load(uuid);
            plugin.getSkilltreeManager().load(uuid);
            plugin.getManaManager().get(uuid); // Manaはデフォルト値が入る
            
            // DailyTasks, Crafting, Profession は PlayerConnectionListener で loadPlayer を呼んでいるため
            // 現時点ではここでは省略するが、最終的にはここに集約するのが望ましい
            
            PlayerCache pc = cache.getCache(uuid);
            return pc.getData();
        }, plugin.getAsyncExecutor());
    }

    /**
     * プレイヤーデータを保存します。
     */
    public void saveData(UUID uuid) {
        plugin.getLevelManager().save(uuid);
        plugin.getAttributeManager().save(uuid);
        plugin.getSkilltreeManager().save(uuid, plugin.getSkilltreeManager().load(uuid));
        
        // 追加
        DailyTaskData taskData = DW.cache().getCache(uuid).get(DailyTaskData.class);
        if (taskData != null) {
            plugin.getDailyTaskManager().saveAndUnloadPlayer(uuid);
        }
        
        plugin.getCraftingManager().saveAndUnloadPlayer(uuid);
        
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            plugin.getProfessionManager().saveAndUnloadPlayer(p);
        }
    }

    /**
     * プレイヤーデータをアンロードします。
     */
    public void unloadData(UUID uuid) {
        saveData(uuid);
        cache.removeCache(uuid);
    }
}
