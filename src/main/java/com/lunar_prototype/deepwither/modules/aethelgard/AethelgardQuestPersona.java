package com.lunar_prototype.deepwither.modules.aethelgard;

public enum AethelgardQuestPersona {
    GUILD_OFFICIAL("ギルド事務官", "事務的で冷徹な口調。効率と事実を重視する。"),
    VETERAN_MERCENARY("熟練の傭兵", "粗野だが信頼感のある口調。戦場での経験を強調する。"),
    WORRIED_CITIZEN("心配性の市民", "丁寧だが切迫した口調。助けを求める姿勢が強い。"),
    MYSTERIOUS_PATRON("謎の依頼主", "慇懃無礼で含みのある口調。目的を完全には明かさない。");

    private final String displayName;
    private final String description;

    AethelgardQuestPersona(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
