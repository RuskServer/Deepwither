package com.lunar_prototype.deepwither;

import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@DependsOn({SkillSlotManager.class, SkillLoader.class})
public class SkillAssignmentGUI implements Listener, IManager {

    private static final Component GUI_TITLE = Component.text("スキル割り当て", NamedTextColor.DARK_GREEN);
    private static final int BACK_SLOT = 40;

    /** 現在選択中のスキルID（2ステップ操作用） */
    private final Map<UUID, String> selectedSkillMap = new HashMap<>();
    /** プレイヤーの現在のページ番号 (0-indexed) */
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private SkillSlotManager slotManager;
    private SkillLoader skillLoader;
    private final JavaPlugin plugin;

    public SkillAssignmentGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.slotManager = Deepwither.getInstance().getSkillSlotManager();
        this.skillLoader = Deepwither.getInstance().getSkillLoader();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    // ===== GUI 構築 =====

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        UUID uuid = player.getUniqueId();

        SkillData data = Deepwither.getInstance().getSkilltreeManager().load(uuid);
        String selectedId = selectedSkillMap.get(uuid);
        
        // プレイヤーが持つスキルリストを取得し、現在無効な(存在しない)スキルを事前に除外して詰める
        List<String> unlockedSkills = new ArrayList<>(data.getSkills().keySet());
        unlockedSkills.removeIf(id -> skillLoader.get(id) == null);

        int totalSkills = unlockedSkills.size();
        int maxPage = Math.max(0, (totalSkills - 1) / 36);
        
        // 現在のページを取得・補正
        int currentPage = playerPages.getOrDefault(uuid, 0);
        if (currentPage > maxPage) currentPage = maxPage;
        if (currentPage < 0) currentPage = 0;
        playerPages.put(uuid, currentPage);

        // ---- スキル一覧（行0〜4, slots 0〜35） ----
        int startIndex = currentPage * 36;
        int endIndex = Math.min(startIndex + 36, totalSkills);
        
        for (int i = startIndex; i < endIndex; i++) {
            String skillId = unlockedSkills.get(i);
            SkillDefinition skill = skillLoader.get(skillId);
            if (skill == null) continue;

            int slot = i - startIndex;
            ItemStack item = buildSkillItem(player, skill, skillId.equals(selectedId));
            gui.setItem(slot, item);
        }

        // 残りの空きスロット（0〜35のうち、スキルが配置されなかった部分）をプレースホルダーで埋める
        ItemStack emptySlotItem = buildEmptySlotItem();
        int filledSlots = endIndex - startIndex;
        for (int slot = filledSlots; slot < 36; slot++) {
            gui.setItem(slot, emptySlotItem);
        }

        // ---- セパレーターとページ切り替え（行4, slots 36〜44） ----
        ItemStack sep = buildSeparator();
        for (int i = 36; i <= 44; i++) {
            gui.setItem(i, sep);
        }
        
        // 「前へ」ボタン (slot 36)
        if (currentPage > 0) {
            gui.setItem(36, buildNavButton("前へ", "page:" + (currentPage - 1)));
        }
        // 「次へ」ボタン (slot 44)
        if (currentPage < maxPage) {
            gui.setItem(44, buildNavButton("次へ", "page:" + (currentPage + 1)));
        }

        // ---- スキルスロット（行5, slots 45〜53） ----
        SkillSlotData slotData = slotManager.get(uuid);
        for (int i = 0; i < 9; i++) {
            gui.setItem(45 + i, buildSlotItem(player, i, slotData));
        }

        // ---- 操作ガイド（slot 41） ----
        gui.setItem(BACK_SLOT, buildBackButton());
        gui.setItem(41, buildGuideItem());

        player.openInventory(gui);
    }

    // ===== アイテム生成ヘルパー =====

    /** スキルリスト用アイテム。選択中の場合はグロウ付与 */
    private ItemStack buildSkillItem(Player player, SkillDefinition skill, boolean isSelected) {
        ItemStack item = new ItemStack(skill.material);
        ItemMeta meta = item.getItemMeta();

        // 名前
        Component nameComponent = Component.text(skill.name, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        if (isSelected) {
            // 選択中は名前の前に ▶ を付けて強調
            nameComponent = Component.text("▶ ", NamedTextColor.YELLOW)
                    .append(nameComponent);
        }
        meta.displayName(nameComponent);

        // Lore
        List<Component> lore = new ArrayList<>();
        
        List<Component> tagsLore = SkillTags.buildTagLore(skill);
        if (!tagsLore.isEmpty()) {
            lore.addAll(tagsLore);
            lore.add(Component.empty());
        }

        for (String loreLine : skill.lore) {
            double effectiveCooldown = StatManager.getEffectiveCooldown(player, skill.cooldown);
            double manaCost = skill.manaCost;
            String translated = loreLine
                    .replace("{cooldown}", String.format("%.1f", effectiveCooldown))
                    .replace("{mana}", String.format("%.1f", manaCost));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(translated)
                    .colorIfAbsent(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());

        if (isSelected) {
            lore.add(Component.text("✔ 選択中 — スロットをクリックして割り当て", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("左クリックで選択", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        // 内部ID（ロジック用、表示は最小化）
        lore.add(Component.text("ID:" + skill.id, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // 選択中はエンチャントグロウ付与
        if (isSelected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /** スロットアイテム。割り当て済みならスキルのアイコンと説明、未割り当てならグレーガラス */
    private ItemStack buildSlotItem(Player player, int slotIndex, SkillSlotData slotData) {
        String assigned = slotData.getSkill(slotIndex);
        String slotLabel = "スロット" + (slotIndex + 1);

        if (assigned != null) {
            SkillDefinition assignedSkill = skillLoader.get(assigned);
            if (assignedSkill != null) {
                ItemStack item = new ItemStack(assignedSkill.material);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(slotLabel, NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("割り当て済み: " + assignedSkill.name, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());

                // スキルの詳細説明を追加
                List<Component> tagsLore = SkillTags.buildTagLore(assignedSkill);
                if (!tagsLore.isEmpty()) {
                    lore.addAll(tagsLore);
                    lore.add(Component.empty());
                }

                for (String loreLine : assignedSkill.lore) {
                    double effectiveCooldown = StatManager.getEffectiveCooldown(player, assignedSkill.cooldown);
                    double manaCost = assignedSkill.manaCost;
                    String translated = loreLine
                            .replace("{cooldown}", String.format("%.1f", effectiveCooldown))
                            .replace("{mana}", String.format("%.1f", manaCost));
                    lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(translated)
                            .colorIfAbsent(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.empty());

                lore.add(Component.text("左クリック: 別のスキルを割り当て", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("右クリック: スロットをクリア", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("slot:" + slotIndex, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
                item.setItemMeta(meta);
                return item;
            }
        }

        // 未割り当て
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(slotLabel + " — 未割り当て", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("スキルを選択してからクリックすると割り当てられます", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("slot:" + slotIndex, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSeparator() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildEmptySlotItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("【空きスロット】", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildNavButton(String name, String actionInfo) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("左クリックでページを切り替える", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(actionInfo, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildGuideItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("操作ガイド", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("① 上段からスキルを左クリックで選択", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("② 下段のスロットをクリックで割り当て", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("  右クリックでスロットをクリア", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("メインメニューへ戻る", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Main Menu に戻ります", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ===== クリックイベント =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        int slot = event.getSlot();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return;

        UUID uuid = player.getUniqueId();

        // Lore からスキルIDとスロット番号を解析
        String skillId = null;
        int slotIndex = -1;
        int pageAction = -1;

        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.startsWith("ID:")) {
                skillId = plain.substring(3).trim();
            }
            if (plain.startsWith("slot:")) {
                try {
                    slotIndex = Integer.parseInt(plain.substring(5).trim());
                } catch (NumberFormatException ignored) {}
            }
            if (plain.startsWith("page:")) {
                try {
                    pageAction = Integer.parseInt(plain.substring(5).trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        if (slot == BACK_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            player.closeInventory();
            Deepwither.getInstance().getMenuGUI().open(player);
            return;
        }
        
        // ---- ページ切り替えボタン ----
        if (pageAction != -1) {
            playerPages.put(uuid, pageAction);
            open(player);
            return;
        }

        // ---- スキルアイテムをクリック → 選択 ----
        if (skillId != null) {
            if (skillId.equals(selectedSkillMap.get(uuid))) {
                // 同じスキルを再クリック → 選択解除
                selectedSkillMap.remove(uuid);
                player.sendMessage(Component.text("選択を解除しました。", NamedTextColor.GRAY));
            } else {
                selectedSkillMap.put(uuid, skillId);
                SkillDefinition def = skillLoader.get(skillId);
                String name = (def != null) ? def.name : skillId;
                player.sendMessage(Component.text("「" + name + "」を選択しました。スロットをクリックして割り当ててください。", NamedTextColor.YELLOW));
            }
            open(player); // 選択状態のグロウを反映するためGUIを再描画
            return;
        }

        // ---- スロットをクリック ----
        if (slotIndex >= 0 && slotIndex < 9) {
            // 右クリック → スロットクリア
            if (event.getClick() == ClickType.RIGHT) {
                slotManager.setSlot(uuid, slotIndex, null);
                slotManager.saveAll();
                selectedSkillMap.remove(uuid);
                player.sendMessage(Component.text("スロット" + (slotIndex + 1) + "をクリアしました。", NamedTextColor.GRAY));
                open(player);
                return;
            }

            // 左クリック → 選択中スキルを割り当て
            String selected = selectedSkillMap.get(uuid);
            if (selected == null) {
                player.sendMessage(Component.text("先にスキルを選択してください。", NamedTextColor.RED));
                return;
            }

            SkillDefinition def = skillLoader.get(selected);
            String skillName = (def != null) ? def.name : selected;

            slotManager.setSlot(uuid, slotIndex, selected);
            slotManager.saveAll();
            selectedSkillMap.remove(uuid);
            player.sendMessage(Component.text(
                    "スキル「" + skillName + "」をスロット" + (slotIndex + 1) + "に割り当てました！",
                    NamedTextColor.GREEN));
            open(player);
        }
    }
}
