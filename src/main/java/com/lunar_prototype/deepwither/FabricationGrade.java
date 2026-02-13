package com.lunar_prototype.deepwither;

public enum FabricationGrade {
    STANDARD(1, "Standard", "§7", 1.0),       // 標準 (100%)
    INDUSTRIAL(2, "Industrial", "§8", 1.2),   // 工業 (120%)
    MILITARY(3, "Military", "§2", 1.5),       // 軍用 (150%)
    ADVANCED(4, "Advanced", "§b", 2.0),       // 先進 (200%)
    AETHERBOUND(5, "Aetherbound", "§5", 3.0); // エーテル適合 (300%)

    private final int id;
    private final String codeName;
    private final String color;
    private final double multiplier;

    FabricationGrade(int id, String name, String color, double multiplier) {
        this.id = id;
        this.codeName = name;
        this.color = color;
        this.multiplier = multiplier;
    }

    public int getId() { return id; }

    public String getDisplayName() {
        return color + "FG-" + id + " : " + codeName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public static FabricationGrade fromId(int id) {
        for (FabricationGrade g : values()) {
            if (g.id == id) return g;
        }
        return STANDARD;
    }
}