package com.lunar_prototype.deepwither.dungeon;

public class DungeonPart {
    private final String fileName;
    private final String type;
    private final int length; // 次のパーツまでの距離

    public DungeonPart(String fileName, String type, int length) {
        this.fileName = fileName;
        this.type = type;
        this.length = length;
    }
    public String getFileName() { return fileName; }
    public String getType() { return type; }
    public int getLength() { return length; }
}
