package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

@DependsOn({RoguelikeBuffManager.class})
public class RoguelikeBuffGUI implements Listener, IManager {

    private final Deepwither plugin;
    public static final Component GUI_TITLE = Component.text("Choose a Buff", NamedTextColor.DARK_GRAY);

    public RoguelikeBuffGUI(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {}

    private final Set<UUID> animatingPlayers = new HashSet<>();

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        List<RoguelikeBuff> allBuffs = new ArrayList<>(Arrays.asList(RoguelikeBuff.values()));
        Collections.shuffle(allBuffs);
        List<RoguelikeBuff> choices = allBuffs.subList(0, Math.min(3, allBuffs.size()));

        int[] slots = { 11, 13, 15 };

        player.openInventory(inv);
        animatingPlayers.add(player.getUniqueId());
        Deepwither.getInstance().getRoguelikeBuffManager().startBuffSelection(player);

        new BukkitRunnable() {
            int tick = 0;
            int revealed = 0;
            final Random random = new Random();
            final Material[] rouletteIcons = {Material.ENCHANTED_BOOK, Material.DRAGON_BREATH, Material.NETHER_STAR, Material.FIREWORK_STAR};

            @Override
            public void run() {
                if (!player.getOpenInventory().title().equals(GUI_TITLE)) {
                    animatingPlayers.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                for (int i = revealed; i < slots.length; i++) {
                    inv.setItem(slots[i], new ItemStack(rouletteIcons[random.nextInt(rouletteIcons.length)]));
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f + (tick * 0.05f));

                if (tick > 0 && tick % 15 == 0) {
                    RoguelikeBuff buff = choices.get(revealed);
                    inv.setItem(slots[revealed], createBuffItem(buff));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
                    revealed++;
                }

                if (revealed >= choices.size()) {
                    animatingPlayers.remove(player.getUniqueId());
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    this.cancel();
                }
                tick++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 1L, 2L);
    }

    private ItemStack createBuffItem(RoguelikeBuff buff) {
        ItemStack item = new ItemStack(buff.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(buff.getDisplayName());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(buff.getDescription());
        lore.add(Component.empty());
        lore.add(Component.text("クリックして獲得！", NamedTextColor.YELLOW));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(GUI_TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (animatingPlayers.contains(player.getUniqueId())) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        RoguelikeBuff selectedBuff = null;
        for (RoguelikeBuff buff : RoguelikeBuff.values()) {
            if (clicked.getItemMeta().displayName().equals(buff.getDisplayName())) {
                selectedBuff = buff;
                break;
            }
        }

        if (selectedBuff != null) {
            Deepwither.getInstance().getRoguelikeBuffManager().addBuff(player, selectedBuff);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (e.getView().title().equals(GUI_TITLE)) {
            if (e.getPlayer() instanceof Player player) {
                Deepwither.getInstance().getRoguelikeBuffManager().endBuffSelection(player);
            }
        }
    }
}