package com.lunar_prototype.deepwither;

import io.lumine.mythic.api.skills.Skill;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@DependsOn({SkillSlotManager.class, SkillLoader.class})
public class SkillAssignmentGUI implements Listener, IManager {

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

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "スキル割り当て");

        // 取得済みスキル一覧の描画
        SkilltreeManager.SkillData data = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        int index = 0;
        for (String skillId : data.getSkills().keySet()) {
            SkillDefinition skill = skillLoader.get(skillId);
            if (skill == null) continue;

            ItemStack item = new ItemStack(skill.material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + skill.name);
            List<String> lore = new ArrayList<>();
            // スキルの説明文（lore）に色を適用
            for (String loreLine : skill.lore) {
                String translatedLine = ChatColor.translateAlternateColorCodes('&', loreLine);
                // クールダウンとマナのプレースホルダーを置き換える
                double effectiveCooldown = StatManager.getEffectiveCooldown(player, skill.cooldown);
                double manaCost = skill.manaCost;

                translatedLine = translatedLine.replace("{cooldown}", String.format("%.1f", effectiveCooldown));
                translatedLine = translatedLine.replace("{mana}", String.format("%.1f", manaCost));

                lore.add(ChatColor.GRAY +translatedLine);
            }
            lore.add("");
            lore.add(ChatColor.GRAY + "クリックでスロットに割り当て");
            lore.add(ChatColor.DARK_GRAY + "ID:" + skillId);
            meta.setLore(lore);
            item.setItemMeta(meta);

            gui.setItem(index++, item);
        }

        // スロット枠の描画（Slot1〜4）
        SkillSlotData slotData = slotManager.get(player.getUniqueId());
        for (int i = 0; i < 4; i++) {
            String assigned = slotData.getSkill(i);
            ItemStack slotItem;
            if (assigned != null) {
                SkillDefinition assignedSkill = skillLoader.get(assigned);
                slotItem = new ItemStack(Material.BOOK);
                ItemMeta meta = slotItem.getItemMeta();
                meta.setDisplayName(ChatColor.AQUA + "スロット" + (i + 1));
                meta.setLore(List.of(
                        ChatColor.YELLOW + "割り当て済み: " + assignedSkill.name,
                        ChatColor.DARK_GRAY + "slot:" + i
                ));
                slotItem.setItemMeta(meta);
            } else {
                slotItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = slotItem.getItemMeta();
                meta.setDisplayName(ChatColor.GRAY + "スロット" + (i + 1));
                meta.setLore(List.of(ChatColor.DARK_GRAY + "slot:" + i));
                slotItem.setItemMeta(meta);
            }
            gui.setItem(45 + i, slotItem);
        }

        for (String id : data.getSkills().keySet()) {
            SkillDefinition def = skillLoader.get(id);
            if (def == null) {
                Bukkit.getLogger().warning("[SkillGUI] スキルID " + id + " は定義ファイルに存在しません！");
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_GREEN + "スキル割り当て")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null) return;

        // スキルをクリック → どのスキルを割り当てるか一時保持
        String skillId = null;
        for (String line : lore) {
            if (line.contains("ID:")) {
                skillId = ChatColor.stripColor(line.replace("ID:", "").trim());
                break;
            }
        }

        if (skillId != null) {
            // スキル選択状態に（プレイヤー→一時保存）
            selectedSkillMap.put(player.getUniqueId(), skillId);
            player.sendMessage(ChatColor.YELLOW + "スロットをクリックして割り当ててください。");
            return;
        }

        // スロットクリックかどうか
        for (String line : lore) {
            if (ChatColor.stripColor(line).contains("slot:")) {
                try {
                    String stripped = ChatColor.stripColor(line).trim(); // 例: "slot:0"
                    String slotStr = stripped.replace("slot:", "").trim();
                    int slot = Integer.parseInt(slotStr);

                    String selected = selectedSkillMap.get(player.getUniqueId());
                    if (selected == null) {
                        player.sendMessage(ChatColor.RED + "先にスキルを選択してください。");
                        return;
                    }

                    // 割り当て処理
                    slotManager.setSlot(player.getUniqueId(), slot, selected);
                    slotManager.saveAll();
                    selectedSkillMap.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.GREEN + "スキル「" + selected + "」をスロット" + (slot + 1) + "に割り当てました！");
                    Deepwither.getInstance().getSkillAssignmentGUI().open(player);

                    return;
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "スロット番号の解析に失敗しました。");
                    e.printStackTrace();
                }
            }
        }
    }
}

