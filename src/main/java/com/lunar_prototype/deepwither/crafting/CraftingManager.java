package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.DatabaseManager;
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
public class CraftingManager implements IManager, com.lunar_prototype.deepwither.api.playerdata.IPlayerDataHandler {

    private final Deepwither plugin;
    private CraftingDataStore dataStore;
    private PlayerRecipeJsonStore recipeStore;
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

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
        // grade_crafting.yml の読み込みは等級システム廃止のため停止
        plugin.getLogger().info("Loaded " + recipes.size() + " crafting recipes.");
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

            int requiredCraftLevel = section.getInt(key + ".required_craft_level", 0);

            CraftingRecipe baseRecipe = new CraftingRecipe(key, result, time, ingredients, requiredCraftLevel);
            if (registerStandard) recipes.put(key, baseRecipe);
            // generateHigherGradeRecipes は等級システム廃止のため呼ばない
        }
    }

    // generateHigherGradeRecipes は等級システム廃止のため削除済み

    @Override
    public java.util.concurrent.CompletableFuture<Void> loadData(UUID uuid, com.lunar_prototype.deepwither.core.PlayerCache cache) {
        return dataStore.loadData(uuid).thenCompose(data -> 
            recipeStore.loadUnlockedRecipes(uuid).thenAccept(unlocked -> {
                data.setUnlockedRecipes(unlocked);
                cache.set(com.lunar_prototype.deepwither.crafting.CraftingData.class, data);
            })
        ).thenCompose(unused -> loadCraftSkillAsync(uuid, cache));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> saveData(UUID uuid, com.lunar_prototype.deepwither.core.PlayerCache cache) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            com.lunar_prototype.deepwither.crafting.CraftingData data = cache.get(com.lunar_prototype.deepwither.crafting.CraftingData.class);
            if (data != null) {
                dataStore.saveData(data);
                recipeStore.saveUnlockedRecipes(uuid, data.getUnlockedRecipes());
            }
            CraftingSkillData skillData = cache.get(CraftingSkillData.class);
            if (skillData != null) {
                saveCraftSkill(uuid, skillData);
            }
        }, plugin.getAsyncExecutor());
    }

    public CraftingData getData(Player player) {
        CraftingData data = DW.cache().getCache(player.getUniqueId()).get(CraftingData.class);
        return data != null ? data : new CraftingData(player.getUniqueId());
    }

    /** 等級システム廃止のため grade 引数は無視し、全レシピを返す。 */
    public List<CraftingRecipe> getRecipesByGrade(FabricationGrade grade) {
        return new ArrayList<>(recipes.values());
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
        meta.displayName(Component.text("製造設計図: " + recipe.getResultItemId(), NamedTextColor.AQUA));
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

        // クラフトLvチェック
        int playerCraftLv = getCraftLevel(player);
        if (playerCraftLv < recipe.getRequiredCraftLevel()) {
            player.sendMessage(
                Component.text("クラフトLv が不足しています！", NamedTextColor.RED)
                    .append(Component.text(" (必要: Lv" + recipe.getRequiredCraftLevel()
                        + " / あなた: Lv" + playerCraftLv + ")", NamedTextColor.YELLOW))
            );
            return false;
        }

        CraftingData data = getData(player);

        if (!hasIngredients(player, recipe.getIngredients())) {
            player.sendMessage(Component.text("素材が不足しています。", NamedTextColor.RED));
            return false;
        }

        consumeIngredients(player, recipe.getIngredients());

        long finishTime = System.currentTimeMillis() + (recipe.getTimeSeconds() * 1000L);
        data.addJob(new CraftingJob(recipe.getId(), recipe.getResultItemId(), finishTime));
        dataStore.saveData(data);

        player.sendMessage(Component.text("製作を開始しました！", NamedTextColor.GREEN));
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

        ItemStack resultItem = Deepwither.getInstance().getItemFactory().getCustomItemStack(job.getResultItemId());
        if (resultItem == null) {
            player.sendMessage(Component.text("アイテム生成エラー", NamedTextColor.RED));
            return;
        }

        player.getInventory().addItem(resultItem);
        data.removeJob(jobId);
        dataStore.saveData(data);

        player.sendMessage(Component.text("アイテムを受け取りました！", NamedTextColor.GOLD));

        // クラフトスキル経験値付与 (10〜15 exp)
        addCraftExp(player, 10 + random.nextInt(6));
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

    // ===== クラフトスキル関連 =====

    /** プレイヤーの現在クラフトLvを返す。データがない場合は 1 を返す。 */
    public int getCraftLevel(Player player) {
        CraftingSkillData data = DW.cache().getCache(player.getUniqueId()).get(CraftingSkillData.class);
        return data != null ? data.getCraftLevel() : 1;
    }

    /** プレイヤーのクラフトスキルデータを返す。なければ新規作成。 */
    public CraftingSkillData getCraftSkillData(Player player) {
        CraftingSkillData data = DW.cache().getCache(player.getUniqueId()).get(CraftingSkillData.class);
        return data != null ? data : new CraftingSkillData();
    }

    /**
     * クラフト経験値を加算し、レベルアップした場合にメッセージを送る。
     */
    private void addCraftExp(Player player, double amount) {
        com.lunar_prototype.deepwither.core.PlayerCache cache = DW.cache().getCache(player.getUniqueId());
        CraftingSkillData data = cache.get(CraftingSkillData.class);
        if (data == null) data = new CraftingSkillData();

        int before = data.getCraftLevel();
        boolean leveledUp = data.addExp(amount);
        int after = data.getCraftLevel();
        cache.set(CraftingSkillData.class, data);

        // 経験値獲得メッセージ
        player.sendMessage(
            Component.text("[クラフト] ", net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA)
                .append(Component.text("+" + (int) amount + " exp", net.kyori.adventure.text.format.NamedTextColor.AQUA))
                .append(Component.text(" (Lv" + after + " / " + (int) data.getCraftExp() + "/"
                    + data.getRequiredExp() + ")", net.kyori.adventure.text.format.NamedTextColor.GRAY))
        );

        if (leveledUp) {
            if (after >= CraftingSkillData.getMaxLevel()) {
                player.sendMessage(
                    Component.text("★ クラフトスキルが最大レベル(Lv100)に達しました！",
                        net.kyori.adventure.text.format.NamedTextColor.GOLD,
                        net.kyori.adventure.text.format.TextDecoration.BOLD)
                );
            } else {
                player.sendMessage(
                    Component.text("★ クラフトスキルが Lv" + before + " → Lv" + after + " にアップしました！",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN)
                );
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

            // 非同期でDBに保存
            final CraftingSkillData toSave = data;
            plugin.getAsyncExecutor().execute(() -> saveCraftSkill(player.getUniqueId(), toSave));
        }
    }

    /** クラフトスキルデータを DB から非同期でロードし PlayerCache にセット */
    private java.util.concurrent.CompletableFuture<Void> loadCraftSkillAsync(UUID uuid,
            com.lunar_prototype.deepwither.core.PlayerCache cache) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (java.sql.Connection conn = plugin.get(DatabaseManager.class).getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT craft_level, craft_exp FROM player_levels WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int lv = rs.getInt("craft_level");
                        double exp = rs.getDouble("craft_exp");
                        // NULL の場合 (列が0として返る) はデフォルト値に
                        if (lv <= 0) lv = 1;
                        cache.set(CraftingSkillData.class, new CraftingSkillData(lv, exp));
                    } else {
                        cache.set(CraftingSkillData.class, new CraftingSkillData());
                    }
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("[CraftingManager] クラフトスキルのロードに失敗: " + e.getMessage());
                cache.set(CraftingSkillData.class, new CraftingSkillData());
            }
        }, plugin.getAsyncExecutor());
    }

    /** クラフトスキルデータを DB に保存 */
    private void saveCraftSkill(UUID uuid, CraftingSkillData data) {
        try (java.sql.Connection conn = plugin.get(DatabaseManager.class).getConnection()) {
            // player_levels に行があるか確認 (LevelManager は別途管理しているが、列だけ更新)
            boolean exists;
            try (java.sql.PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT 1 FROM player_levels WHERE uuid = ?")) {
                checkPs.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "UPDATE player_levels SET craft_level = ?, craft_exp = ? WHERE uuid = ?")) {
                    ps.setInt(1, data.getCraftLevel());
                    ps.setDouble(2, data.getCraftExp());
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } else {
                // 行がない場合は INSERT (LevelManager 初期化前にセーブが走ったケース)
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_levels (uuid, \"level\", exp, craft_level, craft_exp) VALUES (?,1,0,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, data.getCraftLevel());
                    ps.setDouble(3, data.getCraftExp());
                    ps.executeUpdate();
                }
            }
        } catch (java.sql.SQLException e) {
            plugin.getLogger().warning("[CraftingManager] クラフトスキルの保存に失敗: " + e.getMessage());
        }
    }
}
