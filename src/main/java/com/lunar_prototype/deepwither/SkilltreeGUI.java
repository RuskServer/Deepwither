package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.api.event.OpenAttributes;
import com.lunar_prototype.deepwither.api.event.OpenSkilltree;
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
    private final SkillLoader skillLoader; // ← これを追加

    // 定数
    private static final int GUI_ROWS = 6;
    private static final int VIEWPORT_ROWS = 5; // スキル表示エリア（上5行）
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

    // 簡易的な座標保持クラス
    private record NodePosition(int x, int y) {}

    // --- コマンド処理 ---
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

        // ツリー選択画面を開く
        Bukkit.getPluginManager().callEvent(new OpenSkilltree(player));
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

    // --- イベントハンドラ ---
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();

        // ツリー選択画面の処理
        if (event.getView().getTitle().startsWith(ChatColor.DARK_GREEN + "スキルツリー選択")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLore()) return;

            String line = ChatColor.stripColor(clicked.getItemMeta().getLore().get(0)).trim();
            if (!line.startsWith("ID: ")) return;
            String id = line.substring(4).trim();

            // 初期位置 (0, 0) で開く
            openTreeGUI(player, id, 0, 0);
        }
    }

    @EventHandler
    public void onSkillTreeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // タイトル確認
        if (event.getView().getTitle().startsWith(ChatColor.DARK_AQUA + "Skilltree: ")) {
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

            // --- スクロールボタン処理 ---
            if (container.has(keyScrollDir, PersistentDataType.STRING)) {
                String treeId = container.get(keyTree, PersistentDataType.STRING);
                int currentX = container.getOrDefault(keyX, PersistentDataType.INTEGER, 0);
                int currentY = container.getOrDefault(keyY, PersistentDataType.INTEGER, 0);
                String direction = container.get(keyScrollDir, PersistentDataType.STRING);

                int moveAmount = 1; // 1クリックあたりの移動量

                switch (direction) {
                    case "UP" -> currentY -= moveAmount;
                    case "DOWN" -> currentY += moveAmount;
                    case "LEFT" -> currentX -= moveAmount;
                    case "RIGHT" -> currentX += moveAmount;
                    case "RESET" -> { currentX = 0; currentY = 0; }
                }

                openTreeGUI(player, treeId, currentX, currentY);
                return;
            }

            // --- スキルノード処理 ---
            if (!container.has(keyNode, PersistentDataType.STRING)) return;

            String treeId = container.get(keyTree, PersistentDataType.STRING);
            String skillId = container.get(keyNode, PersistentDataType.STRING);

            // 現在のカメラ位置を維持するために取得
            // ※ アイテムに埋め込んでいない場合は、別途管理するか、(0,0)に戻る等の仕様になりますが、
            //    ここではクリック時にGUIを再描画するので、現在のGUIの状態を知る必要があります。
            //    今回はシンプルに「習得後は同じ位置で再描画」するために、GUI生成時にメタデータを持たせます。
            int currentX = container.getOrDefault(keyX, PersistentDataType.INTEGER, 0);
            int currentY = container.getOrDefault(keyY, PersistentDataType.INTEGER, 0);

            handleSkillUnlock(player, treeId, skillId, currentX, currentY);
        }
    }

    // --- メインロジック ---

    // GUIを開くメソッド (座標指定)
    private void openTreeGUI(Player player, String treeId, int camX, int camY) {
        Map<?, ?> currentTree = getTreeConfigMap(treeId);
        if (currentTree == null) {
            player.sendMessage(ChatColor.RED + "エラー: ツリー定義が見つかりません。");
            return;
        }

        SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());

        // ノードデータの準備
        Map<?, ?> starter = (Map<?, ?>) currentTree.get("starter");
        List<Map<?, ?>> nodes = (List<Map<?, ?>>) currentTree.get("nodes");
        Map<String, Map<?, ?>> nodeMap = nodes.stream().collect(Collectors.toMap(n -> (String) n.get("id"), n -> n));
        if (starter != null) nodeMap.put((String) starter.get("id"), (Map<String, Object>) starter);

        // レイアウト計算 (全ノードの絶対座標を決定)
        Map<String, NodePosition> layout = calculateTreeLayout(starter, nodeMap);

        // インベントリ作成
        Inventory inv = Bukkit.createInventory(player, GUI_ROWS * 9, ChatColor.DARK_AQUA + "Skilltree: " + currentTree.get("name"));

        NamespacedKey keyTree = new NamespacedKey("deepwither", "tree_id");
        NamespacedKey keyNode = new NamespacedKey("deepwither", "node_id");
        NamespacedKey keyCamX = new NamespacedKey("deepwither", "cam_x");
        NamespacedKey keyCamY = new NamespacedKey("deepwither", "cam_y");

        // --- ノードの描画 ---
        for (Map.Entry<String, NodePosition> entry : layout.entrySet()) {
            String nodeId = entry.getKey();
            NodePosition pos = entry.getValue();

            // 画面上の位置 (ローカル座標) を計算
            int screenX = pos.x - camX;
            int screenY = pos.y - camY;

            // ビューポート (0~8, 0~4) の範囲内にあるかチェック
            if (screenX >= 0 && screenX < VIEWPORT_COLS && screenY >= 0 && screenY < VIEWPORT_ROWS) {
                int slot = screenY * 9 + screenX;

                ItemStack item = createSkillIcon(nodeMap.get(nodeId), data, treeId,player);
                ItemMeta meta = item.getItemMeta();

                // クリック時に現在のカメラ位置を維持できるようにデータを埋め込む
                meta.getPersistentDataContainer().set(keyTree, PersistentDataType.STRING, treeId);
                meta.getPersistentDataContainer().set(keyNode, PersistentDataType.STRING, nodeId);
                meta.getPersistentDataContainer().set(keyCamX, PersistentDataType.INTEGER, camX);
                meta.getPersistentDataContainer().set(keyCamY, PersistentDataType.INTEGER, camY);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
            }
        }

        // --- コントロールパネル (最下段) ---
        // 背景ガラス
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        // 矢印ボタン作成ヘルパー
        createControlBtn(inv, 45, Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "← 左へ", "LEFT", treeId, camX, camY);
        createControlBtn(inv, 46, Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "↑ 上へ", "UP", treeId, camX, camY);
        createControlBtn(inv, 49, Material.COMPASS, ChatColor.YELLOW + "位置リセット (" + camX + ", " + camY + ")", "RESET", treeId, camX, camY);
        createControlBtn(inv, 52, Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "↓ 下へ", "DOWN", treeId, camX, camY);
        createControlBtn(inv, 53, Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "右へ →", "RIGHT", treeId, camX, camY);

        // スキルポイント表示
        ItemStack spItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta spMeta = spItem.getItemMeta();
        spMeta.setDisplayName(ChatColor.AQUA + "SP: " + data.getSkillPoint());
        spItem.setItemMeta(spMeta);
        inv.setItem(50, spItem); // 右矢印の隣あたりに配置

        player.openInventory(inv);
    }

    // ---------------------------------------------------------
    //  最新版: 矩形ブロック配置ロジック (食い込み防止版)
    // ---------------------------------------------------------

    private Map<String, NodePosition> calculateTreeLayout(Map<?, ?> starter, Map<String, Map<?, ?>> nodeMap) {
        Map<String, NodePosition> layout = new HashMap<>();
        if (starter == null) return layout;

        String starterId = (String) starter.get("id");

        // --- 手順1: X座標 (深さ) を決定 ---
        Map<String, Integer> depthMap = new HashMap<>();
        calculateMaxDepth(starterId, 0, nodeMap, depthMap);

        // --- 手順2: 再帰的に配置し、Y座標の占有範囲を計算 ---
        // スタート位置 (X=4, Y=2)
        placeNodeRecursive(starterId, 4, 2, nodeMap, layout, depthMap);

        return layout;
    }

    // X座標計算 (変更なし)
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

    /**
     * ノードを配置し、そのノード以下のツリーが使用した「最大のY座標」を返す
     * @return この分岐グループが到達した最も下のY座標
     */
    private int placeNodeRecursive(String nodeId, int startX, int currentY,
                                   Map<String, Map<?, ?>> nodeMap,
                                   Map<String, NodePosition> layout,
                                   Map<String, Integer> depthMap) {

        // すでに配置済みなら、そのノードのY座標を返す（更新はしない）
        if (layout.containsKey(nodeId)) {
            return layout.get(nodeId).y();
        }

        // 1. 自分のX座標を取得 (計算済みの深さを使用)
        int myDepth = depthMap.getOrDefault(nodeId, 0);
        int myX = startX + myDepth;

        // 2. 配置を確定
        layout.put(nodeId, new NodePosition(myX, currentY));

        // この枝が使った最大のY座標（最初は自分自身）
        int maxYUsed = currentY;

        // 3. 子ノードを取得
        List<String> children = new ArrayList<>();
        for (Map.Entry<String, Map<?, ?>> entry : nodeMap.entrySet()) {
            Map<?, ?> node = entry.getValue();
            List<?> reqs = (List<?>) node.get("requirements");
            if (reqs != null && reqs.contains(nodeId)) {
                children.add(entry.getKey());
            }
        }

        // 定義順などを維持したい場合はここでソート推奨
        // children.sort(...);

        // 4. 子ノードを配置
        for (int i = 0; i < children.size(); i++) {
            String childId = children.get(i);

            // 最初の子（本流）は、親と同じ高さ(currentY)を維持して横に伸ばす
            // 2番目以降の子（分岐）は、前の兄弟が使い終わった高さの「さらに1つ下」から開始する
            int childStartY;
            if (i == 0) {
                childStartY = currentY;
            } else {
                childStartY = maxYUsed + 1;
            }

            // 再帰呼び出し
            // 戻り値として「その子がどこまで下に伸びたか」を受け取る
            int childMaxY = placeNodeRecursive(childId, startX, childStartY, nodeMap, layout, depthMap);

            // 全体の最大Yを更新 (次の分岐はこれより下になる)
            if (childMaxY > maxYUsed) {
                maxYUsed = childMaxY;
            }
        }

        // 親に対して「私はここからここまで(maxYUsed)使いました」と報告
        return maxYUsed;
    }

    // スキル習得処理
    private void handleSkillUnlock(Player player, String treeId, String skillId, int camX, int camY) {
        SkilltreeManager.SkillData data = skilltreeManager.load(player.getUniqueId());
        Map<String, Object> skillNode = getNodeById(treeId, skillId);

        if (skillNode == null) return;

        int maxLevel = (int) skillNode.getOrDefault("max_level", 1);
        List<String> requirements = (List<String>) skillNode.getOrDefault("requirements", List.of());

        // 前提確認
        boolean allRequirementsMet = requirements.stream().allMatch(reqId -> {
            Map<String, Object> reqNode = getNodeById(treeId, reqId);
            if (reqNode == null) return false;
            int reqMax = (int) reqNode.getOrDefault("max_level", 1);
            return data.hasSkill(reqId) && data.getSkillLevel(reqId) == reqMax;
        });

        if (!allRequirementsMet) {
            player.sendMessage(ChatColor.RED + "前提スキルが足りません。");
            return;
        }

        List<Map<?, ?>> trees = treeConfig.getMapList("trees"); // 設定ファイルから全ツリー取得
        for (String learnedSkillId : data.getSkills().keySet()) {
            // 習得済みスキル(learnedSkillId)のノード定義を取得
            Map<String, Object> learnedNode = getNodeById(treeId, learnedSkillId);
            if (learnedNode != null) {
                List<String> conflicts = (List<String>) learnedNode.getOrDefault("conflicts", List.of());

                if (conflicts.contains(skillId)) {
                    player.sendMessage(ChatColor.RED + "既に取得済みのスキル「" + learnedNode.get("name") + "」と競合するため、このスキルは取得できません。");
                    return;
                }
            }
        }

        if (!data.canLevelUp(skillId, maxLevel)) {
            player.sendMessage(ChatColor.YELLOW + "これ以上レベルアップできません。");
            return;
        }

        if (data.getSkillPoint() <= 0) {
            player.sendMessage(ChatColor.RED + "スキルポイントが不足しています。");
            return;
        }

        // 習得実行
        data.unlock(skillId);
        data.setSkillPoint(data.getSkillPoint() - 1);
        skilltreeManager.save(player.getUniqueId(), data);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        player.sendMessage(ChatColor.GREEN + "スキル習得: " + skillNode.get("name"));

        // 同じ位置で開き直す
        openTreeGUI(player, treeId, camX, camY);
    }

    // --- ヘルパーメソッド群 ---

    private void createControlBtn(Inventory inv, int slot, Material mat, String name, String dir, String treeId, int x, int y) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

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

        // --- 修正: desc の取得 ---
        String desc = "No Description";
        if (node.get("desc") != null) {
            desc = (String) node.get("desc");
        }

        // --- 修正: skillEffectId の取得 ---
        // effectフィールドがあればそれを、なければidを使用
        String skillEffectId = id;
        if (node.get("effect") != null) {
            skillEffectId = (String) node.get("effect");
        }

        // --- 修正: maxLevel の取得 ---
        int maxLevel = 1;
        if (node.get("max_level") != null) {
            maxLevel = (Integer) node.get("max_level");
        }

        boolean learned = data.hasSkill(id);
        int level = data.getSkillLevel(id);

        // --- 修正: requirements の取得 ---
        List<String> reqs = Collections.emptyList();
        if (node.get("requirements") != null) {
            reqs = (List<String>) node.get("requirements");
        }

        // 要件チェック (修正なし、そのまま利用)
        boolean unlocked = reqs.isEmpty() || reqs.stream().allMatch(r -> {
            Map<String, Object> reqNode = getNodeById(treeId, r);
            int reqMax = 1;
            if (reqNode != null && reqNode.get("max_level") != null) {
                reqMax = (Integer) reqNode.get("max_level");
            }
            return data.hasSkill(r) && data.getSkillLevel(r) >= reqMax;
        });

        // --- 修正: conflicts の取得 ---
        List<String> conflicts = Collections.emptyList();
        if (node.get("conflicts") != null) {
            conflicts = (List<String>) node.get("conflicts");
        }

        boolean isConflicted = false;
        for (String learnedId : data.getSkills().keySet()) {
            Map<String, Object> learnedNode = getNodeById(treeId, learnedId);
            if (learnedNode != null) {
                // --- 修正: learnedConflicts の取得 ---
                List<String> learnedConflicts = Collections.emptyList();
                if (learnedNode.get("conflicts") != null) {
                    learnedConflicts = (List<String>) learnedNode.get("conflicts");
                }

                if (learnedConflicts.contains(id)) {
                    isConflicted = true;
                    break;
                }
            }
        }

        if (isConflicted) unlocked = false;

        // --- マテリアルとLoreの準備 ---
        Material mat = Material.GRAY_STAINED_GLASS_PANE; // デフォルト
        List<String> lore = new ArrayList<>();

        // ★ type: skill の場合の処理
        if ("skill".equals(type)) {
            // SkillLoaderからスキル定義を取得
            SkillDefinition skill = skillLoader.get(skillEffectId);

            if (skill != null) {
                // スキル定義が見つかった場合
                mat = skill.material; // アイコンをスキルのものに変更
                name = skill.name;    // 名前をスキルのものに変更

                // スキル本来の説明文(lore)を追加
                if (skill.lore != null) {
                    for (String loreLine : skill.lore) {
                        String translatedLine = ChatColor.translateAlternateColorCodes('&', loreLine);

                        // プレースホルダー置換
                        double effectiveCooldown = StatManager.getEffectiveCooldown(player, skill.cooldown);
                        double manaCost = skill.manaCost;

                        translatedLine = translatedLine.replace("{cooldown}", String.format("%.1f", effectiveCooldown));
                        translatedLine = translatedLine.replace("{mana}", String.format("%.1f", manaCost));

                        lore.add(ChatColor.GRAY + translatedLine);
                    }
                }
            } else {
                // スキル定義が見つからない場合のフォールバック
                mat = Material.RED_STAINED_GLASS_PANE;
                lore.add(ChatColor.RED + "Error: Skill definition not found for '" + skillEffectId + "'");
            }
        } else {
            // --- 従来の表示処理 (buff, starterなど) ---
            if (learned) mat = Material.LIME_STAINED_GLASS_PANE;
            else if (unlocked) mat = Material.YELLOW_STAINED_GLASS_PANE;
            else mat = Material.RED_STAINED_GLASS_PANE;

            if (isConflicted) mat = Material.RED_STAINED_GLASS_PANE;
            if ("starter".equals(type)) mat = Material.BEACON;

            // YAMLのdescを追加
            lore.add(ChatColor.GRAY + desc);
        }

        // --- 共通情報の追加 (ステータス表示) ---
        // スキルの説明と、システム的な情報（必要スキルなど）の間に空白を入れる
        if (!lore.isEmpty()) lore.add("");

        lore.add(ChatColor.GRAY + "ID: " + id);

        // 習得状況による色分け
        ChatColor nameColor;
        String statusText;

        if (learned) {
            nameColor = ChatColor.GREEN;
            statusText = ChatColor.GREEN + "■ 習得済み";
        } else if (isConflicted) {
            nameColor = ChatColor.RED;
            statusText = ChatColor.RED + "■ 習得不可 (競合)";
        } else if (unlocked) {
            nameColor = ChatColor.YELLOW;
            statusText = ChatColor.YELLOW + "■ 習得可能";
        } else {
            nameColor = ChatColor.RED;
            statusText = ChatColor.RED + "■ 未解除";
        }

        lore.add(statusText);
        lore.add(ChatColor.WHITE + "Lv: " + level + "/" + maxLevel);

        // 必要スキル表示
        if (!reqs.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.GOLD + "[必要スキル]");
            for (String r : reqs) {
                boolean hasReq = data.hasSkill(r);
                lore.add((hasReq ? ChatColor.GREEN : ChatColor.RED) + "- " + r);
            }
        }

        // 競合表示
        if (!conflicts.isEmpty()) {
            lore.add("");
            lore.add(ChatColor.DARK_RED + "排他選択 (競合):");
            for (String conflictId : conflicts) {
                Map<String, Object> conflictNode = getNodeById(treeId, conflictId);
                String conflictName = (conflictNode != null) ? (String) conflictNode.get("name") : conflictId;
                lore.add(ChatColor.RED + "- " + conflictName);
            }
        }

        // --- アイテム生成 ---
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // 名前の色を設定
        meta.setDisplayName(nameColor + name);
        meta.setLore(lore);

        // 習得済みならエンチャントの光をつける
        if (learned) {
            meta.addEnchant(Enchantment.DENSITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // アイコン以外の属性（攻撃力など）を隠す
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

    // 既存のgetNodeByIdメソッド (少し整理)
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
}