package com.lunar_prototype.deepwither.seeker;

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
    }

    public static class Communication {
        public String voice_line;
        public String shout_to_allies;
    }
}
