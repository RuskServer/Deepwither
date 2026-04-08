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

    /** 現在選択中のスキルID（2ステップ操作用） */
    private final Map<UUID, String> selectedSkillMap = new HashMap<>();
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

        // ---- スキル一覧（行0〜4, slots 0〜35） ----
        SkillData data = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        String selectedId = selectedSkillMap.get(player.getUniqueId());
        int index = 0;

        for (String skillId : data.getSkills().keySet()) {
            if (index >= 36) break; // 最大36スキルまで表示
            SkillDefinition skill = skillLoader.get(skillId);
            if (skill == null) continue;

            ItemStack item = buildSkillItem(player, skill, skillId.equals(selectedId));
            gui.setItem(index++, item);
        }

        // ---- セパレーター（行4, slots 36〜44） ----
        ItemStack sep = buildSeparator();
        for (int i = 36; i <= 44; i++) {
            gui.setItem(i, sep);
        }

        // ---- スキルスロット（行5, slots 45〜48） ----
        SkillSlotData slotData = slotManager.get(player.getUniqueId());
        for (int i = 0; i < 4; i++) {
            gui.setItem(45 + i, buildSlotItem(i, slotData));
        }

        // ---- 操作ガイド（slot 53） ----
        gui.setItem(53, buildGuideItem());

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

    /** スロットアイテム。割り当て済みならスキルのアイコン、未割り当てならグレーガラス */
    private ItemStack buildSlotItem(int slotIndex, SkillSlotData slotData) {
        String assigned = slotData.getSkill(slotIndex);
        String slotLabel = "スロット" + (slotIndex + 1);

        if (assigned != null) {
            SkillDefinition assignedSkill = skillLoader.get(assigned);
            if (assignedSkill != null) {
                // スキル自体のアイコンをそのまま使用
                ItemStack item = new ItemStack(assignedSkill.material);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(slotLabel, NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("割り当て済み: " + assignedSkill.name, NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("左クリック: 別のスキルを割り当て", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("右クリック: スロットをクリア", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("slot:" + slotIndex, NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
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

    // ===== クリックイベント =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return;

        UUID uuid = player.getUniqueId();

        // Lore からスキルIDとスロット番号を解析
        String skillId = null;
        int slotIndex = -1;

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
        if (slotIndex >= 0 && slotIndex < 4) {
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