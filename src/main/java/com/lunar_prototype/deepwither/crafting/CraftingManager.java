package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.api.DW;
import com.lunar_prototype.deepwither.core.CacheManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@DependsOn({ItemFactory.class, CacheManager.class})
public class CraftingManager implements IManager {

    private final Deepwither plugin;
    private CraftingDataStore dataStore;
    private PlayerRecipeJsonStore recipeStore;
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();

    private NamespacedKey customIdKey;
    public static final NamespacedKey BLUEPRINT_KEY = new NamespacedKey(Deepwither.getInstance(), "blueprint_recipe_id");

    public CraftingManager(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.dataStore = new CraftingDataStore(plugin);
        this.recipeStore = new PlayerRecipeJsonStore(plugin);
        this.customIdKey = new NamespacedKey(plugin, "custom_id");
        loadRecipes();
    }

    @Override
    public void shutdown() {
    }

    public void loadRecipes() {
        recipes.clear();
        loadRecipeFile("crafting.yml", true);
        loadRecipeFile("grade_crafting.yml", false);
        plugin.getLogger().info("Loaded " + recipes.size() + " crafting recipes (total).");
    }

    public String unlockRandomRecipe(Player player, int targetGradeId) {
        CraftingData data = getData(player);
        Set<String> unlocked = data.getUnlockedRecipes();

        List<CraftingRecipe> availableCandidates = recipes.values().stream()
                .filter(r -> targetGradeId == 0 || r.getGrade().getId() == targetGradeId)
                .filter(r -> !unlocked.contains(r.getId()))
                .collect(Collectors.toList());

        if (availableCandidates.isEmpty()) return null;

        Collections.shuffle(availableCandidates);
        CraftingRecipe target = availableCandidates.get(0);
        unlockRecipe(player, target.getId());
        return target.getResultItemId();
    }

    private void loadRecipeFile(String fileName, boolean registerStandard) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                try {
                    file.createNewFile();
                } catch (IOException ioException) {
                    plugin.getLogger().warning("Could not create " + fileName);
                }
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String result = section.getString(key + ".result_id");
            int time = section.getInt(key + ".time_seconds");

            Map<String, Integer> ingredients = new HashMap<>();
            ConfigurationSection ingSection = section.getConfigurationSection(key + ".ingredients");
            if (ingSection != null) {
                for (String matKey : ingSection.getKeys(false)) {
                    ingredients.put(matKey, ingSection.getInt(matKey));
                }
            }

            CraftingRecipe baseRecipe = new CraftingRecipe(key, result, time, ingredients, FabricationGrade.STANDARD);
            if (registerStandard) recipes.put(key, baseRecipe);
            generateHigherGradeRecipes(baseRecipe);
        }
    }

    private void generateHigherGradeRecipes(CraftingRecipe base) {
        for (FabricationGrade grade : FabricationGrade.values()) {
            if (grade == FabricationGrade.STANDARD) continue;

            double multiplier = grade.getMultiplier();
            String newId = base.getId() + "_fg" + grade.getId();

            Map<String, Integer> newIngredients = new HashMap<>();
            base.getIngredients().forEach((k, v) -> {
                newIngredients.put(k, (int) Math.ceil(v * multiplier));
            });

            int newTime = (int) (base.getTimeSeconds() * multiplier);

            CraftingRecipe upgradeRecipe = new CraftingRecipe(newId, base.getResultItemId(), newTime, newIngredients, grade);
            recipes.put(newId, upgradeRecipe);
        }
    }

    public void loadPlayer(Player player) {
        dataStore.loadData(player.getUniqueId()).thenAccept(data -> {
            recipeStore.loadUnlockedRecipes(player.getUniqueId()).thenAccept(unlocked -> {
                data.setUnlockedRecipes(unlocked);
                DW.cache().getCache(player.getUniqueId()).set(CraftingData.class, data);
            });
        });
    }

    public void saveAndUnloadPlayer(UUID playerId) {
        CraftingData data = DW.cache().getCache(playerId).get(CraftingData.class);
        if (data != null) {
            dataStore.saveData(data);
            recipeStore.saveUnlockedRecipes(playerId, data.getUnlockedRecipes());
            DW.cache().getCache(playerId).remove(CraftingData.class);
        }
    }

    public CraftingData getData(Player player) {
        CraftingData data = DW.cache().getCache(player.getUniqueId()).get(CraftingData.class);
        return data != null ? data : new CraftingData(player.getUniqueId());
    }

    public List<CraftingRecipe> getRecipesByGrade(FabricationGrade grade) {
        return recipes.values().stream().filter(r -> r.getGrade() == grade).collect(Collectors.toList());
    }

    public void unlockRecipe(Player player, String recipeId) {
        CraftingData data = getData(player);
        if (!data.hasRecipe(recipeId)) {
            data.unlockRecipe(recipeId);
            recipeStore.saveUnlockedRecipes(player.getUniqueId(), data.getUnlockedRecipes());
            player.sendMessage(Component.text("新しいレシピを習得しました！", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            player.sendMessage(Component.text("すでにこのレシピは習得済みです。", NamedTextColor.YELLOW));
        }
    }

    public ItemStack getBlueprintItem(String recipeId) {
        CraftingRecipe recipe = recipes.get(recipeId);
        if (recipe == null) return null;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("製造設計図: " + recipe.getResultItemId() + " (" + recipe.getGrade().getDisplayName() + ")", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("使用してレシピを習得する", NamedTextColor.GRAY),
                Component.text("ID: " + recipeId, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(BLUEPRINT_KEY, PersistentDataType.STRING, recipeId);
        item.setItemMeta(meta);
        return item;
    }

    public boolean startCrafting(Player player, String recipeId) {
        CraftingRecipe recipe = recipes.get(recipeId);
        if (recipe == null) return false;

        CraftingData data = getData(player);

        if (recipe.getGrade() != FabricationGrade.STANDARD) {
            if (!data.hasRecipe(recipeId)) {
                player.sendMessage(Component.text("このレシピはまだ習得していません！設計図が必要です。", NamedTextColor.RED));
                return false;
            }
        }

        if (!hasIngredients(player, recipe.getIngredients())) {
            player.sendMessage(Component.text("素材が不足しています。", NamedTextColor.RED));
            return false;
        }

        consumeIngredients(player, recipe.getIngredients());

        long finishTime = System.currentTimeMillis() + (recipe.getTimeSeconds() * 1000L);
        data.addJob(new CraftingJob(recipe.getId(), recipe.getResultItemId(), finishTime));
        dataStore.saveData(data);

        player.sendMessage(Component.text("製作を開始しました！ (" + recipe.getGrade().getDisplayName() + ")", NamedTextColor.GREEN));
        return true;
    }

    public void claimJob(Player player, UUID jobId) {
        CraftingData data = getData(player);
        Optional<CraftingJob> jobOpt = data.getJobs().stream().filter(j -> j.getJobId().equals(jobId)).findFirst();

        if (jobOpt.isEmpty()) return;
        CraftingJob job = jobOpt.get();

        if (!job.isFinished()) {
            player.sendMessage(Component.text("まだ完成していません。", NamedTextColor.YELLOW));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("インベントリがいっぱいです。", NamedTextColor.RED));
            return;
        }

        CraftingRecipe recipe = recipes.get(job.getRecipeId());
        FabricationGrade grade = (recipe != null) ? recipe.getGrade() : FabricationGrade.STANDARD;

        ItemStack resultItem = Deepwither.getInstance().getItemFactory().getCustomItemStack(job.getResultItemId(), grade);
        if (resultItem == null) {
            player.sendMessage(Component.text("アイテム生成エラー", NamedTextColor.RED));
            return;
        }

        player.getInventory().addItem(resultItem);
        data.removeJob(jobId);
        dataStore.saveData(data);

        player.sendMessage(Component.text("アイテムを受け取りました！", NamedTextColor.GOLD));
    }

    private String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(customIdKey, PersistentDataType.STRING);
    }

    private boolean hasIngredients(Player player, Map<String, Integer> required) {
        Map<String, Integer> currentCounts = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String cid = getCustomId(item);
            if (cid != null) {
                currentCounts.put(cid, currentCounts.getOrDefault(cid, 0) + item.getAmount());
            }
        }
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (currentCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    private void consumeIngredients(Player player, Map<String, Integer> required) {
        Map<String, Integer> toRemove = new HashMap<>(required);
        for (ItemStack item : player.getInventory().getContents()) {
            if (toRemove.isEmpty()) break;
            String cid = getCustomId(item);
            if (cid != null && toRemove.containsKey(cid)) {
                int needed = toRemove.get(cid);
                int amount = item.getAmount();
                if (amount <= needed) {
                    item.setAmount(0);
                    toRemove.put(cid, needed - amount);
                    if (toRemove.get(cid) <= 0) toRemove.remove(cid);
                } else {
                    item.setAmount(amount - needed);
                    toRemove.remove(cid);
                }
            }
        }
    }

    public CraftingRecipe getRecipe(String recipeId) {
        return recipes.get(recipeId);
    }
}
