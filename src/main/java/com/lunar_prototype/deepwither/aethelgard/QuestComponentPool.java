package com.lunar_prototype.deepwither.aethelgard;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class QuestComponentPool {

    private static final Random RANDOM = new Random();

    private static List<LocationDetails> LOCATIONS = new ArrayList<>();
    private static List<String> MOTIVATIONS = new ArrayList<>();
    private static List<String> REWARD_ITEM_IDS = new ArrayList<>();

    // -------------------------------------------------------------------------
    // 設定ファイルからのデータロード（メインクラスから一度だけ呼び出す）
    // -------------------------------------------------------------------------
    /**
     * YAML設定セクションからクエストコンポーネントデータをロードします。
     * @param section guild_quest_config.yml の 'quest_components' セクション
     */
    public static void loadComponents(ConfigurationSection section) {
        if (section == null) {
            System.err.println("[QuestPool Error] 'quest_components' セクションが見つかりません。デフォルト値が使用されます。");
            return;
        }

        // 1. LOCATIONSのロードとデバッグ
        List<LocationDetails> loadedLocations = new ArrayList<>();
        List<?> rawLocations = section.getList("locations");

        if (rawLocations != null) {
            System.out.println("[QuestPool Debug] ロード対象の生ロケーション数: " + rawLocations.size());

            for (int i = 0; i < rawLocations.size(); i++) {
                Object rawData = rawLocations.get(i);

                // A. Bukkitがデシリアライズに成功した場合
                if (rawData instanceof LocationDetails) {
                    loadedLocations.add((LocationDetails) rawData);
                    System.out.println("[QuestPool Debug] " + i + ": LocationDetailsとしてロード成功。");
                }
                // B. Bukkitがデシリアライズに失敗し、Mapとして残った場合
                else if (rawData instanceof Map) {
                    Map<String, Object> mapData = (Map<String, Object>) rawData;
                    System.err.println("[QuestPool CRITICAL DEBUG] " + i + ": LocationDetailsとしてロード失敗。生のMapデータ: " + mapData);

                    // C. デシリアライズ失敗の原因を探るため、手動で deserialize を試行
                    try {
                        LocationDetails manualLoad = LocationDetails.deserialize(mapData);
                        if (manualLoad != null) {
                            loadedLocations.add(manualLoad);
                            // 本来はここまで到達しないはずだが、もし成功したら追加
                            System.err.println("[QuestPool CRITICAL DEBUG] 手動デシリアライズに成功しました。これはConfigurationSerializationの登録ミスを示唆しています。");
                        }
                    } catch (ClassCastException e) {
                        // 最も可能性の高いエラー: Integer/Double キャスト失敗
                        System.err.println("[QuestPool CRITICAL ERROR] 手動デシリアライズ中に ClassCastException が発生しました！");
                        System.err.println("エラーメッセージ: " + e.getMessage());
                        e.printStackTrace(); // スタックトレースを出力
                    } catch (Exception e) {
                        System.err.println("[QuestPool CRITICAL ERROR] その他のデシリアライズエラーが発生しました。");
                        e.printStackTrace();
                    }
                } else if (rawData != null) {
                    System.err.println("[QuestPool CRITICAL DEBUG] " + i + ": 不明なデータ型 (" + rawData.getClass().getSimpleName() + ")。スキップします。");
                }
            }
        }

        LOCATIONS = Collections.unmodifiableList(loadedLocations);

        // 2. MOTIVATIONSのロード
        MOTIVATIONS = section.getStringList("motivations");
        if (MOTIVATIONS.isEmpty()) {
            System.err.println("[QuestPool Warning] motivationsの設定が空です。");
        }

        // 3. REWARD_ITEM_IDSのロード
        REWARD_ITEM_IDS = section.getStringList("reward_item_ids");
        if (REWARD_ITEM_IDS.isEmpty()) {
            System.err.println("[QuestPool Warning] reward_item_idsの設定が空です。");
        }

        System.out.println(String.format("[QuestPool Info] クエストコンポーネントをロードしました: 場所:%d, 動機:%d, 報酬:%d",
                LOCATIONS.size(), MOTIVATIONS.size(), REWARD_ITEM_IDS.size()));
    }


    /**
     * 読み込み済みのロケーション一覧を返します。
     */
    public static List<LocationDetails> getAllLocationDetails() {
        return LOCATIONS;
    }

    /**
     * ランダムなLocationDetailsオブジェクトを取得します。
     */
    public static LocationDetails getRandomLocationDetails() {
        return LOCATIONS.get(RANDOM.nextInt(LOCATIONS.size()));
    }

    /**
     * ランダムな動機を取得します。
     */
    public static String getRandomMotivation() {
        return MOTIVATIONS.get(RANDOM.nextInt(MOTIVATIONS.size()));
    }

    /**
     * 難易度に基づいて討伐数を計算します。
     */
    public static int calculateRandomQuantity(int difficultyLevel) {
        // 難易度に応じて 15〜50 の間でランダムな値を返す
        return 10 + (difficultyLevel * 5) + RANDOM.nextInt(36);
    }

    /**
     * ランダムな報酬アイテムIDを取得します。
     */
    public static String getRandomRewardItemId() {
        return REWARD_ITEM_IDS.get(RANDOM.nextInt(REWARD_ITEM_IDS.size()));
    }

    /**
     * 難易度に基づいて報酬通貨と経験値の基本値を計算します。
     * @param difficultyLevel クエストの難易度
     * @return 報酬の通貨と経験値の基本値を含むRewardValue
     */
    public static RewardValue calculateBaseCurrencyAndExp(int difficultyLevel) {
        // 1. 通貨 (ギルドコイン): 難易度に応じて増加 + ランダムボーナス
        int baseCoin = 50 + difficultyLevel * 100;
        int coin = baseCoin + RANDOM.nextInt(50); // 50未満のランダムボーナス

        // 2. 経験値: 難易度に応じて大幅に増加
        int baseExp = 200 + difficultyLevel * 300;
        int exp = baseExp + RANDOM.nextInt(100); // 100未満のランダムボーナス

        return new RewardValue(coin, exp);
    }

    /**
     * ランダムなアイテム個数を決定します。
     */
    public static int getRandomItemQuantity(String itemId, int difficultyLevel) {
        if (itemId.contains("POTION") || itemId.contains("FOOD")) {
            // ポーションや食料は少なめ (1〜3個)
            return 1 + RANDOM.nextInt(3);
        } else if (itemId.contains("SHARD")) {
            // 素材系は多め (3〜6個 + 難易度ボーナス)
            return 3 + RANDOM.nextInt(4) + (difficultyLevel / 2);
        } else {
            // その他のアイテム (1個)
            return 1;
        }
    }

    /**
     * 通貨と経験値を一時的に保持する内部クラス
     */
    public static class RewardValue {
        public final int coin;
        public final int exp;

        public RewardValue(int coin, int exp) {
            this.coin = coin;
            this.exp = exp;
        }
    }
}