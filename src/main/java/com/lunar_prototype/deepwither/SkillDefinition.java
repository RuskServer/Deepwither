package com.lunar_prototype.deepwither;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class SkillDefinition {
    public String id;
    public String name;
    public List<String> lore;
    public Material material;
    public double manaCost;
    public int cooldown;
    public int cooldown_min;
    public String mythicSkillId;
    public double castTime; // 追加: 詠唱時間（秒）

    // 追加: スキルタグ
    public final List<SkillTags.Role> roles = new ArrayList<>();
    public final List<SkillTags.Tactics> tactics = new ArrayList<>();
    public final List<SkillTags.Scaling> scalings = new ArrayList<>();
    public final List<SkillTags.Constraint> constraints = new ArrayList<>();
    public final List<String> conflicts = new ArrayList<>();
}

