package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.GetTreeNode;
import com.lunar_prototype.deepwither.api.event.OpenSkilltree;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private YamlConfiguration treeConfig;
    private final JavaPlugin plugin;
    private final SkilltreeManager skilltreeManager;
    private final SkillLoader skillLoader;

    private static final int GUI_ROWS = 6;
    private static final int VIEWPORT_ROWS = 5;
    private static final int VIEWPORT_COLS = 9;

    public SkilltreeGUI(JavaPlugin plugin, File dataFolder, SkilltreeManager skilltreeManager, SkillLoader skillLoader) throws IOException {
        this.plugin = plugin;
        this.skilltreeManager = skilltreeManager;
        this.skillLoader = skillLoader;

        this.treeFile = new File(dataFolder, "tree.yaml");
        if (!treeFile.exists()) {
            treeFile.getParentFile().mkdirs();
            treeFile.createNewFile();
        }
        this.treeConfig = YamlConfiguration.loadConfiguration(treeFile);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        if (!treeFile.exists()) {
            try {
                treeFile.getParentFile().mkdirs();
                treeFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("tree.yaml の作成に失敗しました: " + e.getMessage());
                return;
            }
        }
        this.treeConfig = YamlConfiguration.loadConfiguration(treeFile);
        if (this.skillLoader != null) {
            this.skillLoader.reload();
        }
        plugin.getLogger().info("Skilltree configuration has been reloaded.");
    }

    private record NodePosition(int x, int y) {}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤー専用です。", NamedTextColor.RED));
            return true;
        }

        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        if (trees == null || trees.isEmpty()) {
            player.sendMessage(Component.text("スキルツリー設定が見つかりません。", NamedTextColor.RED));
            return true;
        }

        Bukkit.getPluginManager().callEvent(new OpenSkilltree(player));
        Inventory inv = Bukkit.createInventory(player, 9 * ((trees.size() + 8) / 9), Component.text("スキルツリー選択", NamedTextColor.DARK_GREEN));
        int slot = 0;
        for (Map<?, ?> tree : trees) {
            Map<?, ?> starter = (Map<?, ?>) tree.get("starter");
            if (starter == null) continue;

            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text((String) starter.get("name"), NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("ID: " + tree.get("id"), NamedTextColor.GRAY),
                    Component.text("クリックして開く", NamedTextColor.GREEN)
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (event.getView().title().equals(Component.text("スキルツリー選択", NamedTextColor.DARK_GREEN))) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            List<Component> lore = clicked.getItemMeta().lore();
            if (lore == null || lore.isEmpty()) return;

            String line = PlainTextComponentSerializer.plainText().serialize(lore.get(0)).trim();
            if (!line.startsWith("ID: ")) return;
            String id = line.substring(4).trim();

            NodePosition lastPos = getLastPosition(player, id);
            openTreeGUI(player, id, lastPos.x(), lastPos.y());
        }
    }

    @EventHandler
    public void onSkillTreeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (PlainTextComponentSerializer.plainText().serialize(event.getView().title()).startsWith("Skilltree: ")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            ItemMeta meta = clicked.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            NamespacedKey keyTree = new NamespacedKey("deepwither", "tree_id");
            NamespacedKey keyNode = new NamespacedKey("deepwither", "node_id");
            NamespacedKey keyX = new NamespacedKey("deepwither", "cam_x");
            NamespacedKey keyY = new NamespacedKey("deepwither", "cam_y");
            NamespacedKey keyScrollDir = new NamespacedKey("deepwither", "scroll_dir");

            if (container.has(keyScrollDir, PersistentDataType.STRING)) {
                String treeId = container.get(keyTree, PersistentDataType.STRING);
                int currentX = container.getOrDefault(keyX, PersistentDataType.INTEGER, 0);
                int currentY = container.getOrDefault(keyY, PersistentDataType.INTEGER, 0);
                String direction = container.get(keyScrollDir, PersistentDataType.STRING);

                int moveAmount = 1;

                switch (direction) {
                    case "UP" -> currentY -= moveAmount;
                    case "DOWN" -> currentY += moveAmount;
                    case "LEFT" -> currentX -= moveAmount;
                    case "RIGHT" -> currentX += moveAmount;
                    case "RESET" -> { currentX = 0; currentY = 0; }
                }
                saveLastPosition(player, treeId, currentX, currentY);
                openTreeGUI(player, treeId, currentX, currentY);
                return;
            }

            if (!container.has(keyNode, PersistentDataType.STRING)) return;

            String treeId = container.get(keyTree, PersistentDataType.STRING);
            String skillId = container.get(keyNode, PersistentDataType.STRING);
            int currentX = container.getOrDefault(keyX, PersistentDataType.INTEGER, 0);
            int currentY = container.getOrDefault(keyY, PersistentDataType.INTEGER, 0);

            handleSkillUnlock(player, treeId, skillId, currentX, currentY);
        }
    }

    private void openTreeGUI(Player player, String treeId, int camX, int camY) {
        Map<?, ?> currentTree = getTreeConfigMap(treeId);
        if (currentTree == null) {
            player.sendMessage(Component.text("エラー: ツリー定義が見つかりません。", NamedTextColor.RED));
            return;
        }

        SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());

        Map<?, ?> starter = (Map<?, ?>) currentTree.get("starter");
        List<Map<?, ?>> nodes = (List<Map<?, ?>>) currentTree.get("nodes");
        Map<String, Map<?, ?>> nodeMap = nodes.stream().collect(Collectors.toMap(n -> (String) n.get("id"), n -> n));
        if (starter != null) nodeMap.put((String) starter.get("id"), (Map<String, Object>) starter);

        Map<String, NodePosition> layout = calculateTreeLayout(starter, nodeMap);

        Inventory inv = Bukkit.createInventory(player, GUI_ROWS * 9, Component.text("Skilltree: " + currentTree.get("name"), NamedTextColor.DARK_AQUA));

        NamespacedKey keyTree = new NamespacedKey("deepwither", "tree_id");
        NamespacedKey keyNode = new NamespacedKey("deepwither", "node_id");
        NamespacedKey keyCamX = new NamespacedKey("deepwither", "cam_x");
        NamespacedKey keyCamY = new NamespacedKey("deepwither", "cam_y");

        for (Map.Entry<String, NodePosition> entry : layout.entrySet()) {
            String nodeId = entry.getKey();
            NodePosition pos = entry.getValue();

            int screenX = pos.x - camX;
            int screenY = pos.y - camY;

            if (screenX >= 0 && screenX < VIEWPORT_COLS && screenY >= 0 && screenY < VIEWPORT_ROWS) {
                int slot = screenY * 9 + screenX;

                ItemStack item = createSkillIcon(nodeMap.get(nodeId), data, treeId,player);
                ItemMeta meta = item.getItemMeta();

                meta.getPersistentDataContainer().set(keyTree, PersistentDataType.STRING, treeId);
                meta.getPersistentDataContainer().set(keyNode, PersistentDataType.STRING, nodeId);
                meta.getPersistentDataContainer().set(keyCamX, PersistentDataType.INTEGER, camX);
                meta.getPersistentDataContainer().set(keyCamY, PersistentDataType.INTEGER, camY);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
            }
        }

        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.displayName(Component.text(" "));
        glass.setItemMeta(gMeta);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        createControlBtn(inv, 45, Material.RED_STAINED_GLASS_PANE, Component.text("← 左へ", NamedTextColor.RED), "LEFT", treeId, camX, camY);
        createControlBtn(inv, 46, Material.LIME_STAINED_GLASS_PANE, Component.text("↑ 上へ", NamedTextColor.GREEN), "UP", treeId, camX, camY);
        createControlBtn(inv, 49, Material.COMPASS, Component.text("位置リセット (" + camX + ", " + camY + ")", NamedTextColor.YELLOW), "RESET", treeId, camX, camY);
        createControlBtn(inv, 52, Material.LIME_STAINED_GLASS_PANE, Component.text("↓ 下へ", NamedTextColor.GREEN), "DOWN", treeId, camX, camY);
        createControlBtn(inv, 53, Material.RED_STAINED_GLASS_PANE, Component.text("右へ →", NamedTextColor.RED), "RIGHT", treeId, camX, camY);

        ItemStack spItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta spMeta = spItem.getItemMeta();
        spMeta.displayName(Component.text("SP: " + data.getSkillPoint(), NamedTextColor.AQUA));
        spItem.setItemMeta(spMeta);
        inv.setItem(50, spItem);

        player.openInventory(inv);
    }

    private Map<String, NodePosition> calculateTreeLayout(Map<?, ?> starter, Map<String, Map<?, ?>> nodeMap) {
        Map<String, NodePosition> layout = new HashMap<>();
        if (starter == null) return layout;
        String starterId = (String) starter.get("id");
        Map<String, Integer> depthMap = new HashMap<>();
        calculateMaxDepth(starterId, 0, nodeMap, depthMap);
        placeNodeRecursive(starterId, 4, 2, nodeMap, layout, depthMap);
        return layout;
    }

    private void calculateMaxDepth(String currentId, int currentDepth, Map<String, Map<?, ?>> nodeMap, Map<String, Integer> depthMap) {
        if (depthMap.containsKey(currentId)) {
            if (depthMap.get(currentId) >= currentDepth) return;
        }
        depthMap.put(currentId, currentDepth);

        for (Map.Entry<String, Map<?, ?>> entry : nodeMap.entrySet()) {
            Map<?, ?> node = entry.getValue();
            List<?> reqs = (List<?>) node.get("requirements");
            if (reqs != null && reqs.contains(currentId)) {
                calculateMaxDepth(entry.getKey(), currentDepth + 1, nodeMap, depthMap);
            }
        }
    }

    private int placeNodeRecursive(String nodeId, int startX, int currentY,
                                   Map<String, Map<?, ?>> nodeMap,
                                   Map<String, NodePosition> layout,
                                   Map<String, Integer> depthMap) {
        if (layout.containsKey(nodeId)) {
            return layout.get(nodeId).y();
        }
        int myDepth = depthMap.getOrDefault(nodeId, 0);
        int myX = startX + myDepth;
        layout.put(nodeId, new NodePosition(myX, currentY));
        int maxYUsed = currentY;
        List<String> children = new ArrayList<>();
        for (Map.Entry<String, Map<?, ?>> entry : nodeMap.entrySet()) {
            Map<?, ?> node = entry.getValue();
            List<?> reqs = (List<?>) node.get("requirements");
            if (reqs != null && reqs.contains(nodeId)) {
                children.add(entry.getKey());
            }
        }
        for (int i = 0; i < children.size(); i++) {
            String childId = children.get(i);
            int childStartY = (i == 0) ? currentY : maxYUsed + 1;
            int childMaxY = placeNodeRecursive(childId, startX, childStartY, nodeMap, layout, depthMap);
            if (childMaxY > maxYUsed) maxYUsed = childMaxY;
        }
        return maxYUsed;
    }

    private void handleSkillUnlock(Player player, String treeId, String skillId, int camX, int camY) {
        SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());
        Map<String, Object> skillNode = getNodeById(treeId, skillId);

        if (skillNode == null) return;

        int maxLevel = (int) skillNode.getOrDefault("max_level", 1);
        List<String> requirements = (List<String>) skillNode.getOrDefault("requirements", List.of());

        boolean allRequirementsMet = requirements.stream().allMatch(reqId -> {
            Map<String, Object> reqNode = getNodeById(treeId, reqId);
            if (reqNode == null) return false;
            int reqMax = (int) reqNode.getOrDefault("max_level", 1);
            return data.hasSkill(reqId) && data.getSkillLevel(reqId) == reqMax;
        });

        if (!allRequirementsMet) {
            player.sendMessage(Component.text("前提スキルが足りません。", NamedTextColor.RED));
            return;
        }

        for (String learnedSkillId : data.getSkills().keySet()) {
            Map<String, Object> learnedNode = getNodeById(treeId, learnedSkillId);
            if (learnedNode != null) {
                List<String> conflicts = (List<String>) learnedNode.getOrDefault("conflicts", List.of());
                if (conflicts.contains(skillId)) {
                    player.sendMessage(Component.text("既に取得済みのスキル「" + learnedNode.get("name") + "」と競合するため、このスキルは取得できません。", NamedTextColor.RED));
                    return;
                }
            }
        }

        if (!data.canLevelUp(skillId, maxLevel)) {
            player.sendMessage(Component.text("これ以上レベルアップできません。", NamedTextColor.YELLOW));
            return;
        }

        if (data.getSkillPoint() <= 0) {
            player.sendMessage(Component.text("スキルポイントが不足しています。", NamedTextColor.RED));
            return;
        }

        Bukkit.getPluginManager().callEvent(new GetTreeNode(player,skillId));

        data.unlock(skillId);
        data.setSkillPoint(data.getSkillPoint() - 1);
        skilltreeManager.save(player.getUniqueId(), data);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        player.sendMessage(Component.text("スキル習得: " + skillNode.get("name"), NamedTextColor.GREEN));

        openTreeGUI(player, treeId, camX, camY);
    }

    private void createControlBtn(Inventory inv, int slot, Material mat, Component name, String dir, String treeId, int x, int y) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("deepwither", "scroll_dir"), PersistentDataType.STRING, dir);
        pdc.set(new NamespacedKey("deepwither", "tree_id"), PersistentDataType.STRING, treeId);
        pdc.set(new NamespacedKey("deepwither", "cam_x"), PersistentDataType.INTEGER, x);
        pdc.set(new NamespacedKey("deepwither", "cam_y"), PersistentDataType.INTEGER, y);

        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private ItemStack createSkillIcon(Map<?, ?> node, SkilltreeManager.SkillData data, String treeId, Player player) {
        String id = (String) node.get("id");
        String type = (String) node.get("type");
        String name = (String) node.get("name");

        String desc = (node.get("desc") != null) ? (String) node.get("desc") : "No Description";
        Object effectObj = node.get("effect");
        String skillEffectId = (effectObj instanceof String) ? (String) effectObj : id;
        int maxLevel = (node.get("max_level") != null) ? (Integer) node.get("max_level") : 1;

        boolean learned = data.hasSkill(id);
        int level = data.getSkillLevel(id);

        List<String> reqs = (node.get("requirements") != null) ? (List<String>) node.get("requirements") : Collections.emptyList();

        boolean unlocked = reqs.isEmpty() || reqs.stream().allMatch(r -> {
            Map<String, Object> reqNode = getNodeById(treeId, r);
            int reqMax = (reqNode != null && reqNode.get("max_level") != null) ? (Integer) reqNode.get("max_level") : 1;
            return data.hasSkill(r) && data.getSkillLevel(r) >= reqMax;
        });

        List<String> conflicts = (node.get("conflicts") != null) ? (List<String>) node.get("conflicts") : Collections.emptyList();

        boolean isConflicted = false;
        for (String learnedId : data.getSkills().keySet()) {
            Map<String, Object> learnedNode = getNodeById(treeId, learnedId);
            if (learnedNode != null) {
                List<String> learnedConflicts = (learnedNode.get("conflicts") != null) ? (List<String>) learnedNode.get("conflicts") : Collections.emptyList();
                if (learnedConflicts.contains(id)) {
                    isConflicted = true;
                    break;
                }
            }
        }

        if (isConflicted) unlocked = false;

        Material mat = Material.GRAY_STAINED_GLASS_PANE;
        List<Component> lore = new ArrayList<>();

        if ("skill".equals(type)) {
            SkillDefinition skill = skillLoader.get(skillEffectId);
            if (skill != null) {
                mat = skill.material;
                name = skill.name;
                if (skill.lore != null) {
                    for (String loreLine : skill.lore) {
                        double effectiveCooldown = StatManager.getEffectiveCooldown(player, skill.cooldown);
                        double manaCost = skill.manaCost;
                        String translated = loreLine.replace("{cooldown}", String.format("%.1f", effectiveCooldown))
                                                   .replace("{mana}", String.format("%.1f", manaCost));
                        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(translated).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    }
                }
            } else {
                mat = Material.RED_STAINED_GLASS_PANE;
                lore.add(Component.text("Error: Skill definition not found for '" + skillEffectId + "'", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        } else if ("special_passive".equals(type)) {
            mat = Material.ENCHANTED_BOOK;
            lore.add(Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            Map<?, ?> effect = (Map<?, ?>) node.get("effect");
            if (effect != null) {
                lore.add(Component.empty());
                lore.add(Component.text("特殊効果: " + effect.get("id"), NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            }
        } else {
            if (learned) mat = Material.LIME_STAINED_GLASS_PANE;
            else if (unlocked) mat = Material.YELLOW_STAINED_GLASS_PANE;
            else mat = Material.RED_STAINED_GLASS_PANE;
            if (isConflicted) mat = Material.RED_STAINED_GLASS_PANE;
            if ("starter".equals(type)) mat = Material.BEACON;
            lore.add(Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        if (!lore.isEmpty()) lore.add(Component.empty());
        lore.add(Component.text("ID: " + id, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        NamedTextColor nameColor;
        Component statusText;

        if (learned) {
            nameColor = NamedTextColor.GREEN;
            statusText = Component.text("■ 習得済み", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
        } else if (isConflicted) {
            nameColor = NamedTextColor.RED;
            statusText = Component.text("■ 習得不可 (競合)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        } else if (unlocked) {
            nameColor = NamedTextColor.YELLOW;
            statusText = Component.text("■ 習得可能", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
        } else {
            nameColor = NamedTextColor.RED;
            statusText = Component.text("■ 未解除", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        }

        lore.add(statusText);
        lore.add(Component.text("Lv: " + level + "/" + maxLevel, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        if (!reqs.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("[必要スキル]", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            for (String r : reqs) {
                boolean hasReq = data.hasSkill(r);
                lore.add(Component.text("- " + r, hasReq ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }

        if (!conflicts.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("排他選択 (競合):", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
            for (String conflictId : conflicts) {
                Map<String, Object> conflictNode = getNodeById(treeId, conflictId);
                String conflictName = (conflictNode != null) ? (String) conflictNode.get("name") : conflictId;
                lore.add(Component.text("- " + conflictName, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, nameColor).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        if (learned) {
            meta.addEnchant(Enchantment.DENSITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Map<?, ?> getTreeConfigMap(String treeId) {
        List<Map<?, ?>> trees = treeConfig.getMapList("trees");
        for (Map<?, ?> t : trees) {
            if (treeId.equals(t.get("id"))) return t;
        }
        return null;
    }

    public Map<String, Object> getNodeById(String treeId, String nodeId) {
        Map<?, ?> tree = getTreeConfigMap(treeId);
        if (tree == null) return null;
        Map<String, Object> starter = (Map<String, Object>) tree.get("starter");
        if (starter != null && nodeId.equals(starter.get("id"))) return starter;
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                if (nodeId.equals(node.get("id"))) return node;
            }
        }
        return null;
    }

    private void saveLastPosition(Player player, String treeId, int x, int y) {
        NamespacedKey key = new NamespacedKey("deepwither", "last_pos_" + treeId);
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, x + "," + y);
    }

    private NodePosition getLastPosition(Player player, String treeId) {
        NamespacedKey key = new NamespacedKey("deepwither", "last_pos_" + treeId);
        String data = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (data != null) {
            try {
                String[] parts = data.split(",");
                return new NodePosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (Exception ignored) {}
        }
        return new NodePosition(0, 0);
    }
}
