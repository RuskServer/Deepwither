package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.stat.IStatManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
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

@DependsOn({})
public class StatManager implements IManager, IStatManager {

    private final Map<UUID, Double> actualCurrentHealth = new HashMap<>();
    private final Map<UUID, StatMap> temporaryBuffs = new HashMap<>();

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a3bb7af7-3c5b-4df1-a17e-cdeae1db1d32");
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("ff5dd7e3-d781-4fee-b3d4-bfe3a5fda85d");

    @Override
    public void init() {}

    @Override
    public void shutdown() {}

    @Override
    public PlayerStat of(Player player) {
        return new PlayerStat() {
            @Override
            public double getHP() { return getActualCurrentHealth(player); }
            @Override
            public void setHP(double health) { setActualCurrentHealth(player, health); }
            @Override
            public double getMaxHP() { return getActualMaxHealth(player); }
            @Override
            public void heal(double amount) { StatManager.this.heal(player, amount); }
            @Override
            public StatMap getAll() { return getTotalStats(player); }
            @Override
            public void update() { updatePlayerStats(player); }
        };
    }

    @Override
    public void updatePlayerStats(Player player) {
        StatMap total = getTotalStats(player);
        syncAttackDamage(player, total);
        syncAttributes(player,total);
        syncBukkitHealth(player);
        // 必要に応じて今後他ステータスも同期
    }

    @Override
    public StatMap getTotalStats(Player player) {
        return getTotalStatsFromEquipment(player);
    }

    /**
     * プレイヤーの実際の最大HPを計算する。（StatMapのMAX_HEALTHのFlat値を使用）
     */
    @Override
    public double getActualMaxHealth(Player player) {
        // getTotalStatsFromEquipment内の計算結果を使用する
        StatMap total = getTotalStats(player);
        // getTotalStatsFromEquipment内で既に baseHp (20.0) が加算されているので、その値をそのまま使用
        return total.getFlat(StatType.MAX_HEALTH);
    }

    // ----------------------------------------------------
    // ★ 一時バフの適用
    // ----------------------------------------------------
    @Override
    public void applyTemporaryBuff(UUID playerUUID, StatMap buff) {
        temporaryBuffs.put(playerUUID, buff);
    }

    // ----------------------------------------------------
    // ★ 一時バフの削除
    // ----------------------------------------------------
    @Override
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
            // ★修正点1: 死亡/リスポーン時
            // HP 0.0 にするとリスポーン直後に即死して無限ループするため、
            // 「1.0」または「最大HPの数%」のような "死なないギリギリの値" をセットする。
            double respawnHp = Math.max(1.0, maxHp * 0.1); // 例: 最大HPの10%または最低1.0
            actualCurrentHealth.put(player.getUniqueId(), respawnHp);
        }

        // BukkitのHPバーも同期
        syncBukkitHealth(player);
    }

    /**
     * プレイヤーのHPを自然回復させる。
     * @param player 回復対象
     * @param seconds 経過秒数 (タスク間隔)
     */
    @Override
    public void naturalRegeneration(Player player, double seconds) {
        double currentHp = getActualCurrentHealth(player);
        double maxHp = getActualMaxHealth(player);

        if (currentHp >= maxHp) return;

        // 実際の回復量を計算 (例: 毎秒最大HPの0.5%を回復)
        StatMap stats = getTotalStats(player);
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
     * プレイヤーのカスタムHPを回復させます。
     */
    @Override
    public void heal(Player player, double amount) {
        healCustomHealth(player, amount);
    }

    /**
     * プレイヤーのカスタムHPを回復させます。
     * @deprecated Use {@link #heal(Player, double)} instead.
     */
    @Deprecated
    public void healCustomHealth(Player player, double amount) {
        // プレイヤーの現在のカスタムHPを取得。ない場合はデフォルトの最大HP（20.0）を初期値とする。
        // StatManagerで最大HPも管理している場合は、そちらの値を参照してください。
        double currentHealth = getActualCurrentHealth(player);

        // プレイヤーの最大HPを取得 (StatManagerでカスタム最大HPを管理している場合はその値を使用)
        // ここでは便宜上、標準のAttributeから取得しています。
        double maxHealth = getActualMaxHealth(player);

        // 回復後のHPを計算（最大HPを超えないようにする）
        double newHealth = Math.min(currentHealth + amount, maxHealth);

        // カスタムHPを更新
        setActualCurrentHealth(player, newHealth);
    }

    /**
     * プレイヤーの現在の実際のHPを取得する。存在しない場合は最大HPで初期化。
     */
    @Override
    public double getActualCurrentHealth(Player player) {
        // 最初のロード時（マップに存在しない時）は最大HPで初期化する
        return actualCurrentHealth.getOrDefault(player.getUniqueId(), getActualMaxHealth(player));
    }

    /**
     * プレイヤーの実際のHPを更新し、BukkitのHPバーと同期する。（ダメージ/回復処理のコア）
     */
    @Override
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
     * プレイヤーの実際のHPを更新し、BukkitのHPバーと同期する。（ダメージ/回復処理のコア）
     */
    @Override
    public void setActualCurrenttoMaxHealth(Player player) {
        setActualCurrenttoMaxHelth(player);
    }

    /**
     * @deprecated Use {@link #setActualCurrenttoMaxHealth(Player)} instead.
     */
    @Deprecated
    @Override
    public void setActualCurrenttoMaxHelth(Player player) {
        double max = getTotalStats(player).getFinal(StatType.MAX_HEALTH);

        // 内部マップを更新
        actualCurrentHealth.put(player.getUniqueId(), max);

        // BukkitのHPバーを同期する
        syncBukkitHealth(player);
    }

    /**
     * 実際のHP割合に基づいて、BukkitのHPバー表示を更新する。
     * このメソッドは、最大HPが更新された場合（装備変更など）の現行値の維持も兼ねる。
     */
    public void syncBukkitHealth(Player player) {
        // ★修正点2: プレイヤーが既に死んでいる場合はHP操作をしない
        // これを行わないと、死体に対してsetHealth(0)が走って死亡イベントが多重発生する
        if (player.isDead()) {
            return;
        }

        double actualMax = getActualMaxHealth(player);
        double actualCurrent = getActualCurrentHealth(player);

        // ------------------------------------------------------------------
        // ★ 追加ロジック: 装備変更などで最大HPが減少した場合の現行HP調整
        if (actualCurrent > actualMax) {
            actualCurrent = actualMax;
            actualCurrentHealth.put(player.getUniqueId(), actualCurrent);
        }
        // ------------------------------------------------------------------

        // Bukkitの最大HPを20.0に固定
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null && maxHealthAttr.getValue() != 20.0) {
            AttributeModifier existing = maxHealthAttr.getModifier(new NamespacedKey("minecraft",MAX_HEALTH_MODIFIER_ID.toString()));
            if (existing != null) {
                maxHealthAttr.removeModifier(existing);
            }
            maxHealthAttr.setBaseValue(20.0);
        }

        // 実際のHP割合を計算
        double ratio = (actualMax > 0) ? actualCurrent / actualMax : 0.0;
        double bukkitHealth = ratio * 20.0;

        // ★修正点3: カスタムHPが0より大きいなら、バニラHPも最低値を保証する
        // 計算誤差で 0.0 になると勝手に死んでしまうため
        if (actualCurrent > 0 && bukkitHealth < 0.5) {
            bukkitHealth = 0.5; // 半ハート（生存）
        }

        // 死亡アニメーションのために0.0は許容するが、負の値は防ぐ
        player.setHealth(Math.max(0.0, bukkitHealth));
    }

    /**
     * @deprecated Use {@link #getTotalStats(Player)} instead.
     */
    @Deprecated
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

        ItemStack backpack = Deepwither.getInstance().getArtifactManager().getPlayerBackpack(player);
        total.add(readStatsFromItem(backpack));

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
                        double currentPercent = total.getPercent(StatType.ATTACK_DAMAGE);
                        total.setPercent(StatType.ATTACK_DAMAGE, currentPercent + (points * 1.0));
                    }
                    case VIT -> {
                        // 最大体力と防御力を乗算に
                        double hpPercent = total.getPercent(StatType.MAX_HEALTH);
                        total.setPercent(StatType.MAX_HEALTH, hpPercent + (points * 1.0));
                        double defPercent = total.getPercent(StatType.DEFENSE);
                        total.setPercent(StatType.DEFENSE, defPercent + (points * 0.5));
                    }
                    case MND -> {
                        double val = total.getFlat(StatType.CRIT_DAMAGE);
                        total.setFlat(StatType.CRIT_DAMAGE, val + points * 1.5);

                        double pDmgPercent = total.getPercent(StatType.PROJECTILE_DAMAGE);
                        total.setPercent(StatType.PROJECTILE_DAMAGE, pDmgPercent + (points * 1.5));
                    }
                    case INT -> {
                        double cdVal = total.getFlat(StatType.COOLDOWN_REDUCTION);
                        total.setFlat(StatType.COOLDOWN_REDUCTION, cdVal + points * 0.1);

                        double manaPercent = total.getPercent(StatType.MAX_MANA);
                        total.setPercent(StatType.MAX_MANA, manaPercent + (points * 2.0));
                    }
                    case AGI -> {
                        // クリティカルチャンスの計算はそのまま (AGI 1ポイントあたり 0.2%上昇)
                        double critChanceVal = total.getFlat(StatType.CRIT_CHANCE);
                        total.setFlat(StatType.CRIT_CHANCE, critChanceVal + points * 0.2);

                        // 移動速度の計算を修正 (AGI 1ポイントあたり 0.0025 上昇、つまり2ポイントで 0.005 上昇)
                        double speedVal = total.getFlat(StatType.MOVE_SPEED);
                        total.setFlat(StatType.MOVE_SPEED, speedVal + points * 0.0025);
                    }
                }
            }
        }

        // バフノードの加算（SkilltreeManager経由でSkillData取得）
        SkilltreeManager.SkillData skillData = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        if (skillData != null) {
            total.add(skillData.getPassiveStats());
        }

        // 内部的にStatManagerのインスタンスを取得してtemporaryBuffsにアクセス
        StatManager instance = (StatManager) Deepwither.getInstance().getStatManager();
        StatMap tempBuff = instance.temporaryBuffs.get(player.getUniqueId());
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
        StatMap stats = Deepwither.getInstance().getStatManager().getTotalStats(player);
        double cooldownReduction = stats.getFinal(StatType.COOLDOWN_REDUCTION);

        // クールダウン減少率を適用
        // 例: クールダウン減少率が20%の場合、0.2を乗算して元の値から引く
        return baseCooldown * (1.0 - (cooldownReduction / 100.0));
    }


    /**
     * @deprecated Internal use only.
     */
    @Deprecated
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
        AttributeModifier existing = attr.getModifier(new NamespacedKey("minecraft",ATTACK_DAMAGE_MODIFIER_ID.toString()));
        if (existing != null) {
            attr.removeModifier(existing);
        }

        // 値が0なら追加不要
        if (value == 0) return;

        // 新たなModifierを追加
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey("minecraft",ATTACK_DAMAGE_MODIFIER_ID.toString()),
                value,
                AttributeModifier.Operation.ADD_NUMBER
        );
        attr.addModifier(modifier);
    }

    public static void syncAttributes(Player player, StatMap stats) {
        // 防御力
        syncAttribute(player, Attribute.ARMOR, stats.getFinal(StatType.DEFENSE));
        //攻撃速度
        if (stats.getFinal(StatType.ATTACK_SPEED) > 0.1){
            double modifierValue = stats.getFinal(StatType.ATTACK_SPEED) - 4.0;
            syncAttribute(player,Attribute.ATTACK_SPEED,modifierValue);
        }

        syncAttribute(player,Attribute.ENTITY_INTERACTION_RANGE,stats.getFinal(StatType.REACH));

        // 1. まず計算上の移動速度補正値を取得 (例: -0.02 や +0.05)
        double speedBonus = stats.getFinal(StatType.MOVE_SPEED);

        // 2. 移動速度がマイナス（低下）している場合のみ軽減処理を行う
        if (speedBonus < 0) {
            // 軽減ステータスを取得 (例: 50 なら 50% 軽減)
            // ※ StatType.SLOW_RESISTANCE は新しく定義したEnumを使用してください
            double resistance = stats.getFinal(StatType.REDUCES_MOVEMENT_SPEED_DECREASE);

            // 抵抗値が 0 より大きい場合のみ計算
            if (resistance > 0) {
                // 100%を超えると逆に速度が上がってしまうため、最大100(1.0)に制限する
                double reductionFactor = Math.min(100.0, resistance) / 100.0;

                // 計算: 元のマイナス値 * (1.0 - 軽減率)
                // 例: -0.02 * (1.0 - 0.5) = -0.01 (低下量が半分になる)
                speedBonus = speedBonus * (1.0 - reductionFactor);
            }
        }

        // 3. 補正後の値を適用
        // 注意: syncAttributeの実装によりますが、基本値(0.1)に加算する仕組みならこれでOKです
        syncAttribute(player, Attribute.MOVEMENT_SPEED, speedBonus);
    }

    private static void syncAttribute(Player player, Attribute attrType,double value) {
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;

        NamespacedKey att_key = new NamespacedKey(Deepwither.getInstance(),"RPG");
        NamespacedKey baseAttackSpeed = NamespacedKey.minecraft("base_attack_speed");

        attr.removeModifier(baseAttackSpeed);

        // 既存の同一IDのModifierを削除
        for (AttributeModifier mod : new HashSet<>(attr.getModifiers())) {
            try {
                if (mod.getKey().equals(att_key)) {
                    attr.removeModifier(mod);
                }
            } catch (IllegalArgumentException ex) {
                Bukkit.getLogger().warning("[StatManager] Invalid AttributeModifier UUID on player " +
                        player.getName() + " | Attribute: " + attrType + " | Modifier: " + mod);
                // 明示的に削除しても良い（安全であれば）
                attr.removeModifier(mod);
            }
        }

        // 値が0ならスキップ（初期値に任せる）
        if (value == 0) return;

        AttributeModifier modifier = new AttributeModifier(att_key,value, AttributeModifier.Operation.ADD_NUMBER);
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
        // 1. 空気チェック
        if (item == null || item.getType().isAir()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;

        // 2. Damageable インターフェースを持っているか確認
        if (meta instanceof Damageable damageable) {

            // ★重要: getMaxDamage() を呼ぶ前に hasMaxDamage() をチェックする
            // これを怠ると IllegalStateException が発生します
            if (damageable.hasMaxDamage()) {
                int maxDurability = damageable.getMaxDamage();

                if (maxDurability > 0) {
                    int currentDamage = damageable.getDamage();
                    int remainingDurability = maxDurability - currentDamage;

                    // 残り耐久値が 1 以下の場合は読み込みをスキップ
                    if (remainingDurability <= 1) {
                        return false;
                    }
                }
            } else {
                // hasMaxDamage() が false の場合でも、バニラのデフォルト耐久値を持つか確認
                // (1.21.x ではコンポーネントがない＝壊れないアイテム、またはブロック等の場合が多い)
                int vanillaMax = item.getType().getMaxDurability();
                if (vanillaMax > 0) {
                    int currentDamage = damageable.getDamage();
                    if (vanillaMax - currentDamage <= 1) {
                        return false;
                    }
                }
            }
        }

        // 耐久値に問題がない（または耐久値という概念がない）アイテムはステータスを読み込む
        return true;
    }
}
