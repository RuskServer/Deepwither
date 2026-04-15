package com.lunar_prototype.deepwither.modules.minidungeon.util;

import com.lunar_prototype.deepwither.loot.LootChestTemplate;
import com.lunar_prototype.deepwither.loot.LootEntry;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class MiniDungeonLootUtil {

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();

    /**
     * LootChestManager の fillChest をベースに、進行状態に応じた報酬減衰処理を挟むメソッド。
     * @param chestState ブロック状態のChest
     * @param template 使用するルートチェストテンプレート
     * @param multiplier 報酬倍率 (0.01 ～ 1.0)
     */
    public static void fillScaledChest(Chest chestState, LootChestTemplate template, double multiplier) {
        Inventory inv = chestState.getInventory();
        inv.clear();

        if (template.getEntries().isEmpty()) return;

        int min = template.getMinItems();
        int max = template.getMaxItems();
        
        // 元の抽選枠数
        int baseSlots = random.nextInt(max - min + 1) + min;
        
        // 乗算によるスケーリング (最低1回は抽選のテーブルに立たせる)
        int scaledSlots = Math.max(1, (int) Math.round(baseSlots * multiplier));

        for (int i = 0; i < scaledSlots; i++) {
            // スケーリング：Multiplierが低いほど、この枠の抽選自体が「ハズレ（空っぽ）」になる確率を設ける
            // これで「質と量」が確率的に落ちることを表現する
            if (multiplier < 1.0 && random.nextDouble() > multiplier) {
                // ハズレ枠（進行度が低いペナルティ）
                continue;
            }

            double totalChance = template.getTotalChance();
            double roll = random.nextDouble() * totalChance;
            LootEntry selectedEntry = null;
            double currentRoll = roll;
            
            for (LootEntry entry : template.getEntries()) {
                currentRoll -= entry.getChance();
                if (currentRoll <= 0) {
                    selectedEntry = entry;
                    break;
                }
            }

            if (selectedEntry != null) {
                ItemStack item = selectedEntry.createItem(random);
                if (item != null) {
                    // 空いているスロットをランダムに探す
                    int slot = random.nextInt(inv.getSize());
                    // 既にアイテムがある場合は上書きしない（簡易的な被り防止）
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, item);
                    }
                }
            }
        }

        // 完全に空になってしまった場合の救済措置 (進行度ペナルティが大きすぎた場合など)
        // 進行度が極端に低い場合（例：30%未満）は救済しないというような厳しさも考えられるが、
        // LootChestManagerに合わせて、最低1個の保証枠を用意するかどうかを決める。
        // ここでは multiplier > 0.1 の場合のみ最低保証をつける。
        if (inv.isEmpty() && multiplier > 0.1) {
            LootEntry fallback = template.getEntries().get(random.nextInt(template.getEntries().size()));
            ItemStack item = fallback.createItem(random);
            if (item != null) {
                inv.setItem(random.nextInt(inv.getSize()), item);
            }
        }
    }
}
