package com.lunar_prototype.deepwither;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SkilltreeGUI implements CommandExecutor, Listener {

    private final File treeFile;
    private final YamlConfiguration treeConfig;
    private final JavaPlugin plugin;
    private final SkilltreeManager skilltreeManager;

    public SkilltreeGUI(JavaPlugin plugin, File dataFolder,SkilltreeManager skilltreeManager) throws IOException {
        this.plugin = plugin;
        this.skilltreeManager = skilltreeManager;

        this.treeFile = new File(dataFolder, "tree.yaml");
        if (!treeFile.exists()) {
            treeFile.getParentFile().mkdirs();
            treeFile.createNewFile();
        }
        this.treeConfig = YamlConfiguration.loadConfiguration(treeFile);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public Map<String, Object> getNodeById(String treeId, String nodeId) {
        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        System.out.println("[DEBUG] Searching for tree ID: " + treeId + ", node ID: " + nodeId);

        for (Map<?, ?> tree : trees) {
            System.out.println("[DEBUG] Checking tree ID: " + tree.get("id"));

            if (treeId.equals(tree.get("id"))) {
                System.out.println("[DEBUG] Tree matched!");

                Map<String, Object> starter = (Map<String, Object>) tree.get("starter");
                if (starter != null) {
                    System.out.println("[DEBUG] Starter ID: " + starter.get("id"));
                    if (nodeId.equals(starter.get("id"))) {
                        System.out.println("[DEBUG] Matched starter node");
                        return starter;
                    }
                }

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
                if (nodes != null) {
                    for (Map<String, Object> node : nodes) {
                        System.out.println("[DEBUG] Checking node ID: " + node.get("id"));
                        if (nodeId.equals(node.get("id"))) {
                            System.out.println("[DEBUG] Matched regular node");
                            return node;
                        }
                    }
                }

                System.out.println("[DEBUG] Node not found in this tree.");
            }
        }

        System.out.println("[DEBUG] Tree not found.");
        return null;
    }

    @EventHandler
    public void onClick2(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // GUIタイトル確認
        if (event.getView().getTitle().startsWith(ChatColor.DARK_AQUA + "Skilltree: ")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            ItemMeta meta = clicked.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            NamespacedKey keyTree = new NamespacedKey("deepwither", "tree_id");
            NamespacedKey keyNode = new NamespacedKey("deepwither", "node_id");
            NamespacedKey keyPage = new NamespacedKey("deepwither", "page");

            // ページングボタンのクリック処理
            if (container.has(keyPage, PersistentDataType.INTEGER)) {
                if (clicked.getType() == Material.ARROW) {
                    String treeId = container.get(keyTree, PersistentDataType.STRING);
                    int page = container.get(keyPage, PersistentDataType.INTEGER);
                    openTreeGUI(player, treeId, page);
                    return;
                }
            }

            // スキルノードのクリック処理
            String treeId = container.get(keyTree, PersistentDataType.STRING);
            String skillId = container.get(keyNode, PersistentDataType.STRING);
            Integer page = container.get(keyPage, PersistentDataType.INTEGER);
            player.sendMessage("" + treeId + skillId + page);
            if (treeId == null || skillId == null || page == null) return;

            // データ読み込み
            SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());

            // スキル定義を取得（treeId で対応）
            Map<String, Object> skillNode = getNodeById(treeId, skillId);
            if (skillNode == null) {
                player.sendMessage(ChatColor.RED + "スキルが見つかりません。");
                return;
            }

            int maxLevel = (int) skillNode.getOrDefault("max_level", 1);
            List<String> requirements = (List<String>) skillNode.getOrDefault("requirements", List.of());

            // **修正箇所**
            // 前提スキルの最高レベル習得をチェック
            boolean allRequirementsMet = requirements.stream()
                    .allMatch(reqId -> {
                        // 前提スキルの定義を取得
                        Map<String, Object> reqNode = getNodeById(treeId, reqId);
                        if (reqNode == null) return false;

                        int reqMaxLevel = (int) reqNode.getOrDefault("max_level", 1);
                        return data.hasSkill(reqId) && data.getSkillLevel(reqId) == reqMaxLevel;
                    });

            if (!allRequirementsMet) {
                player.sendMessage(ChatColor.RED + "このスキルを取得するには前提スキルを最大レベルまで習得する必要があります。");
                return;
            }
            // **修正箇所ここまで**

            if (!data.canLevelUp(skillId, maxLevel)) {
                player.sendMessage(ChatColor.YELLOW + "このスキルはすでに最大まで習得済みです。");
                return;
            }

            if (data.getSkillPoint() <= 0) {
                player.sendMessage(ChatColor.RED + "スキルポイントが足りません。");
                return;
            }

            // 習得処理
            data.unlock(skillId);
            data.setSkillPoint(data.getSkillPoint() - 1);
            skilltreeManager.save(player.getUniqueId(), data);

            player.sendMessage(ChatColor.GREEN + "スキル「" + skillNode.get("name") + "」を習得しました！");
            openTreeGUI(player,treeId,page);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤー専用です。");
            return true;
        }

        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        if (trees == null || trees.isEmpty()) {
            player.sendMessage(ChatColor.RED + "スキルツリー設定が見つかりません。");
            return true;
        }

        Inventory inv = Bukkit.createInventory(player, 9 * ((trees.size() + 8) / 9), ChatColor.DARK_GREEN + "スキルツリー選択");

        int slot = 0;
        for (Map<?, ?> tree : trees) {
            Map<?, ?> starter = (Map<?, ?>) tree.get("starter");
            if (starter == null) continue;

            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + (String) starter.get("name"));
            meta.setLore(List.of(
                    ChatColor.GRAY + "ID: " + tree.get("id"),
                    ChatColor.GREEN + "クリックして開く"
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();

        if (inv.getType() != InventoryType.CHEST) return;
        if (event.getView().getTitle().startsWith(ChatColor.DARK_GREEN + "スキルツリー選択")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLore()) return;

            List<String> lore = clicked.getItemMeta().getLore();
            if (lore == null || lore.isEmpty()) return;

            String line = ChatColor.stripColor(lore.get(0)).trim(); // "ID: mage"など
            if (!line.startsWith("ID: ")) return;

            String id = line.substring(4).trim(); // "mage"
            openTreeGUI(player, id, 0); // **ページ0で開くように変更**
        }
    }

    // `SkilltreeGUI` クラス内
    private void openTreeGUI(Player player, String treeId, int page) {
        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());

        Map<?, ?> currentTree = null;
        for (Map<?, ?> tree : trees) {
            Object treeIdObj = tree.get("id");
            if (treeIdObj instanceof String && ((String) treeIdObj).equals(treeId)) {
                currentTree = tree;
                break;
            }
        }

        if (currentTree == null) {
            player.sendMessage(ChatColor.RED + "スキルツリーが見つかりません。");
            return;
        }

        Map<?, ?> starter = (Map<?, ?>) currentTree.get("starter");
        List<Map<?, ?>> nodes = (List<Map<?, ?>>) currentTree.get("nodes");
        Map<String, Map<?, ?>> nodeMap = nodes.stream().collect(Collectors.toMap(n -> (String) n.get("id"), n -> n));

        int guiSize = 54; // 9x6
        int slotsPerPage = 45; // 9x5
        int offset = page * slotsPerPage;

        Inventory inv = Bukkit.createInventory(player, guiSize, ChatColor.DARK_AQUA + "Skilltree: " + currentTree.get("name"));

        // ツリー全体のノード配置を計算するマップ
        Map<String, Integer> fullTreeSlotMap = new HashMap<>();
        Set<String> placed = new HashSet<>();

        // スターターノードと子ノードをすべて計算して fullTreeSlotMap に格納
        if (starter != null) {
            String starterId = (String) starter.get("id");
            int starterSlot = slot(2, 0); // 中央行の左端スロットを計算
            fullTreeSlotMap.put(starterId, starterSlot); // スターターノードを明示的に配置
            placed.add(starterId);
            placeNodesRecursive(starterId, 2, 1,true, nodeMap, fullTreeSlotMap, placed);
        }

        fullTreeSlotMap.forEach((id, slot) -> {
            System.out.println("Node " + id + " -> slot " + slot);
        });

        NamespacedKey keyTree = new NamespacedKey("deepwither", "tree_id");
        NamespacedKey keyNode = new NamespacedKey("deepwither", "node_id");
        NamespacedKey keyPage = new NamespacedKey("deepwither", "page");

        // 計算された全ノードをGUIに配置
        for (String nodeId : fullTreeSlotMap.keySet()) {
            int originalSlot = fullTreeSlotMap.get(nodeId);
            int displaySlot = originalSlot - offset;

            if (displaySlot < 0 || displaySlot >= slotsPerPage) {
                continue;
            }

            Map<?, ?> node;
            if (nodeId.equals(starter.get("id"))) {
                node = starter;
            } else {
                node = (Map<?, ?>) nodeMap.get(nodeId);
            }

            if (node == null) continue;

            String type = (String) node.get("type");
            String name = (String) node.get("name");
            String desc = "No description available.";
            if (node.get("desc") != null){
                desc = (String) node.get("desc");
            }
            List<String> requirements = getRequirementList(node);

            int maxLevel = 1;
            if (node.get("max_level") != null){
                maxLevel = (Integer) node.get("max_level");
            }
            int currentLevel = data.getSkillLevel(nodeId);

            boolean unlocked = requirements.stream().allMatch(reqId -> {
                Map<String, Object> reqNode = getNodeById(treeId, reqId);
                if (reqNode == null) return false;
                int reqMaxLevel = (int) reqNode.getOrDefault("max_level", 1);
                return data.hasSkill(reqId) && data.getSkillLevel(reqId) == reqMaxLevel;
            });
            boolean learned = data.hasSkill(nodeId);

            Material mat = switch (type) {
                case "buff" -> Material.BLUE_STAINED_GLASS_PANE;
                case "skill" -> Material.DIAMOND_SWORD;
                case "starter" -> Material.GOLD_INGOT;
                default -> Material.GRAY_STAINED_GLASS_PANE;
            };

            if (!unlocked) {
                mat = Material.GRAY_STAINED_GLASS_PANE;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String displayName = (learned ? ChatColor.GREEN : unlocked ? ChatColor.AQUA : ChatColor.DARK_GRAY) + name;
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + nodeId);

            if (maxLevel > 1) {
                lore.add(ChatColor.GREEN + "レベル: " + currentLevel + " / " + maxLevel);
            } else {
                lore.add(ChatColor.GREEN + "レベル: " + (learned ? 1 : 0) + " / " + maxLevel);
            }

            lore.add("");
            lore.add(ChatColor.WHITE + desc);
            if (!requirements.isEmpty()) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "必要スキル:");
                for (String req : requirements) {
                    lore.add(ChatColor.GRAY + "- " + req);
                }
            }

            meta.setLore(lore);

            if (learned) {
                meta.addEnchant(Enchantment.DENSITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.getPersistentDataContainer().set(keyTree, PersistentDataType.STRING, treeId);
            meta.getPersistentDataContainer().set(keyNode, PersistentDataType.STRING, nodeId);
            meta.getPersistentDataContainer().set(keyPage, PersistentDataType.INTEGER, page);

            item.setItemMeta(meta);
            inv.setItem(displaySlot, item);
        }

        // ページングボタンの配置
        int maxPages = (int) Math.ceil((double) fullTreeSlotMap.size() / slotsPerPage);

        // 前のページボタン
        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ChatColor.RED + "前のページ");
            prevMeta.getPersistentDataContainer().set(keyTree, PersistentDataType.STRING, treeId);
            prevMeta.getPersistentDataContainer().set(keyPage, PersistentDataType.INTEGER, page - 1);
            prevPage.setItemMeta(prevMeta);
            inv.setItem(45, prevPage);
        }

        // 次のページボタン
        if (page < maxPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ChatColor.GREEN + "次のページ");
            nextMeta.getPersistentDataContainer().set(keyTree, PersistentDataType.STRING, treeId);
            nextMeta.getPersistentDataContainer().set(keyPage, PersistentDataType.INTEGER, page + 1);
            nextPage.setItemMeta(nextMeta);
            inv.setItem(53, nextPage);
        }

        // スキルポイント表示
        ItemStack spItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta spMeta = spItem.getItemMeta();
        spMeta.setDisplayName(ChatColor.AQUA + "スキルポイント");
        spMeta.setLore(List.of(ChatColor.WHITE + "現在のポイント: " + data.getSkillPoint()));
        spItem.setItemMeta(spMeta);
        inv.setItem(49, spItem);

        player.openInventory(inv);
    }

    @SuppressWarnings("unchecked")
    private List<String> getRequirementList(Map<?, ?> node) {
        Object obj = node.get("requirements");
        if (obj instanceof List<?>) {
            return ((List<?>) obj).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    private int slot(int row, int col) {
        int PAGE_COLUMNS = 9;
        int slotsPerPage = 45;
        int page = col / PAGE_COLUMNS;              // どのページか
        int colInPage = col % PAGE_COLUMNS;         // ページ内での列
        return page * slotsPerPage + row * 9 + colInPage;
    }


    private void placeNodesRecursive(String parentId, int baseRow, int col, boolean isStarter,
                                     Map<String, Map<?, ?>> nodeMap,
                                     Map<String, Integer> slotMap,
                                     Set<String> placed) {

        List<String> children = new ArrayList<>();
        for (Map.Entry<String, Map<?, ?>> entry : nodeMap.entrySet()) {
            Map<?, ?> node = entry.getValue();
            List<?> reqs = (List<?>) node.get("requirements");
            if (reqs == null) continue;

            for (Object req : reqs) {
                if (req instanceof String id && id.equals(parentId)) {
                    children.add(entry.getKey());
                }
            }
        }

        if (children.isEmpty()) return;

        int startRow = baseRow - (children.size() / 2);

        for (int i = 0; i < children.size(); i++) {
            String childId = children.get(i);
            int row = startRow + i;

            // 仮想スロット（無限座標）
            int virtualSlot = slot(row, col);

            // ページ補正
            int page = virtualSlot / 45;        // 0ページ目, 1ページ目, ...
            int indexInPage = virtualSlot % 45; // ページ内スロット番号
            int guiSlot = page * 45 + indexInPage;

            // 保存
            slotMap.put(childId, guiSlot);
            placed.add(childId);

            // 再帰
            placeNodesRecursive(childId, row, col + 1, false, nodeMap, slotMap, placed);
        }
    }
}