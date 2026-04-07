package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import com.lunar_prototype.deepwither.api.event.DeepwitherDamageEvent;
import com.lunar_prototype.deepwither.core.damage.DamageContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@DependsOn({})
public class ArtifactManager implements IManager {
    public static final int MAX_SAME_ARTIFACT_TYPE = 2;

    private File dataFile;
    private Map<UUID, List<ItemStack>> playerArtifacts = new HashMap<>();
    // ★ 背中装備保存用のマップを追加
    private Map<UUID, ItemStack> playerBackpacks = new HashMap<>();
    private final JavaPlugin plugin;

    public ArtifactManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.dataFile = new File(plugin.getDataFolder(), "artifacts.dat");
        loadData();
    }

    @Override
    public void shutdown() {
        saveData();
    }

    public List<ItemStack> getPlayerArtifacts(Player player) {
        UUID playerId = player.getUniqueId();
        List<ItemStack> artifacts = playerArtifacts.computeIfAbsent(playerId, k -> new ArrayList<>());
        List<ItemStack> normalized = normalizeArtifacts(artifacts);
        if (normalized.size() != artifacts.size()) {
            playerArtifacts.put(playerId, normalized);
            return normalized;
        }
        return artifacts;
    }

    // ★ 背中装備を取得するメソッド
    public ItemStack getPlayerBackpack(Player player) {
        return playerBackpacks.get(player.getUniqueId());
    }

    public void savePlayerArtifacts(Player player, List<ItemStack> artifacts, ItemStack backpack) {
        playerArtifacts.put(player.getUniqueId(), normalizeArtifacts(artifacts));
        // ★ 背中装備も同時にキャッシュに保存
        if (backpack != null) {
            playerBackpacks.put(player.getUniqueId(), backpack);
        } else {
            playerBackpacks.remove(player.getUniqueId());
        }
    }

    public List<ItemStack> normalizeArtifacts(List<ItemStack> artifacts) {
        List<ItemStack> normalized = new ArrayList<>();
        Map<String, Integer> typeCounts = new HashMap<>();

        if (artifacts == null) {
            return normalized;
        }

        for (ItemStack artifact : artifacts) {
            if (!isArtifact(artifact)) {
                continue;
            }

            String typeKey = getArtifactTypeKey(artifact);
            if (typeKey == null) {
                continue;
            }
            int currentCount = typeCounts.getOrDefault(typeKey, 0);
            if (currentCount >= MAX_SAME_ARTIFACT_TYPE) {
                continue;
            }

            typeCounts.put(typeKey, currentCount + 1);
            normalized.add(artifact.clone());
        }

        return normalized;
    }

    public Map<String, Integer> countArtifactTypes(List<ItemStack> artifacts) {
        Map<String, Integer> counts = new HashMap<>();
        if (artifacts == null) {
            return counts;
        }

        for (ItemStack artifact : artifacts) {
            if (!isArtifact(artifact)) {
                continue;
            }
            String typeKey = getArtifactTypeKey(artifact);
            if (typeKey == null) {
                continue;
            }
            counts.put(typeKey, counts.getOrDefault(typeKey, 0) + 1);
        }
        return counts;
    }

    public StatMap getFullSetBonus(List<ItemStack> artifacts) {
        StatMap bonus = new StatMap();
        Map<String, Integer> typeCounts = countArtifactTypes(artifacts);

        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            if (entry.getValue() < 2) {
                continue;
            }

            bonus.add(ItemFactory.getArtifactSetBonus(entry.getKey(), entry.getValue()));
        }

        return bonus;
    }

    public int getArtifactTypeCount(Player player, String type) {
        if (player == null || type == null || type.isBlank()) {
            return 0;
        }

        return countArtifactTypes(getPlayerArtifacts(player)).getOrDefault(type.trim().toLowerCase(Locale.ROOT), 0);
    }

    public boolean hasArtifactTypeCount(Player player, String type, int requiredCount) {
        return getArtifactTypeCount(player, type) >= requiredCount;
    }

    public void handleArtifactSetTrigger(DeepwitherDamageEvent event) {
        if (event == null || !(event.getVictim() instanceof Player player)) {
            return;
        }

        Map<String, Integer> typeCounts = countArtifactTypes(getPlayerArtifacts(player));
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            List<ItemFactory.ArtifactSetRule> rules = ItemFactory.getArtifactSetRules(entry.getKey());
            if (rules.isEmpty()) {
                continue;
            }

            ItemFactory.ArtifactSetContext context = new ItemFactory.ArtifactSetContext(
                    player,
                    event,
                    entry.getKey(),
                    entry.getValue()
            );

            for (ItemFactory.ArtifactSetRule rule : rules) {
                if (!rule.matches(ItemFactory.ArtifactSetTrigger.DAMAGE_TAKEN, context)) {
                    continue;
                }

                rule.execute(context);
                if (event.isCancelled()) {
                    return;
                }
            }
        }
    }

    public void handleArtifactSetTrigger(DamageContext context, ItemFactory.ArtifactSetTrigger trigger) {
        if (context == null || trigger == null) {
            return;
        }

        Player player = context.getAttackerAsPlayer();
        if (player == null) {
            return;
        }

        Map<String, Integer> typeCounts = countArtifactTypes(getPlayerArtifacts(player));
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            List<ItemFactory.ArtifactSetRule> rules = ItemFactory.getArtifactSetRules(entry.getKey());
            if (rules.isEmpty()) {
                continue;
            }

            ItemFactory.ArtifactSetContext artifactContext = new ItemFactory.ArtifactSetContext(
                    player,
                    context,
                    entry.getKey(),
                    entry.getValue()
            );

            for (ItemFactory.ArtifactSetRule rule : rules) {
                if (!rule.matches(trigger, artifactContext)) {
                    continue;
                }

                rule.execute(artifactContext);
            }
        }
    }

    public boolean wouldExceedTypeLimit(List<ItemStack> artifacts, ItemStack candidate, int ignoreIndex) {
        if (!isArtifact(candidate)) {
            return false;
        }

        String candidateType = getArtifactTypeKey(candidate);
        if (candidateType == null) {
            return false;
        }
        int count = 0;
        if (artifacts != null) {
            for (int i = 0; i < artifacts.size(); i++) {
                if (i == ignoreIndex) {
                    continue;
                }
                ItemStack artifact = artifacts.get(i);
                if (!isArtifact(artifact)) {
                    continue;
                }
                String artifactType = getArtifactTypeKey(artifact);
                if (artifactType != null && candidateType.equals(artifactType)) {
                    count++;
                }
            }
        }
        return count >= MAX_SAME_ARTIFACT_TYPE;
    }

    public String getArtifactTypeKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String fullsetType = pdc.get(ItemFactory.ARTIFACT_FULLSET_TYPE, PersistentDataType.STRING);
        if (fullsetType != null && !fullsetType.isBlank()) {
            return fullsetType.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        }

        return null;
    }

    private boolean isArtifact(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return false;
        }

        for (net.kyori.adventure.text.Component line : meta.lore()) {
            if (net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains("アーティファクト")) {
                return true;
            }
        }

        return false;
    }

    // --- 永続化のロジック (互換性維持) ---

    public void saveData() {
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(new FileOutputStream(dataFile))) {
            // アーティファクトとバックパックを一つのMapにまとめて保存
            Map<String, Object> allData = new HashMap<>();
            allData.put("artifacts", sanitizeArtifactStorage(playerArtifacts));
            allData.put("backpacks", playerBackpacks);
            oos.writeObject(allData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new FileInputStream(dataFile))) {
            Object obj = ois.readObject();

            if (obj instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) obj;

                // 新しい形式 (Mapの中に "artifacts" キーがある場合)
                if (rawMap.containsKey("artifacts")) {
                    this.playerArtifacts = sanitizeArtifactStorage((Map<UUID, List<ItemStack>>) rawMap.get("artifacts"));
                    Object backpacks = rawMap.get("backpacks");
                    if (backpacks instanceof Map) {
                        this.playerBackpacks = (Map<UUID, ItemStack>) backpacks;
                    }
                }
                // 旧形式 (Map自体が playerArtifacts だった場合)
                else {
                    this.playerArtifacts = sanitizeArtifactStorage((Map<UUID, List<ItemStack>>) rawMap);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Map<UUID, List<ItemStack>> sanitizeArtifactStorage(Map<UUID, List<ItemStack>> source) {
        Map<UUID, List<ItemStack>> sanitized = new HashMap<>();
        if (source == null) {
            return sanitized;
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : source.entrySet()) {
            sanitized.put(entry.getKey(), normalizeArtifacts(entry.getValue()));
        }
        return sanitized;
    }
}
