package com.lunar_prototype.deepwither;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import jdk.jfr.DataAmount;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.*;

public class ItemFactory implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final Map<String, ItemStack> itemMap = new HashMap<>();
    private final NamespacedKey statKey = new NamespacedKey("rpgstats", "statmap");

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllItems();
        PluginCommand command = plugin.getCommand("giveitem");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void loadAllItems() {
        File itemFolder = new File(plugin.getDataFolder(), "items");
        if (!itemFolder.exists()) itemFolder.mkdirs();

        for (File file : Objects.requireNonNull(itemFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Map<String, ItemStack> loaded = ItemLoader.loadItems(config,this);
            itemMap.putAll(loaded);
        }
    }

    public ItemStack applyStatsToItem(ItemStack item, StatMap stats, @Nullable String itemType, @Nullable List<String> flavorText, ItemLoader.RandomStatTracker tracker,@Nullable String rarity) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Lore 更新（タイプとフレーバーテキスト付き）
        meta.setLore(LoreBuilder.build(stats, false, itemType, flavorText,tracker,rarity));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // NBTに保存
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (StatType type : stats.getAllTypes()) {
            container.set(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE, stats.getFlat(type));
            container.set(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE, stats.getPercent(type));
        }
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build().showInTooltip(false));

        return item;
    }

    public StatMap readStatsFromItem(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || !item.hasItemMeta()) return stats;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        for (StatType type : StatType.values()) {
            Double flat = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_flat"), PersistentDataType.DOUBLE);
            Double percent = container.get(new NamespacedKey("rpgstats", type.name().toLowerCase() + "_percent"), PersistentDataType.DOUBLE);
            if (flat != null) stats.setFlat(type, flat);
            if (percent != null) stats.setPercent(type, percent);
        }
        return stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        // リロード処理
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadAllItems();
            player.sendMessage("§aアイテム設定をリロードしました。");
            return true;
        }

        // ステータスリセット処理
        if (args.length == 1 && args[0].equalsIgnoreCase("resetpoints")) {
            UUID uuid = player.getUniqueId();
            AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
            PlayerAttributeData data = attrManager.get(uuid);
            if (data != null) {
                int totalAllocated = 0;
                for (StatType type : StatType.values()) {
                    totalAllocated += data.getAllocated(type);
                    data.setAllocated(type, 0);
                }
                data.addPoints(totalAllocated);
                player.sendMessage("§6すべてのステータスポイントをリセットしました。");
            } else {
                player.sendMessage("§cステータスデータが読み込まれていません。");
            }
            return true;
        }

        // ステータスポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                AttributeManager attrManager = Deepwither.getInstance().getAttributeManager();
                PlayerAttributeData data = attrManager.get(uuid);
                if (data != null) {
                    data.addPoints(amount);
                    player.sendMessage("§aステータスポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cステータスデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        // スキルポイント付与処理
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            try {
                int amount = Integer.parseInt(args[1]);
                UUID uuid = player.getUniqueId();
                SkilltreeManager.SkillData data = Deepwither.getInstance().getSkilltreeManager().load(uuid);
                if (data != null) {
                    data.setSkillPoint(data.getSkillPoint() + amount);
                    Deepwither.getInstance().getSkilltreeManager().save(uuid, data);
                    player.sendMessage("§aスキルポイントを §e" + amount + " §a付与しました。");
                } else {
                    player.sendMessage("§cスキルツリーデータが読み込まれていません。");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c数値を入力してください。");
            }
            return true;
        }

        // アイテム付与処理
        if (args.length != 1) {
            player.sendMessage("§c使い方: /giveitem <id> | reload | resetpoints | addpoints <数>");
            return true;
        }

        String id = args[0];
        File itemFolder = new File(plugin.getDataFolder(), "items");
        ItemStack item = ItemLoader.loadSingleItem(id, this, itemFolder);

        if (item == null) {
            player.sendMessage("§cそのIDのアイテムは存在しません。");
            return true;
        }

        player.getInventory().addItem(item);
        player.sendMessage("§aアイテム §e" + id + " §aを生成して付与しました。");
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(itemMap.keySet());
            candidates.add("reload");
            candidates.add("resetpoints");
            candidates.add("addpoints");
            candidates.add("addskillpoints");
            return candidates.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        // addpoints の引数補完（ダミー値）
        if (args.length == 2 && args[0].equalsIgnoreCase("addpoints")) {
            return List.of("10", "25", "50");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addskillpoints")) {
            return List.of("5", "10", "20");
        }

        return Collections.emptyList();
    }
}

class ItemLoader {
    private static final Random random = new Random();

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

    public static ItemStack loadSingleItem(String id, ItemFactory factory, File itemFolder) {
        for (File file : Objects.requireNonNull(itemFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains(id)) {
                Map<String, ItemStack> map = loadItems(config, factory);
                return map.get(id); // 1つだけ生成して返す
            }
        }
        return null;
    }

    public static Map<String, ItemStack> loadItems(YamlConfiguration config, ItemFactory factory) {
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

                // ランダムステータスの品質判定用トラッカー初期化
                RandomStatTracker tracker = new RandomStatTracker();

                StatMap stats = new StatMap();
                if (config.isConfigurationSection(key + ".stats")) {
                    for (String statKey : config.getConfigurationSection(key + ".stats").getKeys(false)) {
                        StatType type = StatType.valueOf(statKey.toUpperCase());

                        // flat値処理
                        double flat;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".flat")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".flat.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".flat.spread", 0);
                            flat = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type,base, spread, flat);
                        } else {
                            flat = config.getDouble(key + ".stats." + statKey + ".flat", 0);
                            tracker.add(type,flat, 0, flat);
                        }
                        stats.setFlat(type, flat);

                        // percent値処理
                        double percent;
                        if (config.isConfigurationSection(key + ".stats." + statKey + ".percent")) {
                            double base = config.getDouble(key + ".stats." + statKey + ".percent.base", 0);
                            double spread = config.getDouble(key + ".stats." + statKey + ".percent.spread", 0);
                            percent = base + (spread > 0 ? random.nextDouble() * spread : 0);
                            tracker.add(type,base, spread, percent);
                        } else {
                            percent = config.getDouble(key + ".stats." + statKey + ".percent", 0);
                            tracker.add(type,percent, 0, percent);
                        }
                        stats.setPercent(type, percent);
                    }
                }

                // 品質ランク判定
                QualityRank rank = QualityRank.fromRatio(tracker.getRatio());

                // 名前に品質名をプレフィックス付け
                String originalName = config.getString(key + ".name", key);
                String newName = originalName;
                meta.setDisplayName(newName);
                item.setItemMeta(meta);

                String itemType = config.getString(key + ".type", null);
                String rarity = config.getString(key + ".rarity", null);
                List<String> flavorText = config.getStringList(key + ".flavor");
                // Lore + PDC 書き込みをItemFactory側で処理
                item = factory.applyStatsToItem(item, stats,itemType,flavorText,tracker,rarity);

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
                        System.out.println("Applied armor trim: " + armortrim + " with material: " + armortrimmaterial + " to item " + key);
                    } else {
                        System.err.println("Failed to get trim pattern or material for item: " + key);
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
}
