package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // 追加
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;

public class DamageManager implements Listener {

    private final Set<UUID> isProcessingDamage = new HashSet<>();
    private final StatManager statManager; // StatManagerへの参照を追加
    private final Map<UUID, Long> onHitCooldowns = new HashMap<>();

    public DamageManager(StatManager statManager) {
        this.statManager = statManager;
    }

    private static final Set<String> UNDEAD_MOB_IDS = new HashSet<>(Arrays.asList(
            "melee_skeleton",
            "ranged_skeleton"
    ));

    // ----------------------------------------------------
    // --- A. Mythic Mobs 魔法ダメージ処理 ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onMythicDamage(MythicDamageEvent e){
        if (!(e.getCaster().getEntity().getBukkitEntity() instanceof Player player)) return;
        if (!(e.getTarget().getBukkitEntity() instanceof LivingEntity targetLiving)) return;

        boolean hasLifestealTag = e.getDamageMetadata().getTags().contains("LIFESTEAL");
        boolean hasUndeadTag = e.getDamageMetadata().getTags().contains("UNDEAD");

        // ------------------------------------------------------------------
        // ★追加: UNDEAD 特攻ダメージ処理 (確定で80%HPを削る)
        // ------------------------------------------------------------------
        if (hasUndeadTag) {

            // 1. Mythic MobsのAPIを使用してターゲットのMob IDを取得
            String mobId = MythicBukkit.inst().getMobManager().getActiveMob(targetLiving.getUniqueId()).get().getMobType();

            // 2. Mob IDを事前に定義したアンデッドリストと照合
            boolean isTargetUndead = (mobId != null && UNDEAD_MOB_IDS.contains(mobId));

            if (isTargetUndead) {
                // 敵の現在HPの80%を確定で削る
                double damageAmount = targetLiving.getHealth() * 0.5;

                player.sendMessage("§5§l聖特攻！ §fアンデッドに対して§5§l50%の確定ダメージ！");
                targetLiving.getWorld().playSound(targetLiving.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);

                // MythicDamageEventをキャンセルし、このダメージのみをカスタムで適用して終了
                e.setDamage(0.0);
                e.setCancelled(true);

                // カスタムダメージシステムに直接ダメージを適用し、処理を終了
                applyCustomDamage(targetLiving, damageAmount, player);

                return; // 後続の魔法ダメージ計算、Lifesteal、PvPチェックを全てスキップ
            }
        }

        // ------------------------------------------------------------------
        // 3. LIFESTEAL 処理の適用
        // ------------------------------------------------------------------
        if (hasLifestealTag) {
            // LIFESTEALの回復量を敵の最大HPの割合で計算する
            double lifestealBaseDamage = e.getDamage(); // 回復のベースとなる元のスキルダメージ

            // 1. 敵の最大HPを取得
            double targetMaxHealth = targetLiving.getMaxHealth();

            // 2. 回復割合の計算
            // 例: lifestealBaseDamage (元のスキルダメージ) が 1000 の場合、割合は 10% (1000 / 10000 * 100)
            // ここでは、LIFESTEALのTagを持つスキルダメージ 100 = 敵の最大HPの 1% 回復、とする。
            final double RATIO_DIVISOR = 100.0; // スキルダメージを何で割ったら % にするか
            double healthRatio = lifestealBaseDamage / RATIO_DIVISOR; // 例: 10.0 -> 10%

            // 3. 最終的な回復量を計算 (敵の最大HP * 回復割合/100)
            double healAmount = targetMaxHealth * (healthRatio / 100.0);

            // 【任意】回復量に上限を設定 (例: プレイヤーの最大HPの20%を上限とする)
            double playerMaxHealth = player.getMaxHealth();
            final double MAX_HEAL_PERCENT = 0.20;
            healAmount = Math.min(healAmount, playerMaxHealth * MAX_HEAL_PERCENT);

            // プレイヤーのHPを回復
            double newHealth = Math.min(player.getHealth() + healAmount, playerMaxHealth);
            player.setHealth(newHealth);

            // 回復エフェクトとメッセージ
            player.sendMessage(String.format("§a§lライフスティール！ §2%.1f §aHPを回復しました。", healAmount));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        }

        // EntityDamageEvent.DamageCause.ENTITY_EXPLOSION の判定は、
        // Mythic Mobs のスキル設定（例えばカスタムダメージタイプ）と連動させる方が良い場合があります。
        // 例: e.getDamageMetadata().getString(MythicDamageMeta.DMG_TYPE_KEY).equals("MAGIC")
        if (e.getDamageMetadata().getDamageCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }

        StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
        StatMap defender = (targetLiving instanceof Player p)
                ? StatManager.getTotalStatsFromEquipment(p)
                : new StatMap();

        double magicAttack = attacker.getFinal(StatType.MAGIC_DAMAGE);
        double magicDefense = defender.getFinal(StatType.MAGIC_RESIST);
        double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
        double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);

        // 【TRPG 1d100形式】1から100までのランダムな目を生成
        int roll = (int)(Math.random() * 100) + 1;
        boolean isCrit = roll <= critChance;

        // ★修正点1: baseDamageの計算を、元のダメージに純粋な数値を加算する形に修正
        // e.getDamage() (MythicMobsのスキルダメージ) + magicAttack (装備等からの追加ダメージ)
        double baseDamage = e.getDamage() + magicAttack;

        if (isCrit) {
            baseDamage *= (critDamage / 100.0);
            player.sendMessage("§6§l魔法クリティカル！");
        }

        // ------------------------------------------------------------------
        // ★追加: BURST / AOE タグによるダメージ減衰
        // ------------------------------------------------------------------
        boolean isBurstApplied = e.getDamageMetadata().getTags().contains("BURST");
        boolean isAoeApplied = e.getDamageMetadata().getTags().contains("AOE");

        if (isBurstApplied) {
            // BURST: 魔法ダメージの40%を加える
            double magic_burst_damage = attacker.getFinal(StatType.MAGIC_BURST_DAMAGE);
            double tagBonus =  0.40;
            baseDamage *= tagBonus;
            baseDamage = baseDamage + magic_burst_damage;
        }

        if (isAoeApplied) {
            // AOE: 魔法ダメージの60%を加える
            // 注意: BURSTが適用されていた場合、その加算後の値に対して60%が適用される
            double tagBonus = 0.60;
            double magic_aoe_damage = attacker.getFinal(StatType.MAGIC_AOE_DAMAGE);
            baseDamage *= tagBonus;
            baseDamage = baseDamage + magic_aoe_damage;
        }

        double magicDefenseRatio = magicDefense / (magicDefense + 100.0);
        double finalMagicDamage = baseDamage * (1.0 - magicDefenseRatio);

        // ------------------------------------------------------------------
        // ★修正点2: PvP判定でのキャンセル処理と早期リターン
        // ------------------------------------------------------------------
        if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
            e.setDamage(0.0);
            e.setCancelled(true);
            player.sendMessage("§ePvP保護によりダメージは無効化されました。");
            return; // PvPがキャンセルされた場合はここで処理を終了
        }

        String damageTypeMessage;
        if (isAoeApplied) {
            // AOEタグがあれば最優先
            damageTypeMessage = "§b§l魔法AOEダメージ！";
        } else if (isBurstApplied) {
            // BURSTタグがあれば次
            damageTypeMessage = "§c§l魔法バーストダメージ！";
        } else {
            // どちらもなければ通常の魔法ダメージ
            damageTypeMessage = "§b§l魔法ダメージ！";
        }
        player.sendMessage(damageTypeMessage + " §c+" + Math.round(finalMagicDamage));

        // Mythic Mobsがダメージ処理をしないように、イベントを完全に上書きする
        e.setDamage(0.0);

        applyCustomDamage(targetLiving, finalMagicDamage, player);
    }

    // ----------------------------------------------------
    // --- B. 近接物理ダメージ処理 (Projectiveではないもの) ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH) // HIGH優先度で、バニラ処理より先にカスタムロジックを適用
    public void onMeleeDamage(EntityDamageByEntityEvent e) {
        // 1. ダメージ源がプレイヤーの近接攻撃であるかチェック (投射物/爆発ではないこと)
        if (!(e.getDamager() instanceof Player player)) return;
        if (e.getDamager() instanceof Projectile || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

        // 2. 処理中の再帰呼び出し防止
        if (isProcessingDamage.contains(player.getUniqueId())) return;

        // 3. ターゲットチェック
        if (!(e.getEntity() instanceof LivingEntity targetLiving)) return;

        // --- (既存のクリティカル判定とダメージ計算ロジック) ---
        StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
        StatMap defender = (targetLiving instanceof Player p)
                ? StatManager.getTotalStatsFromEquipment(p)
                : new StatMap();
        // ... (省略: 攻撃力、防御力、クリティカル計算) ...
        double attack = attacker.getFinal(StatType.ATTACK_DAMAGE);
        double defense = defender.getFinal(StatType.DEFENSE);
        double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
        double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);
        // 【TRPG 1d100形式】1から100までのランダムな目を生成
        int roll = (int)(Math.random() * 100) + 1;
        // 判定：出目がクリティカル率以下なら成功
        boolean isCrit = roll <= critChance;

        // ここで元のイベントをキャンセルし、独自にダメージを計算・適用
        e.setDamage(0.0);

        // クリティカルダメージ計算 (e.getDamage()は使わず、StatMapのATTACK_DAMAGEを基点とする)
        double baseDamage = attack;
        double defenseRatio = defense / (defense + 100.0);
        if (isCrit) {
            baseDamage *= (critDamage / 100.0);
        }
        double finalDamage = baseDamage * (1.0 - defenseRatio);
        finalDamage = Math.max(0.1, finalDamage);

        if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
            e.setDamage(0.0);
            e.setCancelled(true);
            return;
        }

        float attackCooldown = player.getAttackCooldown(); // 0.0 (クールダウン開始) から 1.0 (クールダウン完了)

        if (attackCooldown < 1.0f) {

            // 【前提】クールダウン適用前のダメージを preCooldownDamage に保持していると仮定
            double preCooldownDamage = finalDamage;

            // クールダウンが完了していない場合、ダメージに進行度を掛け合わせる
            // 例: クールダウン50%の場合、ダメージは半分になる
            finalDamage *= attackCooldown;

            // 減衰したダメージ量を計算
            double reducedDamage = preCooldownDamage - finalDamage;

            // 最低ダメージを保証
            finalDamage = Math.max(0.1, finalDamage);

            // ★修正点: 表示用の値を四捨五入して整数にする
            long displayReducedDamage = Math.round(reducedDamage);
            long displayFinalDamage = Math.round(finalDamage);

            // メッセージの整形と表示
            // 減少量と最終ダメージを整数表示に変更
            player.sendMessage("§c攻撃クールダウン！ §c-" + displayReducedDamage + " §7ダメージ §a(" + displayFinalDamage + ")");
        }

        if (isCrit) {
            player.sendMessage("§6§lクリティカルヒット！ §c+" + Math.round(finalDamage));
        }

        player.getWorld().spawnParticle(Particle.CRIT, targetLiving.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);

        // --- ダメージ適用 ---
        applyCustomDamage(targetLiving, finalDamage, player);

        tryTriggerOnHitSkill(player, targetLiving, player.getInventory().getItemInMainHand());

        if (isSpearWeapon(player.getInventory().getItemInMainHand())) {

            // 槍の貫通ダメージを計算 (例: メインダメージの50%)
            double cleaveDamage = finalDamage * 0.5;

            // ターゲットと攻撃者の間のベクトル（方向）を取得
            Vector direction = targetLiving.getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize();

            // ターゲットの後方2ブロック（最大3体）のエンティティを探す
            Location targetLoc = targetLiving.getLocation();

            // 貫通範囲 (ターゲットの座標から方向ベクトルに沿って延長)
            // 2ブロックの範囲をチェック: ターゲットから1ブロック後方、2ブロック後方
            for (int i = 1; i <= 2; i++) {
                Location checkLoc = targetLoc.clone().add(direction.clone().multiply(i));

                // checkLocの近くにいるLivingEntityを取得
                // Bukkit.getScheduler().runTaskLater を使って非同期で実行することを推奨しますが、
                // シンプルな同期処理として、周囲のエンティティを取得します。
                targetLoc.getWorld().getNearbyEntities(checkLoc, 0.7, 0.7, 0.7).forEach(entity -> {
                    if (entity instanceof LivingEntity nextTarget && !entity.equals(player) && !entity.equals(targetLiving)) {

                        // ターゲット済みではないかチェックし、ダメージを適用
                        // 貫通ダメージはクリティカル判定なしで適用

                        // ダメージを適用する前に、再帰防止のために再度チェック
                        if (!isProcessingDamage.contains(player.getUniqueId())) {

                            if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
                                e.setDamage(0.0);
                                e.setCancelled(true);
                                return;
                            }

                            // ★ 貫通ダメージ適用
                            applyCustomDamage(nextTarget, cleaveDamage, player);

                            // エフェクト
                            nextTarget.getWorld().spawnParticle(Particle.SWEEP_ATTACK, nextTarget.getLocation().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.0);
                            player.sendMessage("§e貫通！ §a" + nextTarget.getName() + " §7に §c+" + Math.round(cleaveDamage) + " §7ダメージ");
                        }
                    }
                });
            }
        }
    }

    // ----------------------------------------------------
    // --- C. 遠距離物理ダメージ処理 ---
    // ----------------------------------------------------
    // メソッド名を変更し、優先度をHIGHにすることで、近接の後の処理を狙う
    @EventHandler(priority = EventPriority.HIGH)
    public void onRangeDamage(EntityDamageByEntityEvent e) {

        Player attackerPlayer = null;
        if (e.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                attackerPlayer = (Player) shooter;
                e.getDamager().remove(); // 矢の二重ダメージ防止のため即座に削除
            } else {
                return; // プレイヤー以外の投射物は無視
            }
        } else {
            return; // 投射物でない場合は無視
        }

        // 攻撃者がプレイヤーでない、または処理中の場合は無視
        if (attackerPlayer == null) return;
        if (isProcessingDamage.contains(attackerPlayer.getUniqueId())) return;
        if (!(e.getEntity() instanceof LivingEntity targetLiving)) return;

        // 遠距離攻撃は常にカスタム処理を行うため、元のイベントはキャンセル
        e.setDamage(0.0);
        Player player = attackerPlayer;

        // --- (既存の遠距離ダメージ計算ロジック) ---
        StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
        StatMap defender = (targetLiving instanceof Player p)
                ? StatManager.getTotalStatsFromEquipment(p)
                : new StatMap();
        // ... (省略: 攻撃力、防御力、クリティカル、距離倍率計算) ...
        double attack = attacker.getFinal(StatType.PROJECTILE_DAMAGE);
        double defense = defender.getFinal(StatType.DEFENSE);
        double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
        double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);
        double originalDamage = e.getDamage(); // 弓の基本ダメージとして使用
        // 【TRPG 1d100形式】1から100までのランダムな目を生成
        int roll = (int)(Math.random() * 100) + 1;
        // 判定：出目がクリティカル率以下なら成功
        boolean isCrit = roll <= critChance;
        double distanceMultiplier = calculateDistanceMultiplier(player, targetLiving); // 既存のヘルパーメソッドが必要

        double baseDamage = attack + originalDamage;
        if (isCrit) {
            baseDamage *= (critDamage / 100.0);
        }
        baseDamage *= distanceMultiplier;

        double defenseRatio = defense / (defense + 100.0);
        double finalDamage = baseDamage * (1.0 - defenseRatio);
        finalDamage = Math.max(0.1, finalDamage);

        if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
            e.setDamage(0.0);
            e.setCancelled(true);
            return;
        }

        // エフェクト・演出 (距離倍率を表示)
        String multiplierText = String.format("%.1f%%", distanceMultiplier * 100);
        String messagePrefix = (isCrit ? "§6§lクリティカルヒット！(遠距離) " : "§7物理ダメージ！(遠距離) ");
        player.sendMessage(messagePrefix + "§c+" + Math.round(finalDamage) + " §e[" + multiplierText + "]" );

        if (isCrit) {
            player.getWorld().spawnParticle(Particle.CRIT, targetLiving.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }

        // --- ダメージ適用 ---
        applyCustomDamage(targetLiving, finalDamage, player);
        tryTriggerOnHitSkill(player, targetLiving, player.getInventory().getItemInMainHand());
    }

    // ----------------------------------------------------
    // --- D. 共通ダメージ適用ヘルパーメソッド ---
    // ----------------------------------------------------

    /**
     * カスタムダメージをターゲットに適用する共通ロジック
     * @param target ダメージを受けるエンティティ
     * @param damage 適用する最終ダメージ
     * @param damager ダメージを与えたエンティティ (表示用)
     */
    private void applyCustomDamage(LivingEntity target, double damage, Player damager) { // Player damager はそのまま
        if (target instanceof Player targetPlayer) {
            // ★ 修正: Player damager から名前を取得して渡す
            processPlayerDamageWithAbsorption(targetPlayer, damage, damager.getName());
        } else {
            // MOBの場合の処理はそのまま
            isProcessingDamage.add(damager.getUniqueId());
            try {
                target.damage(damage, damager);
            } finally {
                isProcessingDamage.remove(damager.getUniqueId());
            }
        }
    }


    // ----------------------------------------------------
    // --- E. MOB/環境からプレイヤーへのダメージ処理 ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST) // 最優先で実行
    public void onPlayerReceivingDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.isCancelled()) return;

        // 既に他のカスタムダメージ処理（例: 近接、遠距離、魔法）で処理されている場合はスキップ
        // ただし、上記A, B, Cはプレイヤーが「攻撃者」のパターンなので、ここでは主に環境ダメージや、
        // MOBからの攻撃で EntityDamageByEntityEvent のカスタム処理が行われなかったケースを捕捉します。

        // ★ 警告: MOBからのEntityDamageByEntityEventは、onMeleeDamageやonRangeDamageでは
        // DamagerがPlayerであることをチェックしているため、ここでは捕捉されます。

        // 1. ダメージソースの特定
        LivingEntity attacker = null;
        if (e instanceof EntityDamageByEntityEvent byEntityEvent) {
            if (byEntityEvent.getDamager() instanceof LivingEntity living) {
                attacker = living;
            } else if (byEntityEvent.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
                attacker = living;
            }
            // AttackerがPlayerの場合、既にonMeleeDamageやonRangeDamageで処理されているはずだが、
            // クリティカルでなかった場合はバニラに任せるため、ここでも処理を行う必要がある。
        }

        // 2. 防御力/軽減の計算
        StatMap defender = StatManager.getTotalStatsFromEquipment(player);
        double defense = defender.getFinal(StatType.DEFENSE);
        double incomingDamage = e.getFinalDamage(); // Bukkitが計算した後の値（バニラの防御力は適用済みかもしれないが、無視する）

        if (e instanceof EntityDamageByEntityEvent byEntityEvent) {
            // --- A. MOBの弓ダメージ処理 ---
            if (attacker != null && !(attacker instanceof Player) && attacker instanceof Mob mob) {
                // 弓を持っている場合（Projectileからのダメージである前提）
                if (byEntityEvent.getDamager() instanceof Projectile projectile &&
                        e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {

                    // Mobのメインハンドアイテムを取得
                    ItemStack mainHand = mob.getEquipment().getItemInMainHand();

                    // 弓（またはクロスボウ）を持っているか、または弓ダメージ特有の処理を行う
                    if (mainHand.getType().name().contains("BOW")) {
                        // MOBの持つ弓のカスタムステータスを取得（仮のメソッドを想定）
                        StatMap mobWeaponStats = StatManager.readStatsFromItem(mainHand);
                        double bonusRangeDamage = mobWeaponStats.getFinal(StatType.PROJECTILE_DAMAGE);
                        double distanceMultiplier = calculateDistanceMultiplier(player,attacker);

                        bonusRangeDamage *= distanceMultiplier;
                        incomingDamage += bonusRangeDamage;
                        
                        player.sendMessage("§3§l敵の弓による追加ダメージ！ §b+" + String.format("%.1f", bonusRangeDamage));
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 1. ENTITY_EXPLOSION (魔法ダメージ) 処理 と 早期リターン
        // ------------------------------------------------------------------
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {

            // 爆発ダメージを魔法ダメージとして処理し、後続の物理防御をスキップ

            defender = StatManager.getTotalStatsFromEquipment(player);
            double magicDefense = defender.getFinal(StatType.MAGIC_RESIST);
            double baseDamage = e.getFinalDamage(); // バニラが計算した爆発ダメージをベースとする

            // 魔法防御の計算ロジックを適用 (二重軽減を避けるため、ここで最終ダメージとする)
            double magicDefenseRatio = magicDefense / (magicDefense + 100.0);
            double finalExplosionDamage = baseDamage * (1.0 - magicDefenseRatio);
            finalExplosionDamage = Math.max(0.1, finalExplosionDamage); // 最低ダメージ保証

            // 魔法ダメージのエフェクトとメッセージ
            player.sendMessage("§5§l魔法ダメージ！ §c" + String.format("%.1f", finalExplosionDamage) + "のダメージを受けました。");

            // バニラのダメージをキャンセル
            e.setDamage(0.0);

            // HP圧縮ロジックを適用し、ここで処理を終了 (二重軽減を回避)
            String attackerName = (attacker != null) ? attacker.getName() : "環境";
            processPlayerDamageWithAbsorption(player, finalExplosionDamage, attackerName);

            return; // ★ 処理を終了させることで、後続の物理防御・クリティカルロジックをスキップ
        }

        // ★★★ MOBからの攻撃に対するカスタムクリティカル判定 ★★★
        if (attacker != null && !(attacker instanceof Player)) {

            // 1. クリティカル成功値 (40から60でランダム)
            int minCritChance = 5;
            int maxCritChance = 30;
            int mobCritChance = (int)(Math.random() * (maxCritChance - minCritChance + 1)) + minCritChance;

            // 2. 1d100ロール
            int roll = (int)(Math.random() * 100) + 1;

            // 3. 判定：出目が成功値以下ならクリティカル
            boolean isMobCrit = roll <= mobCritChance;

            if (isMobCrit) {
                // 4. クリティカル倍率 (1.2から2.0でランダム)
                double minMultiplier = 1.2;
                double maxMultiplier = 2.0;
                double mobCritMultiplier = Math.random() * (maxMultiplier - minMultiplier) + minMultiplier;

                // 5. ダメージに倍率を適用
                incomingDamage *= mobCritMultiplier;

                // 6. エフェクトとメッセージ
                player.sendMessage("§4§l敵のクリティカル！ §c" + attacker.getName() + " §4から痛恨の一撃！");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                // 赤い粒子でクリティカルヒットを表現
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
            }
        }
        // ★★★ クリティカル判定終了 ★★★

        // 定数を500.0に変更 (カーブが緩やかになり、軽減率50%に到達するのに防御力500が必要)
        double defenseRatio = defense / (defense + 500.0);
        double finalDamage = incomingDamage * (1.0 - defenseRatio);
        finalDamage = Math.max(0.1, finalDamage);

        e.setDamage(0.0);

        // 4. HP圧縮ロジックの適用
        String attackerName = (attacker != null) ? attacker.getName() : "環境";

        // ★ ここで HP 圧縮ヘルパーメソッドを呼び出す
        processPlayerDamageWithAbsorption(player, finalDamage, attackerName);
    }

    /**
     * バニラのHP回復イベントをフックし、カスタムHPに割り当てる
     */
    @EventHandler(priority = EventPriority.HIGHEST) // 最優先で実行
    public void onEntityRegainHealth(EntityRegainHealthEvent e) {
        // プレイヤーでなければ無視
        if (!(e.getEntity() instanceof Player player)) return;

        // 既に他のプラグインによってキャンセルされている場合はスキップ
        if (e.isCancelled()) return;

        // 1. 回復ソースの確認とフィルタリング
        EntityRegainHealthEvent.RegainReason reason = e.getRegainReason();

        // 魔法 (ポーション)、再生エフェクト、自然回復、食事による回復などを対象とする
        // 必要に応じて、カスタムHPに割り当てたくない回復理由 (例: PLUGIN) を除外してください。
        if (reason == EntityRegainHealthEvent.RegainReason.MAGIC ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN ||
                reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.EATING ||
                reason == EntityRegainHealthEvent.RegainReason.CUSTOM) {

            // 2. バニラの回復量を把握
            double vanillaHealAmount = e.getAmount();

            // 3. バニラの回復をキャンセル
            e.setCancelled(true);

            // 4. カスタムHPシステムに回復量を適用
            Deepwither.getInstance().getStatManager().healCustomHealth(player,vanillaHealAmount);
        }
    }

    /**
     * HPバー圧縮ロジックを持つプレイヤーにダメージを適用する。
     */
    /**
     * HPバー圧縮ロジックと衝撃吸収ロジックを持つプレイヤーにダメージを適用する。
     */
    private void processPlayerDamageWithAbsorption(Player targetPlayer, double damage, String damagerName) {

        // 1. 衝撃吸収値の取得（バニラハート数）
        double vanillaAbsorptionHearts = targetPlayer.getAbsorptionAmount();

        // 2. カスタム値（カスタムHP換算）に変換
        // 1 ハート = 10 カスタムHP と仮定
        double currentAbsorptionCustom = vanillaAbsorptionHearts * 10.0;

        if (currentAbsorptionCustom > 0) {
            // --- 衝撃吸収がある場合の処理 ---

            if (damage <= currentAbsorptionCustom) {
                // A) ダメージが衝撃吸収値以下の場合
                double newAbsorptionCustom = currentAbsorptionCustom - damage;

                // バニラ値に戻して設定
                double newAbsorptionHearts = newAbsorptionCustom / 10.0;
                targetPlayer.setAbsorptionAmount((float) newAbsorptionHearts);

                targetPlayer.sendMessage("§e-" + Math.round(damage) + " §7ダメージを§6衝撃吸収§7しました。 (§6" + String.format("%.0f", newAbsorptionCustom) + "§7)");

                return;

            } else {
                // B) ダメージが衝撃吸収値を超える場合

                // 衝撃吸収ハートを全て削除
                targetPlayer.setAbsorptionAmount(0.0f);

                targetPlayer.sendMessage("§e-" + String.format("%.0f", currentAbsorptionCustom) + " §7ダメージを§6衝撃吸収§7しましたが、§c吸収限界を超えました！");
                targetPlayer.sendMessage("§cメインHPは守られました。");

                // メインHPにダメージは与えないため、ここで処理を終了
                return;
            }

        }

        // --- 衝撃吸収がない場合のメインHP適用処理 (既存のロジック) ---

        double currentHealth = statManager.getActualCurrentHealth(targetPlayer);
        double newHealth = currentHealth - damage;

        statManager.setActualCurrentHealth(targetPlayer, newHealth);

        if (newHealth <= 0.0) {
            targetPlayer.sendMessage("§4あなたは §c" + damagerName + " §4によって倒されました。");
        } else {
            targetPlayer.sendMessage("§c-" + Math.round(damage) + " §7ダメージを受けました。(被弾)");
        }
    }


    // ----------------------------------------------------
    // --- E. ユーティリティメソッド (既存のロジックから抽出) ---
    // ----------------------------------------------------

    // 既存の距離倍率計算ロジックを独立したメソッドとして抽出
    private double calculateDistanceMultiplier(Player player, LivingEntity targetLiving) {
        double distance = targetLiving.getLocation().distance(player.getLocation());

        final double MIN_DISTANCE = 10.0;
        final double MAX_BOOST_DISTANCE = 40.0;
        final double MAX_MULTIPLIER = 1.2;
        final double MIN_MULTIPLIER = 0.6;

        double distanceMultiplier;

        if (distance <= MIN_DISTANCE) {
            double range = MIN_DISTANCE;
            double minMaxDiff = 1.0 - MIN_MULTIPLIER;
            distanceMultiplier = MIN_MULTIPLIER + (distance / range) * minMaxDiff;

        } else if (distance >= MAX_BOOST_DISTANCE) {
            distanceMultiplier = MAX_MULTIPLIER;

        } else {
            double range = MAX_BOOST_DISTANCE - MIN_DISTANCE;
            double current = distance - MIN_DISTANCE;
            double minMaxDiff = MAX_MULTIPLIER - 1.0;
            distanceMultiplier = 1.0 + (current / range) * minMaxDiff;
        }

        return Math.max(MIN_MULTIPLIER, Math.min(distanceMultiplier, MAX_MULTIPLIER));
    }

    // ----------------------------------------------------
// ★ ヘルパーメソッド: 槍判定
// ----------------------------------------------------
    private boolean isSpearWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                // "タイプ: 槍" をチェック
                if (line.contains("§7カテゴリ:§f槍")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * WorldGuardのPvPフラグをチェックし、PvPが拒否されている場合はtrueを返す。
     * @param attacker 攻撃者 (Player)
     * @param target ターゲット (LivingEntity)
     * @return PvPが許可されていない場合 (false) は true を返す
     */
    private boolean isPvPPrevented(Player attacker, LivingEntity target) {
        if (!target.getWorld().getName().equals(attacker.getWorld().getName())) {
            return false; // 異なるワールド間の攻撃は通常許可される（要件次第）
        }

        // 攻撃者がプレイヤーでない場合、このチェックは不要だが、念のためガード
        if (!(target instanceof Player)) {
            return false;
        }

        // WorldGuardが有効でなければ何もしない
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }

        try {
            com.sk89q.worldguard.protection.regions.RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();

            // 攻撃者の位置でPvPフラグをチェック
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(attacker.getLocation()));

            // PvPフラグがDENYされているかを確認
            // target=nullでチェックすると、グローバルやリージョン内のフラグが適用される
            if (!set.testState(null, com.sk89q.worldguard.protection.flags.Flags.PVP)) {
                // PvPがDENYされている
                attacker.sendMessage("§cこの区域ではPvPが禁止されています。");
                return true; // ダメージ適用を防止
            }
            return false;

        } catch (NoClassDefFoundError ex) {
            // WorldGuardが見つからない場合の例外処理
            return false;
        }
    }

    /**
     * On-Hitスキル発動ロジック
     * @param attacker スキルを発動するプレイヤー
     * @param target スキルターゲット
     * @param item 攻撃に使用したアイテム
     */
    private void tryTriggerOnHitSkill(Player attacker, LivingEntity target, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 1. PDCから設定値を読み込み
        Double chance = container.get(ItemLoader.SKILL_CHANCE_KEY, PersistentDataType.DOUBLE);
        Integer cooldown = container.get(ItemLoader.SKILL_COOLDOWN_KEY, PersistentDataType.INTEGER);
        String skillId = container.get(ItemLoader.SKILL_ID_KEY, PersistentDataType.STRING);

        if (chance == null || skillId == null) return;

        // 2. クールダウンチェック
        long lastTrigger = onHitCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = (cooldown != null) ? cooldown * 1000L : 0L;

        if (currentTime < lastTrigger + cooldownMillis) {
            // クールダウン中
            // attacker.sendMessage("§e[On-Hit] クールダウン中: あと " + (lastTrigger + cooldownMillis - currentTime) / 1000.0 + "秒"); // デバッグ用
            return;
        }

        // 3. 確率判定 (1d100)
        int roll = (int) (Math.random() * 100) + 1;

        if (roll <= chance) {
            // 4. スキル発動
            MythicBukkit.inst().getAPIHelper().castSkill(attacker.getPlayer(),skillId);

            // 5. クールダウンを更新
            onHitCooldowns.put(attacker.getUniqueId(), currentTime);

            // フィードバック
            attacker.sendMessage("§a[On-Hit] スキル「§b" + skillId + "§a」を発動！");
        }
    }
}