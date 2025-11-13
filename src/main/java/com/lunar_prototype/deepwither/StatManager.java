package com.lunar_prototype.deepwither;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class StatManager {

    private final Map<UUID, Double> actualCurrentHealth = new HashMap<>();
    private final Map<UUID, StatMap> temporaryBuffs = new HashMap<>();

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a3bb7af7-3c5b-4df1-a17e-cdeae1db1d32");
    private static final UUID DEFENSE_MODIFIER_ID = UUID.fromString("3811b7a8-4756-415d-be35-5ed4ba14228b");
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("ff5dd7e3-d781-4fee-b3d4-bfe3a5fda85d");
    private static final UUID MOVE_SPEED_MODIFIER_ID = UUID.fromString("6692942f-af28-454e-b945-474de887286d");
    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("06b4e42e-9c53-4b72-80d1-4d59fb7dc1ef");

    public void updatePlayerStats(Player player) {
        StatMap total = getTotalStatsFromEquipment(player);
        syncAttackDamage(player, total);
        syncAttributes(player,total);
        syncBukkitHealth(player);
        // 必要に応じて今後他ステータスも同期
    }

    /**
     * プレイヤーの実際の最大HPを計算する。（StatMapのMAX_HEALTHのFlat値を使用）
     */
    public double getActualMaxHealth(Player player) {
        // getTotalStatsFromEquipment内の計算結果を使用する
        StatMap total = getTotalStatsFromEquipment(player);
        // getTotalStatsFromEquipment内で既に baseHp (20.0) が加算されているので、その値をそのまま使用
        return total.getFlat(StatType.MAX_HEALTH);
    }

    // ----------------------------------------------------
    // ★ 一時バフの適用
    // ----------------------------------------------------
    public void applyTemporaryBuff(UUID playerUUID, StatMap buff) {
        temporaryBuffs.put(playerUUID, buff);
    }

    // ----------------------------------------------------
    // ★ 一時バフの削除
    // ----------------------------------------------------
    public void removeTemporaryBuff(UUID playerUUID) {
        temporaryBuffs.remove(playerUUID);
    }


    /**
     * ログイン時やリスポーン時に呼ばれ、HPをリセットする。
     * ログイン時は最大値に設定。リスポーン時は0.0からスタートさせ、リスポーン保護中に自然回復させるのが一般的。
     */
    public void resetHealthOnEvent(Player player, boolean isLogin) {
        double maxHp = getActualMaxHealth(player);

        if (isLogin) {
            // ログイン時: 最大HPで初期化
            actualCurrentHealth.put(player.getUniqueId(), maxHp);
        } else {
            // 死亡/リスポーン時: 最小HP (0.0) で初期化
            actualCurrentHealth.put(player.getUniqueId(), 0.0);
        }

        // BukkitのHPバーも同期
        syncBukkitHealth(player);
    }

    /**
     * プレイヤーのHPを自然回復させる。
     * @param player 回復対象
     * @param seconds 経過秒数 (タスク間隔)
     */
    public void naturalRegeneration(Player player, double seconds) {
        double currentHp = getActualCurrentHealth(player);
        double maxHp = getActualMaxHealth(player);

        if (currentHp >= maxHp) return;

        // 実際の回復量を計算 (例: 毎秒最大HPの0.5%を回復)
        StatMap stats = getTotalStatsFromEquipment(player);
        double regenPercent = stats.getFinal(StatType.HP_REGEN) / 100.0; // HP_REGENステータスを想定

        // 毎秒の基本回復量 (MAX HP * 0.5% + StatのRegen量)
        double baseRegenPerSecond = maxHp * 0.01 + regenPercent;

        double actualRegenAmount = baseRegenPerSecond * seconds;

        // HPを更新
        setActualCurrentHealth(player, currentHp + actualRegenAmount);

        // 回復エフェクト（任意）
        player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.5, 0.5, 0.5, 0);
    }

    /**
     * プレイヤーの現在の実際のHPを取得する。存在しない場合は最大HPで初期化。
     */
    public double getActualCurrentHealth(Player player) {
        // 最初のロード時（マップに存在しない時）は最大HPで初期化する
        return actualCurrentHealth.getOrDefault(player.getUniqueId(), getActualMaxHealth(player));
    }

    /**
     * プレイヤーの実際のHPを更新し、BukkitのHPバーと同期する。（ダメージ/回復処理のコア）
     */
    public void setActualCurrentHealth(Player player, double newHealth) {
        double max = getActualMaxHealth(player);

        // HPを最大値と0の間に制限
        newHealth = Math.min(newHealth, max);
        newHealth = Math.max(newHealth, 0.0);

        // 内部マップを更新
        actualCurrentHealth.put(player.getUniqueId(), newHealth);

        // BukkitのHPバーを同期する
        syncBukkitHealth(player);
    }

    /**
     * 実際のHP割合に基づいて、BukkitのHPバー表示を更新する。
     * このメソッドは、最大HPが更新された場合（装備変更など）の現行値の維持も兼ねる。
     */
    public void syncBukkitHealth(Player player) {
        double actualMax = getActualMaxHealth(player);
        double actualCurrent = getActualCurrentHealth(player);

        // ------------------------------------------------------------------
        // ★ 追加ロジック: 装備変更などで最大HPが減少した場合の現行HP調整
        // (maintainHealthOnStatUpdate の役割を統合)
        if (actualCurrent > actualMax) {
            // 現在のHPが新しい最大HPを超えていたら、上限に合わせる
            actualCurrent = actualMax;
            // 内部マップも更新
            actualCurrentHealth.put(player.getUniqueId(), actualCurrent);
        }
        // ------------------------------------------------------------------

        // Bukkitの最大HPを20.0に固定
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getValue() != 20.0) {
            // 既存のMAX_HEALTH Modifierを削除
            AttributeModifier existing = maxHealthAttr.getModifier(MAX_HEALTH_MODIFIER_ID);
            if (existing != null) {
                maxHealthAttr.removeModifier(existing);
            }
            // ベース値も20.0に設定することで、クライアントに20.0を強制させる
            maxHealthAttr.setBaseValue(20.0);
        }

        // 実際のHP割合を計算
        double ratio = (actualMax > 0) ? actualCurrent / actualMax : 0.0;

        // Bukkitの最大HP (20.0) に割合を適用し、表示用のHP値を決定
        double bukkitHealth = ratio * 20.0;

        // プレイヤーのBukkit HPを更新 (死亡アニメーションのために0.1を最低値とする)
        player.setHealth(Math.max(0.1, bukkitHealth));

        // 死亡時のHP=0.0の確実な同期（念のため）
        if (actualCurrent <= 0.0 && player.getHealth() > 0.1) {
            player.setHealth(0.0);
        }
    }

    public static StatMap getTotalStatsFromEquipment(Player player) {
        StatMap total = new StatMap();
        PlayerLevelData data = Deepwither.getInstance().getLevelManager().get(player);

        // 装備ステータス読み込み
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (shouldReadStats(mainHand)) {
            total.add(readStatsFromItem(mainHand));
        }

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total.add(readStatsFromItem(armor));
        }

        List<ItemStack> artifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (ItemStack artifact : artifacts) {
            total.add(readStatsFromItem(artifact));
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isOffHandEquipment(offHand)) {
            total.add(readStatsFromItem(offHand));
        }

        // ステ振りバフ（AttributeManagerと連携）
        PlayerAttributeData attr = Deepwither.getInstance().getAttributeManager().get(player.getUniqueId());
        if (attr != null) {
            for (StatType type : StatType.values()) {
                int points = attr.getAllocated(type);
                switch (type) {
                    case STR -> {
                        double val = total.getFlat(StatType.ATTACK_DAMAGE);
                        total.setFlat(StatType.ATTACK_DAMAGE, val + points * 2.0);
                    }
                    case VIT -> {
                        double val = total.getFlat(StatType.MAX_HEALTH);
                        total.setFlat(StatType.MAX_HEALTH, val + points * 2.0);
                        total.setFlat(StatType.DEFENSE, val + points * 1.0);
                    }
                    case MND -> {
                        double val = total.getFlat(StatType.CRIT_DAMAGE);
                        total.setFlat(StatType.CRIT_DAMAGE, val + points * 1.5);
                    }
                    case INT -> {
                        double cdVal = total.getFlat(StatType.COOLDOWN_REDUCTION);
                        total.setFlat(StatType.COOLDOWN_REDUCTION, cdVal + points * 0.1);

                        double manaVal = total.getFlat(StatType.MAX_MANA);
                        total.setFlat(StatType.MAX_MANA, manaVal + points * 5.0);
                    }
                    case AGI -> {
                        double critChanceVal = total.getFlat(StatType.CRIT_CHANCE);
                        total.setFlat(StatType.CRIT_CHANCE, critChanceVal + points * 0.2);

                        double speedVal = total.getFlat(StatType.MOVE_SPEED);
                        total.setFlat(StatType.MOVE_SPEED, speedVal + points * 0.01);
                    }
                }
            }
        }

        // バフノードの加算（SkilltreeManager経由でSkillData取得）
        SkilltreeManager.SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        if (skillData != null) {
            total.add(skillData.getPassiveStats());
        }

        StatMap tempBuff = Deepwither.getInstance().statManager.temporaryBuffs.get(player.getUniqueId());
        if (tempBuff != null) {
            total.add(tempBuff); // StatMapのaddメソッドを使用
        }

        // 体力の基礎値を追加（例えば20）
        double baseHp = 20.0;
        double currentHp = total.getFinal(StatType.MAX_HEALTH);
        double levelhp = 2 * data.getLevel();
        total.setFlat(StatType.MAX_HEALTH, currentHp + baseHp + levelhp);
        // マナの基礎地を追加
        double baseMana = 100.0;
        double currentMana = total.getFinal(StatType.MAX_MANA);
        total.setFlat(StatType.MAX_MANA, currentMana + baseMana);

        return total;
    }

    public static double getEffectiveCooldown(Player player, double baseCooldown) {
        // プレイヤーの合計クールダウン減少率を取得
        StatMap stats = getTotalStatsFromEquipment(player);
        double cooldownReduction = stats.getFinal(StatType.COOLDOWN_REDUCTION);

        // クールダウン減少率を適用
        // 例: クールダウン減少率が20%の場合、0.2を乗算して元の値から引く
        return baseCooldown * (1.0 - (cooldownReduction / 100.0));
    }


    public static StatMap readStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || !item.hasItemMeta()) return stats;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        for (StatType type : StatType.values()) {
            Double flat = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    public static void syncAttackDamage(Player player, StatMap stats) {
        double flat = stats.getFlat(StatType.ATTACK_DAMAGE);
        double percent = stats.getPercent(StatType.ATTACK_DAMAGE);
        double value = flat * (1 + percent / 100.0);

        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr == null) return;

        // 先に既存のModifier（UUID指定）を完全に削除
        AttributeModifier existing = attr.getModifier(ATTACK_DAMAGE_MODIFIER_ID);
        if (existing != null) {
            attr.removeModifier(existing);
        }

        // 値が0なら追加不要
        if (value == 0) return;

        // 新たなModifierを追加
        AttributeModifier modifier = new AttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID,
                "MMO_Attack_Damage",
                value,
                AttributeModifier.Operation.ADD_NUMBER
        );
        attr.addModifier(modifier);
    }

    public static void syncAttributes(Player player, StatMap stats) {
        // 防御力
        syncAttribute(player, Attribute.ARMOR, DEFENSE_MODIFIER_ID, stats.getFinal(StatType.DEFENSE));
        //攻撃速度
        if (stats.getFinal(StatType.ATTACK_SPEED) > 0.1){
            double modifierValue = stats.getFinal(StatType.ATTACK_SPEED) - 4.0;
            syncAttribute(player, Attribute.ATTACK_SPEED,ATTACK_SPEED_MODIFIER_ID,modifierValue);
        }

        // 移動速度（注意：初期値が0.1くらいなので +0.01でも体感変わる）
        syncAttribute(player, Attribute.MOVEMENT_SPEED, MOVE_SPEED_MODIFIER_ID, stats.getFinal(StatType.MOVE_SPEED));
    }

    private static void syncAttribute(Player player, Attribute attrType, UUID uuid, double value) {
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;

        // 既存の同一IDのModifierを削除
        for (AttributeModifier mod : new HashSet<>(attr.getModifiers())) {
            try {
                if (mod.getUniqueId().equals(uuid)) {
                    attr.removeModifier(mod);
                }
            } catch (IllegalArgumentException ex) {
                Bukkit.getLogger().warning("[StatManager] Invalid AttributeModifier UUID on player " +
                        player.getName() + " | Attribute: " + attrType.name() + " | Modifier: " + mod);
                // 明示的に削除しても良い（安全であれば）
                attr.removeModifier(mod);
            }
        }

        // 値が0ならスキップ（初期値に任せる）
        if (value == 0) return;

        AttributeModifier modifier = new AttributeModifier(uuid, "custom_" + attrType.name(), value, AttributeModifier.Operation.ADD_NUMBER);
        attr.addModifier(modifier);
    }

    private static boolean isOffHandEquipment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                // 「カテゴリ:オフハンド装備」という文字列が完全に含まれているかをチェック
                // 色コードがついていても機能するように、ChatColor.stripColor() を使用することを推奨します。
                String strippedLine = org.bukkit.ChatColor.stripColor(line);
                if (strippedLine.contains("カテゴリ:オフハンド装備")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldReadStats(ItemStack item) {
        if (item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // ItemMetaが存在し、耐久値を持つアイテム（Damageable）であるかチェック
        if (meta instanceof Damageable damageable) {

            int maxDurability = item.getType().getMaxDurability();
            int currentDamage = damageable.getDamage(); // 現在の累積ダメージ量

            // 残り耐久値を計算 (最大耐久値 - 累積ダメージ)
            int remainingDurability = maxDurability - currentDamage;

            // 残り耐久値が 1 でないことを確認
            if (remainingDurability <= 1) {
                // 耐久値が1以下の場合、統計値の読み込みをスキップ
                return false;
            }
        }

        // 通常のアイテム、または耐久値が2以上のアイテムの場合は読み込みを許可
        return true;
    }
}
