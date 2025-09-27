package com.lunar_prototype.deepwither;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class SkillLoader {
    private final Map<String, SkillDefinition> skills = new HashMap<>();

    public void loadAllSkills(File skillFolder) {
        skills.clear();
        if (!skillFolder.exists()) skillFolder.mkdirs();

        for (File file : Objects.requireNonNull(skillFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            String id = file.getName().replace(".yml", "");
            String name = config.getString("name", id);
            List<String> lore = config.getStringList("lore");
            Material material = Material.matchMaterial(config.getString("material", "STONE"));

            double mana = config.getDouble("mana.base", 0);
            int cooldown = config.getInt("cooldown.base", 0);
            String mythicSkill = config.getString("mythic_skill");

            SkillDefinition def = new SkillDefinition();
            def.id = id;
            def.name = name;
            def.lore = lore;
            def.material = material != null ? material : Material.STONE;
            def.manaCost = mana;
            def.cooldown = cooldown;
            def.mythicSkillId = mythicSkill;

            skills.put(id, def);
        }
    }

    public SkillDefinition get(String id) {
        return skills.get(id);
    }

    public Collection<SkillDefinition> getAll() {
        return skills.values();
    }
}
