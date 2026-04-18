package com.lunar_prototype.deepwither;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemBuildContext {
    private ItemStack item;
    private StatMap baseStats;
    private Map<StatType, Double> modifiers;
    private String itemType;
    private List<String> flavorText;
    private ItemLoader.RandomStatTracker tracker;
    private String rarity;
    private String artifactFullsetType;
    private FabricationGrade grade;

    private ItemBuildContext(Builder builder) {
        this.item = builder.item;
        this.baseStats = builder.baseStats;
        this.modifiers = builder.modifiers;
        this.itemType = builder.itemType;
        this.flavorText = builder.flavorText;
        this.tracker = builder.tracker;
        this.rarity = builder.rarity;
        this.artifactFullsetType = builder.artifactFullsetType;
        this.grade = builder.grade;
    }

    public static Builder builder(ItemStack item) {
        return new Builder(item);
    }

    public ItemStack getItem() { return item; }
    public StatMap getBaseStats() { return baseStats; }
    public Map<StatType, Double> getModifiers() { return modifiers; }
    public String getItemType() { return itemType; }
    public List<String> getFlavorText() { return flavorText; }
    public ItemLoader.RandomStatTracker getTracker() { return tracker; }
    public String getRarity() { return rarity; }
    public String getArtifactFullsetType() { return artifactFullsetType; }
    public FabricationGrade getGrade() { return grade; }

    public void setGrade(FabricationGrade grade) { this.grade = grade; }
    public void setItem(ItemStack item) { this.item = item; }

    public static class Builder {
        private ItemStack item;
        private StatMap baseStats = new StatMap();
        private Map<StatType, Double> modifiers = new HashMap<>();
        private String itemType;
        private List<String> flavorText;
        private ItemLoader.RandomStatTracker tracker = new ItemLoader.RandomStatTracker();
        private String rarity;
        private String artifactFullsetType;
        private FabricationGrade grade;

        public Builder(ItemStack item) {
            this.item = item;
        }

        public Builder baseStats(StatMap baseStats) { this.baseStats = baseStats; return this; }
        public Builder modifiers(Map<StatType, Double> modifiers) {
            this.modifiers = modifiers != null ? modifiers : new HashMap<>();
            return this; }
        public Builder itemType(String itemType) { this.itemType = itemType; return this; }
        public Builder flavorText(List<String> flavorText) { this.flavorText = flavorText; return this; }
        public Builder tracker(ItemLoader.RandomStatTracker tracker) { this.tracker = tracker; return this; }
        public Builder rarity(String rarity) { this.rarity = rarity; return this; }
        public Builder artifactFullsetType(String artifactFullsetType) { this.artifactFullsetType = artifactFullsetType; return this; }
        public Builder grade(FabricationGrade grade) { this.grade = grade; return this; }

        public ItemBuildContext build() {
            return new ItemBuildContext(this);
        }
    }
}
