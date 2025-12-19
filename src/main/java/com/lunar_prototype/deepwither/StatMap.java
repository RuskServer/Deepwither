package com.lunar_prototype.deepwither;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StatMap {
    private final Map<StatType, Double> flatValues = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> percentValues = new EnumMap<>(StatType.class);

    public void setFlat(StatType type, double value) {
        flatValues.put(type, round(value));
    }

    public void setPercent(StatType type, double value) {
        percentValues.put(type, round(value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public double getFlat(StatType type) {
        return flatValues.getOrDefault(type, 0.0);
    }

    public double getPercent(StatType type) {
        return percentValues.getOrDefault(type, 0.0);
    }

    public double getFinal(StatType type) {
        double flat = getFlat(type);
        double percent = getPercent(type);
        return Math.round((flat * (1 + percent / 100.0)) * 100.0) / 100.0;
    }

    public void add(StatMap other) {
        for (StatType type : other.getAllTypes()) {
            double flat = this.getFlat(type) + other.getFlat(type);
            double percent = this.getPercent(type) + other.getPercent(type);
            this.setFlat(type, flat);
            this.setPercent(type, percent);
        }
    }

    public Set<StatType> getAllTypes() {
        Set<StatType> types = new HashSet<>();
        types.addAll(flatValues.keySet());
        types.addAll(percentValues.keySet());
        return types;
    }

    /**
     * このStatMapに含まれる全てのStatTypeのFlat値とPercent値を指定された乗数で更新します。
     * * @param multiplier 乗数 (例: 1.10 for +10% boost)
     */
    public void multiplyAll(double multiplier) {
        for (StatType type : getAllTypes()) {
            double currentFlat = getFlat(type);
            double currentPercent = getPercent(type);

            // Flat値を更新
            setFlat(type, currentFlat * multiplier);

            // Percent値を更新
            setPercent(type, currentPercent * multiplier);
        }
    }
}
