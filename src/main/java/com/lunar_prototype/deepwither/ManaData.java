package com.lunar_prototype.deepwither;

public class ManaData {
    private double currentMana;
    private double maxMana;

    public ManaData(double maxMana) {
        this.maxMana = maxMana;
        this.currentMana = maxMana;
    }

    public double getCurrentMana() { return currentMana; }
    public double getMaxMana() { return maxMana; }

    public void setCurrentMana(double mana) {
        this.currentMana = Math.min(maxMana, Math.max(0, mana));
    }

    public void changeMana(double delta) {
        setCurrentMana(currentMana + delta);
    }

    public void setMaxMana(double maxMana) {
        this.maxMana = maxMana;
        this.currentMana = Math.min(this.currentMana, maxMana);
    }

    public boolean consume(double cost) {
        if (currentMana >= cost) {
            currentMana -= cost;
            return true;
        }
        return false;
    }

    public void regen(double amount) {
        setCurrentMana(currentMana + amount);
    }
}

