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
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("スキル割り当て", NamedTextColor.DARK_GREEN));

        SkilltreeManager.SkillData data = Deepwither.getInstance().getSkilltreeManager().load(player.getUniqueId());
        int index = 0;
        for (String skillId : data.getSkills().keySet()) {
            SkillDefinition skill = skillLoader.get(skillId);
            if (skill == null) continue;

            ItemStack item = new ItemStack(skill.material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(skill.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String loreLine : skill.lore) {
                double effectiveCooldown = StatManager.getEffectiveCooldown(player, skill.cooldown);
                double manaCost = skill.manaCost;
                String translated = loreLine.replace("{cooldown}", String.format("%.1f", effectiveCooldown))
                                           .replace("{mana}", String.format("%.1f", manaCost));
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(translated).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("クリックでスロットに割り当て", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("ID:" + skillId, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);

            gui.setItem(index++, item);
        }

        SkillSlotData slotData = slotManager.get(player.getUniqueId());
        for (int i = 0; i < 4; i++) {
            String assigned = slotData.getSkill(i);
            ItemStack slotItem;
            if (assigned != null) {
                SkillDefinition assignedSkill = skillLoader.get(assigned);
                slotItem = new ItemStack(Material.BOOK);
                ItemMeta meta = slotItem.getItemMeta();
                meta.displayName(Component.text("スロット" + (i + 1), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("割り当て済み: " + assignedSkill.name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        Component.text("slot:" + i, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                ));
                slotItem.setItemMeta(meta);
            } else {
                slotItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = slotItem.getItemMeta();
                meta.displayName(Component.text("スロット" + (i + 1), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("slot:" + i, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
                slotItem.setItemMeta(meta);
            }
            gui.setItem(45 + i, slotItem);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text("スキル割り当て", NamedTextColor.DARK_GREEN))) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return;

        String skillId = null;
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.contains("ID:")) {
                skillId = plain.replace("ID:", "").trim();
                break;
            }
        }

        if (skillId != null) {
            selectedSkillMap.put(player.getUniqueId(), skillId);
            player.sendMessage(Component.text("スロットをクリックして割り当ててください。", NamedTextColor.YELLOW));
            return;
        }

        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.contains("slot:")) {
                try {
                    int slot = Integer.parseInt(plain.replace("slot:", "").trim());
                    String selected = selectedSkillMap.get(player.getUniqueId());
                    if (selected == null) {
                        player.sendMessage(Component.text("先にスキルを選択してください。", NamedTextColor.RED));
                        return;
                    }

                    slotManager.setSlot(player.getUniqueId(), slot, selected);
                    slotManager.saveAll();
                    selectedSkillMap.remove(player.getUniqueId());
                    player.sendMessage(Component.text("スキル「" + selected + "」をスロット" + (slot + 1) + "に割り当てました！", NamedTextColor.GREEN));
                    open(player);
                    return;
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("スロット番号の解析に失敗しました。", NamedTextColor.RED));
                }
            }
        }
    }
}