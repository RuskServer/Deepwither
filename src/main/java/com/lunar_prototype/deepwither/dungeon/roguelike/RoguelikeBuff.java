package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.StatMap;
import com.lunar_prototype.deepwither.StatType;
import org.bukkit.Material;

public enum RoguelikeBuff {

    STRENGTH_BOOST("§c筋力増強", "§7攻撃力が10%上昇する", Material.IRON_SWORD) {
        @Override
        public void apply(StatMap stats) {
            stats.addPercent(StatType.ATTACK_DAMAGE, 10.0);
        }
    },
    VITALITY_BOOST("§a生命力向上", "§7最大体力が20上昇する", Material.GOLDEN_APPLE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.MAX_HEALTH, 20.0);
        }
    },
    SPEED_BOOST("§b俊足の祝福", "§7移動速度が10%上昇する", Material.FEATHER) {
        @Override
        public void apply(StatMap stats) {
            stats.addPercent(StatType.MOVE_SPEED, 10.0);
        }
    },
    DEFENSE_BOOST("§9鉄壁の守り", "§7防御力が5上昇する", Material.IRON_CHESTPLATE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.DEFENSE, 5.0);
        }
    },
    CRITICAL_STRIKE("§e急所突き", "§7クリティカル率が5%上昇する", Material.FLINT) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.CRIT_CHANCE, 5.0);
        }
    },
    REGENERATION("§d自然治癒", "§7自然回復速度が上昇する", Material.GHAST_TEAR) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.HP_REGEN, 0.5); // %/sec か flat かはStatManagerの実装依存だが一旦Flat扱い
        }
    },
    BLOOD_PACT("§4鮮血の契約", "§7攻撃時、20%の確率で出血を付与する", Material.REDSTONE_BLOCK) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.BLEED_CHANCE, 20.0);
        }
    },
    VAMPIRE_FANG("§c吸血鬼の牙", "§7与えたダメージの5%を回復する", Material.GHAST_TEAR) { // Material変更検討
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.LIFESTEAL, 5.0);
        }
    },
    FROST_TOUCH("§b氷結の指先", "§7攻撃時、15%の確率で敵を凍結させる", Material.PACKED_ICE) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.FREEZE_CHANCE, 15.0);
        }
    },
    EXPLOSIVE_BLOW("§e爆砕撃", "§7攻撃時、10%の確率で周囲に拡散ダメージを与える", Material.TNT) {
        @Override
        public void apply(StatMap stats) {
            stats.addFlat(StatType.AOE_CHANCE, 10.0);
        }
    };

    private final String displayName;
    private final String description;
    private final Material icon;

    RoguelikeBuff(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
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
