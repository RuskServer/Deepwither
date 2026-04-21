package com.lunar_prototype.deepwither;

public enum FabricationGrade {
    STANDARD(1, "Standard"); // 等級システム廃止のため STANDARD のみ存在

    private final int id;
    private final String codeName;

    FabricationGrade(int id, String name) {
        this.id = id;
        this.codeName = name;
    }

    public int getId() { return id; }

    /** 等級表示は廃止済み。空文字を返す。 */
    public String getDisplayName() {
        return "";
    }

    /** 等級システム廃止のため常に 1.0 を返す。 */
    public double getMultiplier() {
        return 1.0;
    }

    /** 等級システム廃止のため常に STANDARD を返す。 */
    public static FabricationGrade fromId(int id) {
        return STANDARD;
    }
}