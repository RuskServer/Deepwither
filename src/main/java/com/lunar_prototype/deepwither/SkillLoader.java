package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

@DependsOn({})
public class SkillLoader implements IManager {
    private final Map<String, SkillDefinition> skills = new HashMap<>();
    private File skillfolder;
    private final JavaPlugin plugin;

    public SkillLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public void init() {
        File folder = new File(plugin.getDataFolder(), "skills");
        loadAllSkills(folder);
    }

    @Override
    public void shutdown() {}

    public void loadAllSkills(File skillFolder) {

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
            def.conflicts.addAll(config.getStringList("conflicts"));

            if (config.contains("tags")) {
                if (config.contains("tags.role")) {
                    for (String str : config.getStringList("tags.role")) {
                        try { def.roles.add(SkillTags.Role.valueOf(str.toUpperCase().replace("-", "_"))); } catch (Exception ignored) {}
                    }
                }
                if (config.contains("tags.tactics")) {
                    for (String str : config.getStringList("tags.tactics")) {
                        try { def.tactics.add(SkillTags.Tactics.valueOf(str.toUpperCase().replace("-", "_"))); } catch (Exception ignored) {}
                    }
                }
                if (config.contains("tags.scaling")) {
                    for (String str : config.getStringList("tags.scaling")) {
                        try { def.scalings.add(SkillTags.Scaling.valueOf(str.toUpperCase().replace("-", "_"))); } catch (Exception ignored) {}
                    }
                }
                if (config.contains("tags.constraint")) {
                    for (String str : config.getStringList("tags.constraint")) {
                        try { def.constraints.add(SkillTags.Constraint.valueOf(str.toUpperCase().replace("-", "_"))); } catch (Exception ignored) {}
                    }
                }
            }

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
