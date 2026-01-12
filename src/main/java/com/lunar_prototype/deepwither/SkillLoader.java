package com.lunar_prototype.deepwither;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class SkillLoader {
    private final Map<String, SkillDefinition> skills = new HashMap<>();
    private File skillfolder;

    public void loadAllSkills(File skillFolder) {
        skills.clear();
        if (!skillFolder.exists()) skillFolder.mkdirs();
        skillfolder = skillFolder;

        for (File file : Objects.requireNonNull(skillFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            String id = file.getName().replace(".yml", "");
            String name = config.getString("name", id);
            List<String> lore = config.getStringList("lore");
            Material material = Material.matchMaterial(config.getString("material", "STONE"));

            double mana = config.getDouble("mana.base", 0);
            int cooldown = config.getInt("cooldown.base", 0);
            int cooldown_min = config.getInt("cooldown.min", 0);
            String mythicSkill = config.getString("mythic_skill");
            double castTime = config.getDouble("cast_time", 0.0); // 追加

            SkillDefinition def = new SkillDefinition();
            def.id = id;
            def.name = name;
            def.lore = lore;
            def.material = material != null ? material : Material.STONE;
            def.manaCost = mana;
            def.cooldown = cooldown;
            def.cooldown_min = cooldown_min;
            def.mythicSkillId = mythicSkill;
            def.castTime = castTime; // 追加

            skills.put(id, def);
        }
    }

    /**
     * スキル設定ファイルを再読み込みします。
     * 既存のキャッシュをクリアし、フォルダ内のファイルを再度スキャンします。
     */
    public void reload() {
        // コンストラクタ等で保持している skillFolder フィールド、
        // あるいは特定のパスを引数として loadAllSkills を呼び出す
        if (this.skillfolder != null) {
            loadAllSkills(this.skillfolder);
            // 必要に応じてログ出力
            // Deepwither.getInstance().getLogger().info("Skills have been reloaded.");
        }
    }

    public SkillDefinition get(String id) {
        return skills.get(id);
    }

    public Collection<SkillDefinition> getAll() {
        return skills.values();
    }
}
