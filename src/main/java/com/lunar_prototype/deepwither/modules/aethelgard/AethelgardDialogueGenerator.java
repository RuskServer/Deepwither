package com.lunar_prototype.deepwither.modules.aethelgard;

import java.util.Random;

public class AethelgardDialogueGenerator {
    private static final Random random = new Random();

    public record GeneratedText(String title, String body) {}

    public GeneratedText generate(AethelgardQuestPersona persona, String targetMobDescription, LocationDetails location, String motivation, int quantity, String rewardText) {
        String title = generateTitle(targetMobDescription, location, motivation);
        String body = generateBody(persona, targetMobDescription, location, motivation, quantity, rewardText);
        return new GeneratedText(title, body);
    }

    private String generateTitle(String targetMobDescription, LocationDetails location, String motivation) {
        String[] patterns = {
            "%sにおける%sの排除要請",
            "%s周辺の%s討伐任務",
            "【重要】%sでの%s掃討作戦",
            "%sに潜む%sの調査と排除",
            "緊急依頼：%sの%sを撃破せよ"
        };
        String pattern = pick(patterns);
        return String.format(pattern, location.getName(), targetMobDescription);
    }

    private String generateBody(AethelgardQuestPersona persona, String targetMobDescription, LocationDetails location, String motivation, int quantity, String rewardText) {
        String greeting = getGreeting(persona);
        String context = getContext(persona, motivation);
        String objective = getObjective(persona, targetMobDescription, location, quantity);
        String closing = getClosing(persona);

        return String.format("%s %s %s %s 報酬として、%s を約束する。", greeting, context, objective, closing, rewardText);
    }

    private String getGreeting(AethelgardQuestPersona persona) {
        return switch (persona) {
            case GUILD_OFFICIAL -> pick("ギルドより通達する。", "新たな依頼が受理された。", "冒険者よ、これを確認しろ。");
            case VETERAN_MERCENARY -> pick("よお、仕事を探してるのか？", "戦い方を忘れてなきゃいいがな。", "俺たちの仲間を助けてやってくれ。");
            case WORRIED_CITIZEN -> pick("あ、あの、お願いがあります...", "助けてください！", "もう、どうすればいいか分からなくて...");
            case MYSTERIOUS_PATRON -> pick("ふふふ、君なら受けてくれると思っていたよ。", "少しばかり耳を貸してくれないか？", "君にふさわしい「取引」があるのだがね。");
        };
    }

    private String getContext(AethelgardQuestPersona persona, String motivation) {
        return motivation + "。放っておけば被害は拡大する一方だ。";
    }

    private String getObjective(AethelgardQuestPersona persona, String targetMobDescription, LocationDetails location, int quantity) {
        String locName = location.getName();
        return switch (persona) {
            case GUILD_OFFICIAL -> String.format("%s付近に出没する%sを%d体排除せよ。速やかな遂行を期待する。", locName, targetMobDescription, quantity);
            case VETERAN_MERCENARY -> String.format("%sにいる%sを%d体ほどブチのめしてこい。簡単な仕事だろ？", locName, targetMobDescription, quantity);
            case WORRIED_CITIZEN -> String.format("%sに%sが%d体もいて、怖くて近寄れないんです。どうか追い払ってください...", locName, targetMobDescription, quantity);
            case MYSTERIOUS_PATRON -> String.format("%sに蠢く%sを%d体、消し去ってほしいのだよ。君の「実力」を見せておくれ。", locName, targetMobDescription, quantity);
        };
    }

    private String getClosing(AethelgardQuestPersona persona) {
        return switch (persona) {
            case GUILD_OFFICIAL -> "以上だ。";
            case VETERAN_MERCENARY -> "期待してるぜ。死ぬんじゃねえぞ。";
            case WORRIED_CITIZEN -> "どうか、よろしくお願いします！";
            case MYSTERIOUS_PATRON -> "良い返事を待っているよ。ふふふ。";
        };
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }
}
