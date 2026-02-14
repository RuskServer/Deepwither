package com.lunar_prototype.deepwither.dynamic_quest.dialogue;

import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestPersona;
import com.lunar_prototype.deepwither.dynamic_quest.enums.QuestType;
import org.bukkit.Location;

import java.util.Random;

public class DialogueGenerator {

    private static final Random random = new Random();

    public String generate(QuestPersona persona, QuestType type, Location target) {
        String greeting = getGreeting(persona);
        String reason = getReason(persona, type);
        String destination = getDestination(persona, target);
        String prompt = getPrompt(persona);
        String reward = getReward(persona);

        return String.format("%s %s %s %s %s", greeting, reason, destination, prompt, reward);
    }

    private String getGreeting(QuestPersona persona) {
        switch (persona) {
            case T_01_VETERAN:
                return pick("貴様、動けるか？", "おい、そこの傭兵。", "状況報告。", "時間がない、聞け。");
            case T_02_TIMID_CITIZEN:
                return pick("あ、あの...すみません...", "助けてください...", "ど、どうしよう...", "お、お願いします...");
            case T_03_ROUGH_OUTLAW:
                return pick("おい、そこのマヌケ面！", "よう、稼ぎたいか？", "ヒヒッ、いい獲物が来たな。", "何見てんだ？仕事だ。");
            case T_04_CALCULATING_INFORMANT:
                return pick("ごきげんよう。", "少しお耳を拝借願えますか？", "良い取引がありますよ。", "ふふ、ちょうど良いところに。");
            default:
                return "よう。";
        }
    }

    private String getReason(QuestPersona persona, QuestType type) {
        // Just a simple implementation for now, expandable later
        switch (type) {
            case RAID:
                switch (persona) {
                    case T_01_VETERAN: return "敵の補給線を確認した。";
                    case T_02_TIMID_CITIZEN: return "あそこの悪い人たちが...私の荷物を奪って...";
                    case T_03_ROUGH_OUTLAW: return "軍の野郎どもがヌクヌクと物資を運びやがってる。";
                    case T_04_CALCULATING_INFORMANT: return "ある組織が非公式に物資を輸送しています。";
                }
            case FETCH:
                 switch (persona) {
                    case T_01_VETERAN: return "至急、医療物資が必要だ。";
                    case T_02_TIMID_CITIZEN: return "家族のために食料が必要なんです...";
                    case T_03_ROUGH_OUTLAW: return "極上の酒が欲しいんだがよ。";
                    case T_04_CALCULATING_INFORMANT: return "ある遺物が必要です。";
                }
            default:
                return "頼みたいことがある。";
        }
    }

    private String getDestination(QuestPersona persona, Location loc) {
        String coords = loc != null ? String.format("(%d, %d)", loc.getBlockX(), loc.getBlockZ()) : "あそこ";
        switch (persona) {
            case T_01_VETERAN: return "座標 " + coords + " へ急行しろ。";
            case T_02_TIMID_CITIZEN: return "場所は " + coords + " なんです...";
            case T_03_ROUGH_OUTLAW: return "あそこのルートAを通る連中のケツを蹴り上げて、中身をぶんどってこい！"; // Matching spec example
            case T_04_CALCULATING_INFORMANT: return "場所は地図に記しておきました " + coords + " です。";
            default: return "場所は " + coords + " だ。";
        }
    }

    private String getPrompt(QuestPersona persona) {
        switch (persona) {
            case T_01_VETERAN: return "直ちに行動を開始せよ。";
            case T_02_TIMID_CITIZEN: return "お願いです、助けてください！";
            case T_03_ROUGH_OUTLAW: return "派手に暴れてこいよ。";
            case T_04_CALCULATING_INFORMANT: return "失敗は許されませんよ？";
            default: return "頼んだぞ。";
        }
    }

    private String getReward(QuestPersona persona) {
        switch (persona) {
            case T_01_VETERAN: return "報酬は規定通り支払う。";
            case T_02_TIMID_CITIZEN: return "なけなしのお金ですが...差し上げます。";
            case T_03_ROUGH_OUTLAW: return "分け前はたっぷりやるぜ、ハハハ！";
            case T_04_CALCULATING_INFORMANT: return "情報は高く買いますからね。";
            default: return "礼はする。";
        }
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }
}
