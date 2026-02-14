package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

@DependsOn({})
public class SkillSlotManager implements IManager {

    private final Map<UUID, SkillSlotData> slotDataMap = new HashMap<>();
    private File slotFile;
    private YamlConfiguration config;
    private final JavaPlugin plugin;

    public SkillSlotManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.slotFile = new File(plugin.getDataFolder(), "skill_slots.yml");
        this.config = YamlConfiguration.loadConfiguration(slotFile);
        loadAll();
    }

    @Override
    public void shutdown() {
        saveAll();
    }

    public void loadAll() {
        for (String key : config.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            List<String> slots = config.getStringList(key);
            slotDataMap.put(uuid, new SkillSlotData(slots));
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, SkillSlotData> entry : slotDataMap.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue().getSlots());
        }
        try {
            config.save(slotFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SkillSlotData get(UUID uuid) {
        return slotDataMap.computeIfAbsent(uuid, k -> new SkillSlotData());
    }

    public void setSlot(UUID uuid, int slot, String skillId) {
        SkillSlotData data = get(uuid);
        data.setSlot(slot, skillId);
    }

    public String getSkillInSlot(UUID uuid, int slot) {
        return get(uuid).getSkill(slot);
    }

    public void clear(UUID uuid) {
        slotDataMap.remove(uuid);
        config.set(uuid.toString(), null);
        try {
            config.save(slotFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SkillSlotData {
    private final List<String> slots; // インデックス0 = スロット1, 最大4スロット想定

    public SkillSlotData() {
        this.slots = new ArrayList<>(Arrays.asList(null, null, null, null));
    }

    public SkillSlotData(List<String> loaded) {
        this.slots = new ArrayList<>(Arrays.asList(null, null, null, null));
        for (int i = 0; i < Math.min(4, loaded.size()); i++) {
            slots.set(i, loaded.get(i));
        }
    }

    public void setSlot(int slotIndex, String skillId) {
        if (slotIndex < 0 || slotIndex >= 4) return;
        slots.set(slotIndex, skillId);
    }

    public String getSkill(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 4) return null;
        return slots.get(slotIndex);
    }

    public List<String> getSlots() {
        return new ArrayList<>(slots); // 保存用
    }
}
