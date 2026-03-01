package com.lunar_prototype.deepwither.modules.mob.service;

import com.lunar_prototype.deepwither.Deepwither;
import com.lunar_prototype.deepwither.util.IManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.stream.Collectors;

public class MobTraitService implements IManager {

    private final Deepwither plugin;
    private final NamespacedKey TRAIT_KEY;
    private static final double TRAIT_SPAWN_CHANCE = 0.15;
    private BukkitTask spawnTask;

    public MobTraitService(Deepwither plugin) {
        this.plugin = plugin;
        this.TRAIT_KEY = new NamespacedKey(plugin, "mob_traits");
    }

    @Override
    public void init() {
        startGlobalTraitTicker();
    }

    @Override
    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    public enum MobTrait {
        BERSERK(Component.text("[狂化]", NamedTextColor.RED), "Basic"),
        HARDENED(Component.text("[硬化]", NamedTextColor.GRAY), "Basic"),
        PIERCING(Component.text("[貫通]", NamedTextColor.YELLOW), "Basic"),
        SNARING_AURA(Component.text("[鈍足]", NamedTextColor.BLUE), "Basic"),
        MANA_LEECH(Component.text("[魔食]", NamedTextColor.LIGHT_PURPLE), "Intermediate"),
        DISRUPTIVE(Component.text("[妨害]", NamedTextColor.DARK_PURPLE), "Intermediate"),
        BLINKING(Component.text("[瞬身]", NamedTextColor.AQUA), "Intermediate"),
        SUMMONER(Component.text("[召喚]", NamedTextColor.GREEN), "Intermediate");

        private final Component displayName;
        private final String category;

        MobTrait(Component displayName, String category) {
            this.displayName = displayName;
            this.category = category;
        }

        public Component getDisplayName() { return displayName; }
        public String getCategory() { return category; }
    }

    public void applyRandomTraits(LivingEntity entity, int level) {
        if (plugin.getRandom().nextDouble() > TRAIT_SPAWN_CHANCE) return;
        List<MobTrait> selectedTraits = getRandomTraitsForLevel(level);
        if (selectedTraits.isEmpty()) return;

        String traitData = selectedTraits.stream().map(Enum::name).collect(Collectors.joining(","));
        entity.getPersistentDataContainer().set(TRAIT_KEY, PersistentDataType.STRING, traitData);
        entity.setGlowing(true);

        Component traitText = Component.empty();
        for (int i = 0; i < selectedTraits.size(); i++) {
            traitText = traitText.append(selectedTraits.get(i).displayName);
            if (i < selectedTraits.size() - 1) traitText = traitText.append(Component.text(" "));
        }

        Component finalTraitText = traitText;
        TextDisplay display = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, (td) -> {
            td.text(finalTraitText);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            Transformation transformation = td.getTransformation();
            transformation.getTranslation().set(0, 1.2f, 0);
            td.setTransformation(transformation);
        });
        entity.addPassenger(display);
        startCleanupTask(entity, display);
    }

    private void startGlobalTraitTicker() {
        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    for (LivingEntity entity : world.getLivingEntities()) {
                        List<String> traits = getTraits(entity);
                        if (traits.isEmpty()) continue;
                        if (traits.contains("SNARING_AURA")) {
                            entity.getNearbyEntities(5, 5, 5).stream()
                                    .filter(e -> e instanceof Player)
                                    .forEach(p -> ((Player)p).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0)));
                        }
                        if (traits.contains("SUMMONER") && plugin.getRandom().nextDouble() < 0.05) {
                            long nearbySilverfish = entity.getNearbyEntities(8, 4, 8).stream()
                                    .filter(e -> e.getType() == EntityType.SILVERFISH)
                                    .count();
                            if (nearbySilverfish < 3) {
                                entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SILVERFISH);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private List<String> getTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null) return List.of();
        return Arrays.asList(data.split(","));
    }

    private void startCleanupTask(LivingEntity mob, TextDisplay display) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    display.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public List<String> getMobTraits(LivingEntity entity) {
        String data = entity.getPersistentDataContainer().get(TRAIT_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return Collections.emptyList();
        return Arrays.asList(data.split(","));
    }

    private List<MobTrait> getRandomTraitsForLevel(int level) {
        List<MobTrait> available = new ArrayList<>();
        int count = 0;
        if (level >= 10 && level < 15) { count = 1; available = getTraitsByCategory("Basic"); }
        else if (level >= 15 && level < 25) { count = plugin.getRandom().nextInt(3) + 1; available = getTraitsByCategory("Basic"); }
        else if (level >= 25 && level <= 35) { count = plugin.getRandom().nextInt(2) + 1; available = getTraitsByCategory("Intermediate"); }
        if (available.isEmpty()) return Collections.emptyList();
        Collections.shuffle(available);
        return available.stream().limit(count).collect(Collectors.toList());
    }

    private List<MobTrait> getTraitsByCategory(String category) {
        return Arrays.stream(MobTrait.values()).filter(t -> t.category.equals(category)).collect(Collectors.toList());
    }
}
