package com.lunar_prototype.deepwither.seeker.v2;

import java.util.Map;

public class BehaviorProfile {
    public String profile_id;      // 例: "spear_expert", "kiting_punisher"
    public int tier;               // 推奨レベル

    // ニューロンごとのベースバイアス (初期値への加算)
    public Map<String, Double> neuron_biases;

    // 状況に応じた反応係数 (例: "kiting" を検知した時の aggression 上昇率)
    public Map<String, Double> situational_weights;

    // 攻撃リズムの初期知識 (V2 攻撃頻度予測の初期値)
    public double default_attack_interval;
    public double default_attack_dist;
}