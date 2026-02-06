package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.FabricationGrade;
import com.lunar_prototype.deepwither.ItemFactory;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.ChatColor;
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

@DependsOn({ItemFactory.class})
public class CraftingManager implements IManager {

    private final Deepwither plugin;
    private CraftingDataStore dataStore;
    private PlayerRecipeJsonStore recipeStore; // JSONストア
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();
    private final Map<UUID, CraftingData> sessionCache = new ConcurrentHashMap<>();

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
        for (UUID uuid : new HashSet<>(sessionCache.keySet())) {
            saveAndUnloadPlayer(uuid);
        }
    }

    public void loadRecipes() {
        recipes.clear();

        // 1. 通常のレシピ (crafting.yml)
        // 基本レシピ(Standard)も登録し、上位等級も自動生成する
        loadRecipeFile("crafting.yml", true);

        // 2. 上位等級専用レシピ (grade_crafting.yml)
        // 基本レシピ(Standard)は登録せず、上位等級のみ生成・登録する
        loadRecipeFile("grade_crafting.yml", false);

        plugin.getLogger().info("Loaded " + recipes.size() + " crafting recipes (total).");
    }

    /**
     * 指定された等級(Grade)の中から、まだ持っていないレシピをランダムに1つ習得させる。
     * @param player 対象プレイヤー
     * @param targetGradeId 対象とする等級ID (0の場合は全等級から抽選)
     * @return 習得したレシピの名前(ResultItemId)。習得できるものがなかった場合は null
     */
    public String unlockRandomRecipe(Player player, int targetGradeId) {
        CraftingData data = getData(player);
        Set<String> unlocked = data.getUnlockedRecipes();

        // 1. 条件に合う未習得レシピをリストアップ
        List<CraftingRecipe> availableCandidates = recipes.values().stream()
                // 指定Gradeと一致するか (0なら全Grade対象)
                .filter(r -> targetGradeId == 0 || r.getGrade().getId() == targetGradeId)
                // Grade 1 (STANDARD) はデフォルト解放済み扱いなら除外する等の調整も可能だが、
                // ここでは「習得済みリスト」になければ対象とする
                .filter(r -> !unlocked.contains(r.getId()))
                .collect(Collectors.toList());

        if (availableCandidates.isEmpty()) {
            return null; // 習得できるレシピがない
        }

        // 2. シャッフルして1つ選ぶ
        Collections.shuffle(availableCandidates);
        CraftingRecipe target = availableCandidates.get(0);

        // 3. 習得処理
        unlockRecipe(player, target.getId());

        // 戻り値として、アイテム名（またはレシピID）を返す
        return target.getResultItemId();
    }

    /**
     * 指定したファイルからレシピを読み込むヘルパーメソッド
     * @param fileName ファイル名
     * @param registerStandard 基本(Grade 1)レシピをマップに登録するかどうか
     */
    private void loadRecipeFile(String fileName, boolean registerStandard) {
        File file = new File(plugin.getDataFolder(), fileName);

        // リソースが存在しない場合の処理 (grade_crafting.ymlがjar内にない場合は空ファイル作成などの対応が必要かも)
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // jar内にファイルがない場合は空のファイルを作成するなどのフォールバック
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

            // ベースとなるレシピオブジェクトを作成
            CraftingRecipe baseRecipe = new CraftingRecipe(key, result, time, ingredients, FabricationGrade.STANDARD);

            // フラグが true の場合のみ、基本(Standard)レシピとしてメインリストに登録
            if (registerStandard) {
                recipes.put(key, baseRecipe);
            }

            // --- 上位等級レシピの自動生成 ---
            // baseRecipeの情報を元にHQ/Masterpiece等を生成し、それらは無条件でrecipesに登録する
            generateHigherGradeRecipes(baseRecipe);
        }
    }

    private void generateHigherGradeRecipes(CraftingRecipe base) {
        // Grade 2(Industrial) から 5(Aetherbound) まで生成
        for (FabricationGrade grade : FabricationGrade.values()) {
            if (grade == FabricationGrade.STANDARD) continue; // Grade 1はスキップ

            double multiplier = grade.getMultiplier();
            String newId = base.getId() + "_fg" + grade.getId(); // 例: sword_fg2

            // 素材コストを倍率で増やす (切り上げ)
            Map<String, Integer> newIngredients = new HashMap<>();
            base.getIngredients().forEach((k, v) -> {
                newIngredients.put(k, (int) Math.ceil(v * multiplier));
            });

            // 製作時間も倍率で増やす
            int newTime = (int) (base.getTimeSeconds() * multiplier);

            CraftingRecipe upgradeRecipe = new CraftingRecipe(
                    newId,
                    base.getResultItemId(),
                    newTime,
                    newIngredients,
                    grade
            );
            recipes.put(newId, upgradeRecipe);
        }
    }

    // --- プレイヤーデータ管理 (YAML + JSON統合) ---
    public void loadPlayer(Player player) {
        // 1. 基本データ(Job等)をYAMLからロード
        dataStore.loadData(player.getUniqueId()).thenAccept(data -> {
            // 2. レシピデータをJSONからロードして結合
            recipeStore.loadUnlockedRecipes(player.getUniqueId()).thenAccept(unlocked -> {
                data.setUnlockedRecipes(unlocked);
                sessionCache.put(player.getUniqueId(), data);
            });
        });
    }

    public void saveAndUnloadPlayer(UUID playerId) {
        CraftingData data = sessionCache.remove(playerId);
        if (data != null) {
            // Job等はYAMLへ
            dataStore.saveData(data);
            // 解放レシピはJSONへ
            recipeStore.saveUnlockedRecipes(playerId, data.getUnlockedRecipes());
        }
    }

    public CraftingData getData(Player player) {
        return sessionCache.getOrDefault(player.getUniqueId(), new CraftingData(player.getUniqueId()));
    }

    // 特定のGradeのレシピのみ取得
    public List<CraftingRecipe> getRecipesByGrade(FabricationGrade grade) {
        return recipes.values().stream()
                .filter(r -> r.getGrade() == grade)
                .collect(Collectors.toList());
    }

    // レシピ解放処理
    public void unlockRecipe(Player player, String recipeId) {
        CraftingData data = getData(player);
        if (!data.hasRecipe(recipeId)) {
            data.unlockRecipe(recipeId);
            // 即時保存（クラッシュ対策）
            recipeStore.saveUnlockedRecipes(player.getUniqueId(), data.getUnlockedRecipes());
            player.sendMessage(ChatColor.GREEN + "新しいレシピを習得しました！");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            player.sendMessage(ChatColor.YELLOW + "すでにこのレシピは習得済みです。");
        }
    }

    // 設計図アイテム生成ヘルパー
    public ItemStack getBlueprintItem(String recipeId) {
        CraftingRecipe recipe = recipes.get(recipeId);
        if (recipe == null) return null;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "製造設計図: " + recipe.getResultItemId() + " (" + recipe.getGrade().getDisplayName() + ")");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "使用してレシピを習得する",
                ChatColor.DARK_GRAY + "ID: " + recipeId
        ));
        meta.getPersistentDataContainer().set(BLUEPRINT_KEY, PersistentDataType.STRING, recipeId);
        item.setItemMeta(meta);
        return item;
    }

    // --- クラフト処理 ---
    public boolean startCrafting(Player player, String recipeId) {
        CraftingRecipe recipe = recipes.get(recipeId);
        if (recipe == null) return false;

        CraftingData data = getData(player);

        // Grade 1以外はレシピ習得チェック
        if (recipe.getGrade() != FabricationGrade.STANDARD) {
            if (!data.hasRecipe(recipeId)) {
                player.sendMessage(ChatColor.RED + "このレシピはまだ習得していません！設計図が必要です。");
                return false;
            }
        }

        // 1. 素材チェック
        if (!hasIngredients(player, recipe.getIngredients())) {
            player.sendMessage(ChatColor.RED + "素材が不足しています。");
            return false;
        }

        // 2. 素材消費
        consumeIngredients(player, recipe.getIngredients());

        // 3. ジョブ追加 (JobデータにはGrade情報は不要、ResultIDとレシピから生成時にGrade適用)
        // ここ重要: Jobが完了したときにアイテムを生成する際、レシピIDからGradeを再特定するか、JobにGradeを持たせるか。
        // CraftingJobはrecipeIdを持っているので、そこからrecipes.get(id)すればGradeはわかる。

        long finishTime = System.currentTimeMillis() + (recipe.getTimeSeconds() * 1000L);
        data.addJob(new CraftingJob(recipe.getId(), recipe.getResultItemId(), finishTime));

        // 4. 保存
        dataStore.saveData(data);

        player.sendMessage(ChatColor.GREEN + "製作を開始しました！ (" + recipe.getGrade().getDisplayName() + ")");
        return true;
    }

    public void claimJob(Player player, UUID jobId) {
        CraftingData data = getData(player);
        Optional<CraftingJob> jobOpt = data.getJobs().stream().filter(j -> j.getJobId().equals(jobId)).findFirst();

        if (jobOpt.isEmpty()) return;
        CraftingJob job = jobOpt.get();

        if (!job.isFinished()) {
            player.sendMessage(ChatColor.YELLOW + "まだ完成していません。");
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "インベントリがいっぱいです。");
            return;
        }

        // レシピ情報からGradeを取得
        CraftingRecipe recipe = recipes.get(job.getRecipeId());
        FabricationGrade grade = (recipe != null) ? recipe.getGrade() : FabricationGrade.STANDARD;

        // アイテム生成 (Gradeを指定して生成)
        ItemStack resultItem = Deepwither.getInstance().getItemFactory().getCustomItemStack(job.getResultItemId(), grade);

        if (resultItem == null) {
            player.sendMessage(ChatColor.RED + "アイテム生成エラー");
            return;
        }

        player.getInventory().addItem(resultItem);
        data.removeJob(jobId);
        dataStore.saveData(data);

        player.sendMessage(ChatColor.GOLD + "アイテムを受け取りました！");
    }

    // --- インベントリ操作ヘルパー (変更なし) ---
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