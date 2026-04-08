package com.lunar_prototype.deepwither;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

public class SkillTags {

    public enum Role {
        ATTACK("攻撃", NamedTextColor.RED),
        CONTROL("妨害", NamedTextColor.YELLOW),
        DEFENSE("防御", NamedTextColor.BLUE),
        SUPPORT("支援", NamedTextColor.GREEN),
        UTILITY("機能", NamedTextColor.LIGHT_PURPLE);

        public final String label;
        public final TextColor color;
        Role(String label, TextColor color) { this.label = label; this.color = color; }
    }

    public enum Tactics {
        BURST("瞬発", NamedTextColor.GOLD),
        DISPLACE("位置操作", NamedTextColor.DARK_AQUA),
        ANTI_TANK("対重装", NamedTextColor.DARK_RED),
        MOBILITY("機動力", NamedTextColor.AQUA),
        DISPEL("解除", NamedTextColor.WHITE);

        public final String label;
        public final TextColor color;
        Tactics(String label, TextColor color) { this.label = label; this.color = color; }
    }

    public enum Scaling {
        PHYSICAL("物理", NamedTextColor.GOLD),
        MAGICAL("魔法", NamedTextColor.LIGHT_PURPLE),
        HYBRID("複合", NamedTextColor.YELLOW),
        CDR_HEAVY("高回転", NamedTextColor.AQUA);

        public final String label;
        public final TextColor color;
        Scaling(String label, TextColor color) { this.label = label; this.color = color; }
    }

    public enum Constraint {
        CHANNELING("詠唱", NamedTextColor.DARK_PURPLE),
        HIGH_COST("高燃費", NamedTextColor.DARK_RED),
        LONG_CD("長大CD", NamedTextColor.GRAY);

        public final String label;
        public final TextColor color;
        Constraint(String label, TextColor color) { this.label = label; this.color = color; }
    }

    /**
     * スキル定義から2行にまとめたタグ表示用のComponentリストを生成します。
     * 例: 
     * 役割: [攻撃] [妨害] 戦術: [瞬発]
     * 特性: [魔法] 制限: [詠唱]
     */
    public static List<Component> buildTagLore(SkillDefinition def) {
        List<Component> out = new ArrayList<>();
        
        // 1行目: 役割 / 戦術
        Component line1 = Component.empty();
        boolean hasLine1 = false;
        
        if (!def.roles.isEmpty()) {
            Component part = Component.text("役割: ", NamedTextColor.GRAY);
            for (Role r : def.roles) {
                part = part.append(Component.text("[", NamedTextColor.DARK_GRAY))
                           .append(Component.text(r.label, r.color))
                           .append(Component.text("] ", NamedTextColor.DARK_GRAY));
            }
            line1 = line1.append(part);
            hasLine1 = true;
        }
        
        if (!def.tactics.isEmpty()) {
            if (hasLine1) line1 = line1.append(Component.text("  ")); // 空白で区切る
            Component part = Component.text("戦術: ", NamedTextColor.GRAY);
            for (Tactics t : def.tactics) {
                part = part.append(Component.text("[", NamedTextColor.DARK_GRAY))
                           .append(Component.text(t.label, t.color))
                           .append(Component.text("] ", NamedTextColor.DARK_GRAY));
            }
            line1 = line1.append(part);
            hasLine1 = true;
        }
        
        if (hasLine1) out.add(line1.decoration(TextDecoration.ITALIC, false));
        
        // 2行目: 特性 / 制限
        Component line2 = Component.empty();
        boolean hasLine2 = false;
        
        if (!def.scalings.isEmpty()) {
            Component part = Component.text("特性: ", NamedTextColor.GRAY);
            for (Scaling s : def.scalings) {
                part = part.append(Component.text("[", NamedTextColor.DARK_GRAY))
                           .append(Component.text(s.label, s.color))
                           .append(Component.text("] ", NamedTextColor.DARK_GRAY));
            }
            line2 = line2.append(part);
            hasLine2 = true;
        }
        
        if (!def.constraints.isEmpty()) {
            if (hasLine2) line2 = line2.append(Component.text("  "));
            Component part = Component.text("制限: ", NamedTextColor.GRAY);
            for (Constraint c : def.constraints) {
                part = part.append(Component.text("[", NamedTextColor.DARK_GRAY))
                           .append(Component.text(c.label, c.color))
                           .append(Component.text("] ", NamedTextColor.DARK_GRAY));
            }
            line2 = line2.append(part);
            hasLine2 = true;
        }
        
        if (hasLine2) out.add(line2.decoration(TextDecoration.ITALIC, false));

        return out;
    }
}
