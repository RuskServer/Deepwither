package com.lunar_prototype.deepwither.seeker;

import org.bukkit.util.Vector;

public class BanditDecision {
    public String engine_version = "v1.0";

    public DecisionCore decision;
    public MovementPlan movement;
    public Communication communication;
    public String reasoning;

    public static class DecisionCore {
        public String action_type;
        public String target_priority;
        public String use_skill;
        public String new_stance;
    }

    public static class MovementPlan {
        public String strategy;
        public String destination; // "NEAREST_COVER", "ENEMY", "NONE"
        public Vector jitter_vector = new Vector(0, 0, 0); // これを追加
    }

    public static class Communication {
        public String voice_line;
        public String shout_to_allies;
    }

    public enum TacticalRole {
        TANKER,   // 挑発(BAITING)でタゲを引き、耐える
        STRIKER,  // 背後や死角から高火力スキル・ダッシュを叩き込む
        HARASSER, // つかず離れずの距離で嫌がらせ(ORBITAL_SLIDE)をして注意を散らす
        SOLO      // 1v1状態
    }
}
