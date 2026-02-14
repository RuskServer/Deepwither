package com.lunar_prototype.deepwither.dynamic_quest.enums;

public enum QuestPersona {
    T_01_VETERAN("ベテラン", "軍隊式、簡潔、専門用語多用。無駄がない。"),
    T_02_TIMID_CITIZEN("弱気な市民", "丁寧、懇願、震え声。必死さが伝わる内容。"),
    T_03_ROUGH_OUTLAW("粗野な無法者", "乱暴、高圧的、スラング多用。利益を強調する。"),
    T_04_CALCULATING_INFORMANT("計算高い情報屋", "冷静、慇懃無礼、含みのある言い回し。");

    private final String displayName;
    private final String description;

    QuestPersona(String displayName, String description) {
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
