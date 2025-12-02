package com.lunar_prototype.deepwither.quest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

/**
 * クエスト発生場所の詳細情報（名前、座標、階層）を保持するクラス。
 */
@SerializableAs("LocationDetails")
public class LocationDetails implements ConfigurationSerializable {
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final String hierarchy; // 階層情報 (例: "地上", "地下1階", "天空")

    public LocationDetails(String name, double x, double y, double z, String hierarchy) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hierarchy = hierarchy;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getHierarchy() {
        return hierarchy;
    }

    public Location toBukkitLocation() {
        // ワールド名はLocationDetailsに持たせる必要があります
        // 仮にワールド名を "world" とします。実際にはConfigからロードするか、クエストデータに含めるべきです。
        World world = Bukkit.getWorld("aether");
        if (world == null) {
            // エラー処理
            return null;
        }
        return new Location(world, x, y, z);
    }

    /**
     * YAMLに保存するためのデータをMapとして提供します。
     */
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", this.name);
        map.put("x", this.x);
        map.put("y", this.y);
        map.put("z", this.z);
        map.put("hierarchy", this.hierarchy);
        return map;
    }

    /**
     * YAMLからデータをロードするための静的ファクトリメソッドです。（数値の安全な変換を適用）
     */
    public static LocationDetails deserialize(Map<String, Object> map) {

        // 1. 文字列の取得
        String name = (String) map.getOrDefault("name", "Unknown Location");
        String hierarchy = (String) map.getOrDefault("hierarchy", "Unknown Hierarchy");

        // 2. 数値の安全な取得と変換 (Number.doubleValue()を使用)
        double x = getDoubleFromMap(map, "x");
        double y = getDoubleFromMap(map, "y");
        double z = getDoubleFromMap(map, "z");

        return new LocationDetails(name, x, y, z, hierarchy);
    }

    /**
     * Mapからキーに対応する値を安全にdoubleとして取得するヘルパーメソッド。
     * Integer/Long/Doubleのいずれにも対応します。
     */
    private static double getDoubleFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            // Number型であれば、doubleValue()を使って安全にdoubleに変換する
            return ((Number) value).doubleValue();
        }
        // エラーが発生した場合や値が存在しない場合のフォールバック
        return 0.0;
    }

    /**
     * LLMプロンプトに含めるためのロケーションテキスト（階層情報を含む）を生成します。
     */
    public String getLlmLocationText() {
        return String.format("%s (%s)", name, hierarchy);
    }
}