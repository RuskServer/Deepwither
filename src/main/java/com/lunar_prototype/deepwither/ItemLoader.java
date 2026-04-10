package com.lunar_prototype.deepwither;

import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.*;

public class ItemLoader {
    private static final Random random = new Random();
    private static final String CUSTOM_ID_KEY = "custom_id";
    public static final NamespacedKey RECOVERY_AMOUNT_KEY = new NamespacedKey(Deepwither.getInstance(), "recovery_amount");
    public static final NamespacedKey COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "cooldown_seconds");
    public static final NamespacedKey SKILL_CHANCE_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_chance");
    public static final NamespacedKey SKILL_COOLDOWN_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_cooldown");
    public static final NamespacedKey SKILL_ID_KEY = new NamespacedKey(Deepwither.getInstance(), "onhit_skillid");
    public static final NamespacedKey CHARGE_ATTACK_KEY = new NamespacedKey(Deepwither.getInstance(), "charge_attack");
    public static final NamespacedKey SPECIAL_ACTION_KEY = new NamespacedKey(Deepwither.getInstance(), "special_action_type");
    public static final NamespacedKey IS_WAND = new NamespacedKey(Deepwither.getInstance(), "is_wand");

    // 品質判定用Enum
    enum QualityRank {
        COMMON("普通の"), UNCOMMON("§a良質な"), RARE("§b希少な"), LEGENDARY("§6伝説の");

        private final String displayName;

        QualityRank(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static QualityRank fromRatio(double ratio) {
            if (ratio >= 0.9) return LEGENDARY;
            if (ratio >= 0.6) return RARE;
            if (ratio >= 0.3) return UNCOMMON;
            return COMMON;
        }
    }

    public static Map<StatType, Double> generateRandomModifiers(String rarity) {
        Map<StatType, Double> modifiers = new HashMap<>();

        // レアリティごとの最大数取得
        int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(rarity, 1);
        // 1～最大数の間でランダムな個数を決定
        int modifiersToApply = random.nextInt(maxModifiers) + 1;

        // 重み付きリストの作成
        List<ModifierDefinition> weightedModifiers = new ArrayList<>();
        for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
            for (int j = 0; j < (int) (def.weight * 10); j++) {
                weightedModifiers.add(def);
            }
        }

        Set<StatType> appliedTypes = new HashSet<>();

        for (int m = 0; m < modifiersToApply; m++) {
            if (weightedModifiers.isEmpty()) break;

            ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));

            if (appliedTypes.contains(selectedDef.type)) {
                weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                m--;
                continue;
            }

            double modifierValue = selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat);
            modifiers.put(selectedDef.type, modifierValue);

            appliedTypes.add(selectedDef.type);
            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
        }

        return modifiers;
    }

    private static int generateRandomSocketCount(String rarity) {
        double chance = 0.2; // 基本20%
        int max = 1;

        if (rarity.contains("アンコモン")) {
            chance = 0.3;
            max = 1;
        } else if (rarity.contains("レア")) {
            chance = 0.4;
            max = 2;
        } else if (rarity.contains("エピック")) {
            chance = 0.5;
            max = 2;
        } else if (rarity.contains("レジェンダリー")) {
            chance = 0.6;
            max = 3;
        }

        if (random.nextDouble() < chance) {
            return 1 + random.nextInt(max);
        }
        return 0;
    }

    // ランダムステータスの理論値＆実際値を管理するクラス
    static class RandomStatTracker {
        private double maxTotal = 0;
        private double actualTotal = 0;

        Map<StatType, Double> weightMap = Map.of(
                StatType.CRIT_CHANCE, 0.5,  // 会心倍率は数値が小さいから低めに重み
                StatType.CRIT_DAMAGE, 0.3
                // 必要に応じて追加
        );

        public void add(StatType type, double base, double spread, double actual) {
            double weight = weightMap.getOrDefault(type, 1.0); // デフォルト1.0
            maxTotal += (base + spread) * weight;
            actualTotal += actual * weight;
        }

        public double getRatio() {
            if (maxTotal == 0) return 0;
            return actualTotal / maxTotal;
        }
    }

    // --- 新規追加: モディファイアー関連 ---

    // レアリティごとのモディファイアー最大個数
    private static final Map<String, Integer> MAX_MODIFIERS_BY_RARITY = Map.of(
            "&f&lコモン", 1,
            "&a&lアンコモン", 2,
            "&b&lレア", 3,
            "&d&lエピック", 4,
            "&6&lレジェンダリー", 6
    );

    // 付与可能なモディファイアーとその重み（StatTypeと値の範囲）
    private static final List<ModifierDefinition> MODIFIER_DEFINITIONS = List.of(
            new ModifierDefinition(StatType.ATTACK_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.DEFENSE, 1.0, 2.0, 15.0),
            new ModifierDefinition(StatType.CRIT_CHANCE, 0.5, 1.0, 5.0),
            new ModifierDefinition(StatType.CRIT_DAMAGE, 0.3, 5.0, 30.0),
            new ModifierDefinition(StatType.MAX_HEALTH, 1.0, 10.0, 20.0),
            new ModifierDefinition(StatType.MAGIC_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_RESIST, 1.0, 2.0, 5.0),
            new ModifierDefinition(StatType.PROJECTILE_DAMAGE, 1.0, 2.0, 10.0),
            new ModifierDefinition(StatType.MAGIC_BURST_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.MAGIC_AOE_DAMAGE, 1.0, 3.0, 5.0),
            new ModifierDefinition(StatType.ATTACK_SPEED,0.1,0.1,0.2),
            new ModifierDefinition(StatType.REACH,0.1,0.2,0.5),
            new ModifierDefinition(StatType.MAX_MANA,0.5,10,40),
            new ModifierDefinition(StatType.MOVE_SPEED,0.1,0.001,0.005),
            new ModifierDefinition(StatType.COOLDOWN_REDUCTION,0.2,2,5),
            new ModifierDefinition(StatType.HP_REGEN,0.1,1,3)
    );

    // モディファイアー定義用ヘルパークラス
    static class ModifierDefinition {
        final StatType type;
        final double weight;
        final double minFlat;
        final double maxFlat;

        ModifierDefinition(StatType type, double weight, double minFlat, double maxFlat) {
            this.type = type;
            this.weight = weight;
            this.minFlat = minFlat;
            this.maxFlat = maxFlat;
        }
    }

    private static EquipmentSlot getSlotFromMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);

        // 防具
        if (name.contains("helmet") || name.contains("skull") || name.contains("head")) return EquipmentSlot.HEAD;
        if (name.contains("chestplate")) return EquipmentSlot.CHEST;
        if (name.contains("leggings")) return EquipmentSlot.LEGS;
        if (name.contains("boots")) return EquipmentSlot.FEET;

        // 武器/ツール (メインハンド)
        if (name.contains("sword") || name.contains("axe") || name.contains("pickaxe") || name.contains("hoe") || name.contains("shovel")) return EquipmentSlot.HAND;

        // その他（例: オフハンド）
        if (material == Material.SHIELD) return EquipmentSlot.OFF_HAND;

        return null;
    }

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder, @Nullable FabricationGrade grade) {
        File[] files = itemFolder.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains(id)) {
                // loadItemsにgradeを渡す
                Map<String, ItemStack> map = loadItems(config, factory, grade);
                return map.get(id);
            }
        }
        return null;
    }

    // 既存互換用
    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder) {
        return loadSingleItem(id, factory, itemFolder, FabricationGrade.STANDARD);
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory, @Nullable FabricationGrade forceGrade) {
        Map<String, ItemStack> result = new HashMap<>();

        for (String key : config.getKeys(false)) {
            try {
                String materialName = config.getString(key + ".material", "STONE");
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    System.err.println("Invalid material for item: " + key);
                    continue;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    System.err.println("ItemMeta is null for: " + key);
                    continue;
                }

                if (material == Material.PLAYER_HEAD) {
                    SkullMeta skullMeta = (SkullMeta) meta;
                    String textureUrl = config.getString(key + ".texture-url");
                    if (textureUrl != null) {
                        try {
                            // Create a custom PlayerProfile with the texture URL
                            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                            PlayerTextures textures = profile.getTextures();
                            textures.setSkin(new URL(textureUrl));
                            profile.setTextures(textures);
                            skullMeta.setOwnerProfile(profile);
                        } catch (Exception e) {
                            System.err.println("Failed to set custom texture for player head: " + key);
                            e.printStackTrace();
                        }
                    }
                }

                int custom_model_data = config.getInt(key + ".custom_mode_data");
                if (custom_model_data != 0){
                    meta.setCustomModelData(custom_model_data);
                }

                FabricationGrade grade = (forceGrade != null) ? forceGrade : FabricationGrade.STANDARD;
                double multiplier = grade.getMultiplier();

                // ランダムステータスの品質判定用トラッカー初期化
                RandomStatTracker tracker = new RandomStatTracker();

                StatMap baseStats = new StatMap(); // ★ ここには「等級倍率適用前」の値を入れる

                if (config.isConfigurationSection(key + ".stats")) {
                    for (String statKey : config.getConfigurationSection(key + ".stats").getKeys(false)) {
                        StatType type = StatType.valueOf(statKey.toUpperCase());
                        // flat値
                        double flat;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".flat")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".flat.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".flat.spread", 0);
                            flat = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type, base, spread, flat);
                        } else {
                            flat = config.getDouble(key + ".stats." + statKey + ".flat", 0);
                            tracker.add(type, flat, 0, flat);
                        }
                        baseStats.setFlat(type, flat);

                        // percent値
                        double percent;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".percent")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".percent.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".percent.spread", 0);
                            percent = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type, base, spread, percent);
                        } else {
                            percent = config.getDouble(key + ".stats." + statKey + ".percent", 0);
                            tracker.add(type, percent, 0, percent);
                        }
                        baseStats.setPercent(type, percent);
                    }
                }

                String itemType = config.getString(key + ".type", null);
                String normalizedType = (itemType != null) ? itemType.trim().toLowerCase() : "";
                boolean isGear = itemType != null
                        && !normalizedType.contains("素材")
                        && !normalizedType.contains("アーティファクト")
                        && !normalizedType.contains("スクロール")
                        && !normalizedType.contains("消費アイテム")
                        && !normalizedType.contains("コンパニオン");

                // ----------------------------------------------------
                // --- ランダム生成ロジック (モディファイアー & ソケット) ---
                // ----------------------------------------------------
                boolean disableModifiers = config.getBoolean(key + ".disable_modifiers", false);
                Map<StatType, Double> modifiers = new HashMap<>();
                String rarity = config.getString(key + ".rarity", "コモン");
                int socketsMax = config.getInt(key + ".sockets_max", 0);

                if (!disableModifiers && isGear) {
                    // 1. 刻印レアリティの判定 (約3%)
                    if (random.nextDouble() < 0.03) {
                        rarity = "&7&l刻印";
                        socketsMax = 3 + random.nextInt(3); // 3-5個
                        //Bukkit.getConsoleSender().sendMessage(Component.text("[RuneSystem] 刻印アイテムを生成しました: " + key + " (ソケット: " + socketsMax + ")", NamedTextColor.YELLOW));
                    } else {
                        // 2. 通常のモディファイアー生成
                        String lookupKey = rarity;
                        if (!lookupKey.startsWith("&")) {
                            if (lookupKey.equals("コモン")) lookupKey = "&f&lコモン";
                            else if (lookupKey.equals("アンコモン")) lookupKey = "&a&lアンコモン";
                            else if (lookupKey.equals("レア")) lookupKey = "&b&lレア";
                            else if (lookupKey.equals("エピック")) lookupKey = "&d&lエピック";
                            else if (lookupKey.equals("レジェンダリー")) lookupKey = "&6&lレジェンダリー";
                        }

                        int maxModifiers = MAX_MODIFIERS_BY_RARITY.getOrDefault(lookupKey, 1);
                        int modifiersToApply = random.nextInt(maxModifiers) + 1;
                        List<ModifierDefinition> weightedModifiers = new ArrayList<>();
                        for (ModifierDefinition def : MODIFIER_DEFINITIONS) {
                            for (int j = 0; j < (int) (def.weight * 10); j++) weightedModifiers.add(def);
                        }
                        Set<StatType> appliedTypes = new HashSet<>();
                        for (int m = 0; m < modifiersToApply; m++) {
                            if (weightedModifiers.isEmpty()) break;
                            ModifierDefinition selectedDef = weightedModifiers.get(random.nextInt(weightedModifiers.size()));
                            if (appliedTypes.contains(selectedDef.type)) {
                                weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                                m--; continue;
                            }
                            modifiers.put(selectedDef.type, selectedDef.minFlat + random.nextDouble() * (selectedDef.maxFlat - selectedDef.minFlat));
                            appliedTypes.add(selectedDef.type);
                            weightedModifiers.removeIf(def -> def.type == selectedDef.type);
                        }
                        // 3. 通常のランダムソケット生成
                        if (socketsMax == 0) {
                            socketsMax = generateRandomSocketCount(rarity);
                            if (socketsMax > 0) {
                                //Bukkit.getConsoleSender().sendMessage(Component.text("[RuneSystem] ソケットを付与しました: " + key + " (" + rarity + " / " + socketsMax + "個)", NamedTextColor.GRAY));
                            }
                        }
                    }
                }

                // 品質ランク判定
                QualityRank rank = QualityRank.fromRatio(tracker.getRatio());

                boolean droppable = config.getBoolean(key + ".droppable", false);
                if (droppable) {
                    factory.rarityPools.computeIfAbsent(rarity, k -> new ArrayList<>()).add(key);
                }

                // PDCに保存 (Lore生成の前に必要)
                NamespacedKey customIdKey = new NamespacedKey(Deepwither.getInstance(), "custom_id");
                meta.getPersistentDataContainer().set(customIdKey, PersistentDataType.STRING, key);

                if (socketsMax > 0) {
                    meta.getPersistentDataContainer().set(new NamespacedKey("deepwither", "sockets_max"), PersistentDataType.INTEGER, socketsMax);
                }
                if (config.getBoolean(key + ".is_rune", false)) {
                    meta.getPersistentDataContainer().set(new NamespacedKey("deepwither", "is_rune"), PersistentDataType.BYTE, (byte) 1);
                }

                // 名前の設定
                meta.setDisplayName(config.getString(key + ".name", key));

                if (config.getBoolean(key + ".unbreaking", false)) {
                    meta.setUnbreakable(true);
                }
                String chargeType = config.getString(key + ".charge_type", null);
                if (chargeType != null) {
                    meta.getPersistentDataContainer().set(CHARGE_ATTACK_KEY, PersistentDataType.STRING, chargeType);
                }
                String companiontype = config.getString(key + ".companion_type", null);
                if (companiontype != null){
                    meta.getPersistentDataContainer().set(Deepwither.getInstance().getCompanionManager().COMPANION_ID_KEY,PersistentDataType.STRING, companiontype);
                }
                String raid_boss_id = config.getString(key + ".raid_boss_id", null);
                if (raid_boss_id != null){
                    meta.getPersistentDataContainer().set(new NamespacedKey(Deepwither.getInstance(), "raid_boss_id"), PersistentDataType.STRING, raid_boss_id);
                }
                String specialAction = config.getString(key + ".special_action");
                if (specialAction != null) {
                    meta.getPersistentDataContainer().set(SPECIAL_ACTION_KEY, PersistentDataType.STRING, specialAction.toUpperCase());
                }
                if ((itemType != null && itemType.contains("杖")) || config.getBoolean(key + ".is_wand")) {
                    meta.getPersistentDataContainer().set(IS_WAND, PersistentDataType.BOOLEAN, true);
                }
                String setPartner = config.getString(key + ".set_partner");
                if (setPartner != null) {
                    meta.getPersistentDataContainer().set(ItemFactory.SET_PARTNER_KEY, PersistentDataType.STRING, setPartner);
                }

                item.setItemMeta(meta);
                List<String> flavorText = config.getStringList(key + ".flavor");

                // 重要: Lore + PDC 書き込み
                String artifactFullsetType = config.getString(key + ".artifact_fullset_type", null);
                item = factory.applyStatsToItem(item, baseStats, modifiers, itemType, flavorText, tracker, rarity, artifactFullsetType, grade);

                // --- 以下の古い冗長な処理は削除 ---

                double recoveryAmount = config.getDouble(key + ".recovery-amount", 0.0);
                int cooldownSeconds = config.getInt(key + ".cooldown-seconds", 0);

                if (recoveryAmount > 0.0) {
                    ItemMeta meta2 = item.getItemMeta();
                    PersistentDataContainer container = meta2.getPersistentDataContainer();

                    // 回復量（例: DOUBLEで保存）
                    container.set(RECOVERY_AMOUNT_KEY, PersistentDataType.DOUBLE, recoveryAmount);

                    // クールダウン（例: INTEGERで保存）
                    if (cooldownSeconds > 0) {
                        container.set(COOLDOWN_KEY, PersistentDataType.INTEGER, cooldownSeconds);
                    }

                    item.setItemMeta(meta2);
                }

                //   recipe_book_grade: 2  (Grade 2のレシピからランダム)
                //   recipe_book_grade: 0  (全Gradeからランダム)
                int recipeBookGrade = config.getInt(key + ".recipe_book_grade", -1);

                if (recipeBookGrade >= -1) { // -1以外なら設定ありとみなす（0も許可）
                    // recipe_book_gradeキーが存在しない場合は -1 が返るが、
                    // 明示的に設定したい場合は getInt のデフォルト値をチェックする必要があるため、
                    // config.contains チェックの方が安全です。
                    if (config.contains(key + ".recipe_book_grade")) {
                        ItemMeta metaBook = item.getItemMeta();
                        metaBook.getPersistentDataContainer().set(ItemFactory.RECIPE_BOOK_KEY, PersistentDataType.INTEGER, recipeBookGrade);

                        String gradeName = (recipeBookGrade == 0) ? "全等級" : "等級 " + recipeBookGrade;
                        metaBook.lore().add(Component.text("右クリックで使用: ", NamedTextColor.GOLD)
                                .append(Component.text("未習得の" + gradeName + "レシピを獲得", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false));

                        item.setItemMeta(metaBook);
                    }
                }

                if (config.isConfigurationSection(key + ".on_hit")) {
                    ItemMeta meta3 = item.getItemMeta();
                    PersistentDataContainer container = meta3.getPersistentDataContainer();

                    double chance = config.getDouble(key + ".on_hit.chance", 0.0);
                    int cooldown = config.getInt(key + ".on_hit.cooldown", 0);
                    String skillId = config.getString(key + ".on_hit.mythic_skill_id", null);

                    if (skillId != null) {
                        // PDCに保存
                        container.set(SKILL_CHANCE_KEY, PersistentDataType.DOUBLE, chance);
                        container.set(SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER, cooldown);
                        container.set(SKILL_ID_KEY, PersistentDataType.STRING, skillId);

                        item.setItemMeta(meta3);
                        // System.out.println(key + "にOnHitスキル: " + skillId + " (確率: " + chance + "%) を設定"); // デバッグ用
                    }
                }

                int durability = config.getInt(key + ".durability",0);
                if (durability > 0){
                    item.setData(DataComponentTypes.MAX_DAMAGE,durability);
                }

                String customArmorAssetId = config.getString(key + ".custom_armor");

                if (customArmorAssetId != null) {
                    // 1. 適切な EquipmentSlot を Material から決定する
                    EquipmentSlot slot = getSlotFromMaterial(material);

                    if (slot != null) {

                        NamespacedKey custom_armor_id = NamespacedKey.minecraft(customArmorAssetId);

                        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(custom_armor_id).build());
                    } else {
                        System.err.println("Custom Armor Asset IDが設定されていますが、Material (" + materialName + ") は認識可能な防具/武器ではありません。");
                    }
                }

                if (config.contains(key + ".equipable")) {
                    ConfigurationSection equipSection = config.getConfigurationSection(key + ".equipable");
                    if (equipSection != null) {
                        // 装備スロットの判定 (HEAD, CHEST, LEGS, FEET, etc.)
                        String slotName = equipSection.getString("slot", "HEAD");
                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName.toUpperCase());

                        // Equippableコンポーネントの構築
                        Equippable equippable = Equippable.equippable(slot)
                                .assetId(equipSection.contains("model") ? NamespacedKey.fromString(equipSection.getString("model")) : null)
                                .dispensable(equipSection.getBoolean("dispensable", true))
                                .swappable(equipSection.getBoolean("swappable", true))
                                .cameraOverlay(equipSection.contains("overlay") ? NamespacedKey.fromString(equipSection.getString("overlay")) : null)
                                .build();

                        // コンポーネントをアイテムに適用
                        item.setData(DataComponentTypes.EQUIPPABLE, equippable);
                    }
                }

                String armortrim = config.getString(key + ".armortrim");
                String armortrimmaterial = config.getString(key + ".armortrimmaterial");
                if (armortrim != null && armortrimmaterial != null) {
                    // 1. 文字列をNamespacedKeyに変換
                    NamespacedKey trimKey = NamespacedKey.minecraft(armortrim);
                    NamespacedKey materialKey = NamespacedKey.minecraft(armortrimmaterial);

                    // 2. Registry.TRIM_PATTERNS と Registry.TRIM_MATERIALS からオブジェクトを取得
                    //    注: Registryクラスは Paper 1.20.5+ で非推奨になる可能性があります。
                    TrimPattern trimPattern = Bukkit.getRegistry(TrimPattern.class).get(trimKey);
                    TrimMaterial trimMaterial = Bukkit.getRegistry(TrimMaterial.class).get(materialKey);

                    // 取得したオブジェクトがnullでないことを確認
                    if (trimPattern != null && trimMaterial != null) {
                        // 3. ArmorTrimオブジェクトを作成
                        ArmorTrim armorTrim = new ArmorTrim(trimMaterial, trimPattern);

                        // 4. DataComponentTypes.TRIM に ArmorTrim を直接セット
                        item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(armorTrim));

                        // Debugging message to confirm the trim was applied.
                        //System.out.println("Applied armor trim: " + armortrim + " with material: " + armortrimmaterial + " to item " + key);
                    } else {
                        System.err.println("Failed to get trim pattern or material for item: " + key);
                    }
                }

                List<String> canBreakBlocks = config.getStringList(key + ".can_destroy");

                if (!canBreakBlocks.isEmpty()) {

                    RegistryKey<BlockType> blockRegistryKey = RegistryKey.BLOCK;
                    Registry<BlockType> blockRegistry = Bukkit.getRegistry(BlockType.class);
                    List<TypedKey<BlockType>> typedBlockKeys = new ArrayList<>();

                    for (String blockId : canBreakBlocks) {
                        NamespacedKey blockKey;
                        if (!blockId.contains(":")) {
                            blockKey = NamespacedKey.minecraft(blockId.toLowerCase());
                        } else {
                            blockKey = NamespacedKey.fromString(blockId);
                        }

                        if (blockKey != null && blockRegistry.get(blockKey) != null) {
                            typedBlockKeys.add(TypedKey.create(blockRegistryKey, blockKey));
                        } else {
                            System.err.println("無効なブロックID: " + blockId);
                        }
                    }

                    if (!typedBlockKeys.isEmpty()) {
                        // 【修正1】変数の型を RegistryKeySet に変更
                        // RegistrySet.keySet(...) は RegistryKeySet を返します
                        RegistryKeySet<BlockType> blockKeySet = RegistrySet.keySet(blockRegistryKey, typedBlockKeys);

                        // 【修正2】BlockPredicate.predicate() からビルダーを開始
                        // blocks() は RegistryKeySet を要求します
                        BlockPredicate blockPredicate = BlockPredicate.predicate()
                                .blocks(blockKeySet)
                                .build();

                        // 【修正3】List.of() でラップして渡す
                        // itemAdventurePredicate は List<BlockPredicate> を要求します
                        ItemAdventurePredicate canBreakPredicate = ItemAdventurePredicate.itemAdventurePredicate(List.of(blockPredicate));

                        item.setData(DataComponentTypes.CAN_BREAK, canBreakPredicate);
                    }
                }

                result.put(key, item);
            } catch (Exception e) {
                System.err.println("Error loading item '" + key + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        return result;
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory) {
        return loadItems(config, factory, null);
    }
}
