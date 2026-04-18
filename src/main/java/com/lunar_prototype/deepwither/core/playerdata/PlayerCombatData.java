package com.lunar_prototype.deepwither.core.playerdata;

import com.lunar_prototype.deepwither.api.playerdata.IPlayerComponent;

public class PlayerCombatData implements IPlayerComponent {
    private int critCounter = 1; // PRD クリティカルカウンタ (デフォルト1)

    public int getCritCounter() {
        return critCounter;
    }

    public void setCritCounter(int critCounter) {
        this.critCounter = critCounter;
    }

    @Override
    public String toString() {
        return "PlayerCombatData{critCounter=" + critCounter + "}";
    }
}
