package com.lunar_prototype.deepwither.aethelgard;

// org.bukkit.entity.EntityTypeの使用をやめ、カスタムMob ID（文字列）を使用するように変更

public enum ExterminationType {
    // Mob ID (MythicMobs IDなど), Mobの修飾語
    ZOMBIE_GUARD("食屍鬼", "食屍鬼"),
    SPIDER_LORD("bandit_sword", "無法者"),
    SKELETAL_ARCHER("エルドグール", "エルドグール"),
    PHANTOM_KING("アークグール", "アークグール");

    private final String mobId; // MythicMobsのIDなど、ゲーム内での一意な識別子
    private final String description; // LLMに渡すための自然な表現

    ExterminationType(String mobId, String description) {
        this.mobId = mobId;
        this.description = description;
    }

    public String getMobId() {
        return mobId;
    }

    public String getDescription() {
        return description;
    }
}