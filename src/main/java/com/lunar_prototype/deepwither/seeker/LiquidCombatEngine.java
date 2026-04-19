package com.lunar_prototype.deepwither.seeker;

import com.lunar_prototype.deepwither.api.DW;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;

public class LiquidCombatEngine {

    private static final String[] ACTIONS = {"ATTACK", "EVADE", "BAITING", "COUNTER", "OBSERVE", "RETREAT", "BURST_DASH", "ORBITAL_SLIDE"};

    /**
     * ネイティブのQ学習エンジンで周囲状況を評価し、移動・行動戦略を含むBanditDecisionを生成する。
     *
     * 内部で戦術優位の更新、移動に関する報酬付与、入力ベクトルの構築、状態の量子化を行い、
     * ネイティブ脳（brain.cycle）による行動選択を反映した決定を返す。
     *
     * @param version     エンジンのバージョン識別文字列（決定メタデータに使用される）
     * @param context     環境情報およびエンティティ状態を含むコンテキスト
     * @param brain       エージェントの学習・戦術情報を保持するLiquidBrainインスタンス（状態更新と学習サイクルに使用）
     * @param bukkitEntity 実ゲーム上のMobオブジェクト（ターゲティングや空間情報の取得に使用）
     * @return            行動種別・移動計画・通信情報・推論メタデータを含むBanditDecision
     */
    public BanditDecision think(String version, BanditContext context, LiquidBrain brain, Mob bukkitEntity) {
        BanditDecision d = new BanditDecision();
        d.engine_version = "v5.0-Native-Rebirth";
        d.decision = new BanditDecision.DecisionCore();
        d.movement = new BanditDecision.MovementPlan();
        d.communication = new BanditDecision.Communication();

        // 1. 環境認識とJava側での戦術評価
        List<BanditContext.EnemyInfo> enemies = context.environment.nearby_enemies;
        float enemyDist = 20.0f;
        Player targetPlayer = null;

        if (!enemies.isEmpty()) {
            BanditContext.EnemyInfo nearest = enemies.stream()
                    .min(Comparator.comparingDouble(e -> e.dist))
                    .orElse(enemies.get(0));
            enemyDist = (float) nearest.dist;
            if (nearest.playerInstance instanceof Player p) {
                targetPlayer = p;
                if (bukkitEntity.getTarget() != p) bukkitEntity.setTarget(p);
            }
        }

        updateTacticalAdvantage(bukkitEntity, brain, brain.tacticalMemory);
        float advantage = (float) brain.tacticalMemory.combatAdvantage;

        // 2. 報酬の計算
        applyMobilityRewards(bukkitEntity, brain, d, (double) enemyDist);

        // 3. 入力ベクトルの作成 (5次元)
        boolean isRecovering = (brain.selfPattern.averageInterval > 0 && (bukkitEntity.getTicksLived() - brain.selfPattern.lastAttackTick) < 15);
        float[] inputs = new float[5];
        inputs[0] = advantage;
        inputs[1] = enemyDist;
        inputs[2] = (float) context.entity.hp_pct / 100.0f;
        inputs[3] = isRecovering ? 1.0f : 0.0f;
        inputs[4] = Math.min(enemies.size(), 5);

        // 敵の攻撃予測を更新
        if (targetPlayer != null) {
            brain.updateEnemyPrediction(targetPlayer.getUniqueId(), (double) enemyDist);
        }

        // [TQH-Bootstrap] 現在の状態を量子化
        int stateId = packState(advantage, enemyDist, inputs[2], isRecovering, enemies.size());
        brain.setCondition(stateId);

        // 4. Native Q-Engine による思考サイクル
        int actionIdx = brain.cycle(inputs);
        String actionName = ACTIONS[actionIdx];

        // 5. 決定の反映
        d.decision.action_type = actionName;

        switch (actionName) {
            case "ATTACK" -> d.movement.destination = "ENEMY";
            case "EVADE" -> d.movement.strategy = "SIDESTEP";
            case "RETREAT" -> d.movement.destination = "NEAREST_COVER";
            case "BAITING" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            case "COUNTER" -> d.movement.strategy = "BACKSTEP"; // Actuator の strategy 名に合わせる
            case "BURST_DASH" -> d.movement.strategy = "BURST_DASH";
            case "ORBITAL_SLIDE" -> d.movement.strategy = "ORBITAL_SLIDE";
            case "OBSERVE" -> d.movement.strategy = "MAINTAIN_DISTANCE";
            default -> d.movement.strategy = "MAINTAIN_DISTANCE";
        }

        // 6. メタデータの付与
        d.reasoning = String.format("A:%s | S:%d | T:%.2f | F:%.2f | Adv:%.2f", 
                actionName, stateId, brain.systemTemperature, brain.frustration, advantage);

        brain.recordSnapshot(d.movement.strategy);

        return d;
    }

    /**
     * 高レベルの戦闘状態を9ビットの整数へ量子化して返す。
     *
     * 各ビットの割り当て:
     * - ビット8-7: 戦術的優位 (`advantage`) を 0/1/2 に分類 (>0.6 → 2, >0.4 → 1, それ以外 → 0)
     * - ビット6-5: 敵までの距離 (`dist`) を 3m未満→0、3–7m未満→1、7m以上→2 に分類
     * - ビット4-3: 体力割合 (`hp`) を <0.3→0、<0.7→1、それ以外→2 に分類
     * - ビット2  : 回復中フラグ (`recovering`)（true→1、false→0）
     * - ビット1-0: 敵数 (`enemyCount`) の下位2ビット（最大3にクリップ）
     *
     * @param advantage 戦術的優位のスコア（0.0〜1.0 を想定）。閾値は 0.4 / 0.6。
     * @param dist      自分とターゲット間の距離（メートル）。
     * @param hp        現在のHP割合（0.0〜1.0 を想定）。
     * @param recovering 回復状態であれば true。
     * @param enemyCount 周囲の敵数（4以上は3として扱われる）。
     * @return 量子化された状態ID（0〜0x1FF の9ビット整数）。
     */
    private int packState(double advantage, double dist, float hp, boolean recovering, int enemyCount) {
        int bits = 0;
        bits |= (advantage > 0.6 ? 2 : (advantage > 0.4 ? 1 : 0)) << 7;
        bits |= (dist < 3.0 ? 0 : (dist < 7.0 ? 1 : 2)) << 5;
        bits |= (hp < 0.3 ? 0 : (hp < 0.7 ? 1 : 2)) << 3;
        bits |= (recovering ? 1 : 0) << 2;
        bits |= (Math.min(enemyCount, 3));
        return bits & 0x1FF;
    }

    /**
     * エンティティの位置関係と距離に基づいて移動関連の報酬を蓄積する。
     *
     * 指定した Mob の現在ターゲットがプレイヤーであり、最後の行動が存在する場合にのみ動作する。
     * ターゲットの向きとの相対角度（ドット積）と敵との距離に応じて小さな報酬を計算し、
     * 正の報酬がある場合は brain.accumulatedReward に加算する。
     *
     * @param bukkitEntity 参照対象の Mob（そのターゲットの位置・向きを使用する）
     * @param brain        報酬の蓄積先および状態（brain.lastActionIdx を確認し、brain.accumulatedReward を更新する）
     * @param d            現在の決定オブジェクト（このメソッド内では参照のみ、説明不要な場合は無視可）
     * @param currentDist  ターゲットとの現在の距離（メートル単位）
     */
    private void applyMobilityRewards(Mob bukkitEntity, LiquidBrain brain, BanditDecision d, double currentDist) {
        if (brain.lastActionIdx < 0) return;

        Player target = (Player) bukkitEntity.getTarget();
        if (target == null) return;

        float totalProcessReward = 0.0f;
        float correlationFactor = brain.composure;

        Vector toSelf = bukkitEntity.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetFacing = target.getLocation().getDirection();
        float dot = (float) toSelf.dot(targetFacing);

        if (dot < -0.3f) {
            totalProcessReward += 0.1f + (0.3f * correlationFactor);
        } else if (dot < 0.2f) {
            totalProcessReward += 0.05f + (0.1f * correlationFactor);
        }

        if (currentDist > 3.0 && currentDist < 5.0) {
            totalProcessReward += 0.05f;
        }

        if (totalProcessReward > 0) {
            brain.accumulatedReward += totalProcessReward;
        }
    }

    /**
     * 敵との現在の交戦状態に基づいて戦術的優位（tacticalMemory.combatAdvantage）を更新し、
     * 対象への注視度（brain.attentionLevel）を設定する。
     *
     * <p>振る舞い:
     * - 自身にターゲットがいない場合、combatAdvantage を 0.9 倍に減衰させて終了する。
     * - ターゲットが存在する場合、ターゲットの向きと自身の相対位置から attentionLevel を算出し設定する。
     * - 現状スナップショットを（HP 比率・空間的自由度・連続被撃状態の重み付き和）として算出し、
     *   glia 活性に応じた学習率で既存の combatAdvantage とブレンドして更新する。
     *
     * @param self            更新対象となる Mob（自身）
     * @param brain           エージェントの内部状態とセンサ情報を保持する LiquidBrain
     * @param tacticalMemory  更新対象の戦術記憶（combatAdvantage を変更する）
     */
    public void updateTacticalAdvantage(Mob self, LiquidBrain brain, LiquidBrain.TacticalMemory tacticalMemory) {

        if (!(self.getTarget() instanceof Player target)) {
            tacticalMemory.combatAdvantage *= 0.9;
            return;
        }

        Vector toSelf = self.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        brain.attentionLevel = (float) target.getLocation().getDirection().dot(toSelf);

        float gliaStress = brain.getGliaActivity(); 
        double spatialFreedom = 1.0 - gliaStress;

        double myHpPct = DW.stats().getMobHealth(self) / DW.stats().getMobMaxHealth(self);
        boolean beingComboed = (System.currentTimeMillis() - brain.tacticalMemory.lastHitTime < 500);

        double wHp = (myHpPct < 0.4) ? 0.5 : 0.2;
        double wSpatial = 0.3;
        double wCombo = beingComboed ? -0.4 : 0.0;

        double currentSnapshot = (myHpPct * wHp) + (spatialFreedom * wSpatial) + wCombo;

        float learningRate = (gliaStress > 0.5f) ? 0.5f : 0.2f;
        tacticalMemory.combatAdvantage = (tacticalMemory.combatAdvantage * (1.0 - learningRate)) + (currentSnapshot * learningRate);
    }
}