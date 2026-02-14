package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum RoguelikeBuff {

    STRENGTH_BOOST(Component.text("筋力増強", NamedTextColor.RED), Component.text("攻撃力が10%上昇する", NamedTextColor.GRAY), Material.IRON_SWORD) {
        @Override
        public void apply(StatMap stats) {
            stats.addPercent(StatType.ATTACK_DAMAGE, 10.0);
        }
    },
    VITALITY_BOOST(Component.text("生命力向上", NamedTextColor.GREEN), Component.text("最大体力が20上昇する", NamedTextColor.GRAY), Material.GOLDEN_APPLE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.MAX_HEALTH, 20.0);
        }
    },
    SPEED_BOOST(Component.text("俊足の祝福", NamedTextColor.AQUA), Component.text("移動速度が10%上昇する", NamedTextColor.GRAY), Material.FEATHER) {
        @Override
        public void apply(StatMap stats) {
            stats.addPercent(StatType.MOVE_SPEED, 10.0);
        }
    },
    DEFENSE_BOOST(Component.text("鉄壁の守り", NamedTextColor.BLUE), Component.text("防御力が5上昇する", NamedTextColor.GRAY), Material.IRON_CHESTPLATE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.DEFENSE, 5.0);
        }
    },
    CRITICAL_STRIKE(Component.text("急所突き", NamedTextColor.YELLOW), Component.text("クリティカル率が5%上昇する", NamedTextColor.GRAY), Material.FLINT) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.CRIT_CHANCE, 5.0);
        }
    },
    REGENERATION(Component.text("自然治癒", NamedTextColor.LIGHT_PURPLE), Component.text("自然回復速度が上昇する", NamedTextColor.GRAY), Material.GHAST_TEAR) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.HP_REGEN, 0.5);
        }
    },
    BLOOD_PACT(Component.text("鮮血の契約", NamedTextColor.DARK_RED), Component.text("攻撃時、20%の確率で出血を付与する", NamedTextColor.GRAY), Material.REDSTONE_BLOCK) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.BLEED_CHANCE, 20.0);
        }
    },
    VAMPIRE_FANG(Component.text("吸血鬼の牙", NamedTextColor.RED), Component.text("与えたダメージの5%を回復する", NamedTextColor.GRAY), Material.GHAST_TEAR) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.LIFESTEAL, 5.0);
        }
    },
    FROST_TOUCH(Component.text("氷結の指先", NamedTextColor.AQUA), Component.text("攻撃時、15%の確率で敵を凍結させる", NamedTextColor.GRAY), Material.PACKED_ICE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.FREEZE_CHANCE, 15.0);
        }
    },
    EXPLOSIVE_BLOW(Component.text("爆砕撃", NamedTextColor.YELLOW), Component.text("攻撃時、10%の確率で周囲に拡散ダメージを与える", NamedTextColor.GRAY), Material.TNT) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.AOE_CHANCE, 10.0);
        }
    };

    private final Component displayName;
    private final Component description;
    private final Material icon;

    RoguelikeBuff(Component displayName, Component description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public Component getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public abstract void apply(StatMap stats);

    public StatMap getStatMap() {
        StatMap map = new StatMap();
        apply(map);
        return map;
    }
}