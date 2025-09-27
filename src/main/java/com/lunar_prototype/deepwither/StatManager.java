package com.lunar_prototype.deepwither;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class StatManager {

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a3bb7af7-3c5b-4df1-a17e-cdeae1db1d32");
    private static final UUID DEFENSE_MODIFIER_ID = UUID.fromString("3811b7a8-4756-415d-be35-5ed4ba14228b");
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("ff5dd7e3-d781-4fee-b3d4-bfe3a5fda85d");
    private static final UUID MOVE_SPEED_MODIFIER_ID = UUID.fromString("6692942f-af28-454e-b945-474de887286d");
    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("06b4e42e-9c53-4b72-80d1-4d59fb7dc1ef");

    public static void updatePlayerStats(Player player) {
        StatMap total = getTotalStatsFromEquipment(player);
        syncAttackDamage(player, total);
        syncAttributes(player,total);
        // 必要に応じて今後他ステータスも同期
    }

    public static StatMap getTotalStatsFromEquipment(Player player) {
        StatMap total = new StatMap();

        // 装備ステータス読み込み
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        total.add(readStatsFromItem(mainHand));

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total.add(readStatsFromItem(armor));
        }

        List<ItemStack> artifacts = Deepwither.getInstance().getArtifactManager().getPlayerArtifacts(player);
        for (ItemStack artifact : artifacts) {
            total.add(readStatsFromItem(artifact));
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        total.add(readStatsFromItem(offHand));

        // 体力の基礎値を追加（例えば20）
        double baseHp = 20.0;
        double currentHp = total.getFlat(StatType.MAX_HEALTH);
        total.setFlat(StatType.MAX_HEALTH, currentHp + baseHp);
        // マナの基礎地を追加
        double baseMana = 100.0;
        double currentMana = total.getFlat(StatType.MAX_MANA);
        total.setFlat(StatType.MAX_MANA, currentMana + baseMana);

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
        // 最大HP
        double hp = stats.getFinal(StatType.MAX_HEALTH);
        syncAttribute(player, Attribute.MAX_HEALTH, MAX_HEALTH_MODIFIER_ID, hp);
        player.setHealth(Math.min(player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getValue())); // オーバーフロー防止

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
}
