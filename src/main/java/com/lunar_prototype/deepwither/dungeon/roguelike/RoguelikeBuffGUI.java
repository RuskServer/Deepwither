package com.lunar_prototype.deepwither.dungeon.roguelike;

import com.lunar_prototype.deepwither.Deepwither;
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

public class RoguelikeBuffGUI implements Listener {

    private final Deepwither plugin;
    private static final String GUI_TITLE = "§8Choose a Buff";

    public RoguelikeBuffGUI(Deepwither plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // アニメーション中のプレイヤーを保持（クリック防止用）
    private final Set<UUID> animatingPlayers = new HashSet<>();

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 1. 背景の設置
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // 2. バフの選出（元のロジックを維持）
        List<RoguelikeBuff> allBuffs = new ArrayList<>(Arrays.asList(RoguelikeBuff.values()));
        Collections.shuffle(allBuffs);
        List<RoguelikeBuff> choices = allBuffs.subList(0, Math.min(3, allBuffs.size()));

        int[] slots = { 11, 13, 15 };

        // インベントリを開く（この時点では中身はガラスのみ）
        player.openInventory(inv);
        animatingPlayers.add(player.getUniqueId());
        Deepwither.getInstance().getRoguelikeBuffManager().startBuffSelection(player);

        // 3. 演出用タスク
        new BukkitRunnable() {
            int tick = 0;
            int revealed = 0;
            final Random random = new Random();
            final Material[] rouletteIcons = {Material.ENCHANTED_BOOK, Material.DRAGON_BREATH, Material.NETHER_STAR, Material.FIREWORK_STAR};

            @Override
            public void run() {
                // プレイヤーが閉じたら終了
                if (!player.getOpenInventory().getTitle().equals(GUI_TITLE)) {
                    animatingPlayers.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                // まだ公開されていないスロットをランダムなアイテムでシャッフル
                for (int i = revealed; i < slots.length; i++) {
                    inv.setItem(slots[i], new ItemStack(rouletteIcons[random.nextInt(rouletteIcons.length)]));
                }

                // チッ、チッ、という音（ピッチを少しずつ上げる）
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f + (tick * 0.05f));

                // 15ティック(0.75秒)ごとに1つずつ確定
                if (tick > 0 && tick % 15 == 0) {
                    RoguelikeBuff buff = choices.get(revealed);
                    inv.setItem(slots[revealed], createBuffItem(buff));

                    // 確定時の「ドン！」という音とエフェクト
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);

                    revealed++;
                }

                // 全て確定
                if (revealed >= choices.size()) {
                    animatingPlayers.remove(player.getUniqueId()); // クリック解禁
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    this.cancel();
                }
                tick++;
            }
        }.runTaskTimer(Deepwither.getInstance(), 1L, 2L); // 0.1秒間隔
    }

    private ItemStack createBuffItem(RoguelikeBuff buff) {
        ItemStack item = new ItemStack(buff.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(buff.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(buff.getDescription());
        lore.add("");
        lore.add("§eクリックして獲得！");
        meta.setLore(lore);

        // バフの種類を特定するためにNBTや隠しデータを仕込むのが定石だが、
        // 今回はシンプルにDisplayNameやLore、あるいはスロット位置とセッション管理で判断する。
        // ここではクリックイベントでItemMetaから逆引きするか、あるいは
        // Inventory自体にHolderを持たせるのが良いが、簡易実装として表示名の一致確認を行う。
        // (より堅牢にするならPersistentDataContainer推奨)

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE))
            return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player))
            return;

        if (animatingPlayers.contains(player.getUniqueId())) {
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;

        // クリックされたアイテムからバフを特定
        RoguelikeBuff selectedBuff = null;
        for (RoguelikeBuff buff : RoguelikeBuff.values()) {
            if (clicked.getItemMeta().getDisplayName().equals(buff.getDisplayName())) {
                selectedBuff = buff;
                break;
            }
        }

        if (selectedBuff != null) {
            // バフ適用
            Deepwither.getInstance().getRoguelikeBuffManager().addBuff(player, selectedBuff);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(GUI_TITLE)) {
            if (e.getPlayer() instanceof Player player) {
                Deepwither.getInstance().getRoguelikeBuffManager().endBuffSelection(player);
            }
        }
    }
}
