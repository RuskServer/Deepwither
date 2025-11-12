package com.lunar_prototype.deepwither;

import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // 追加
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DamageManager implements Listener {

    private final Set<UUID> isProcessingDamage = new HashSet<>();
    private final StatManager statManager; // StatManagerへの参照を追加

    public DamageManager(StatManager statManager) {
        this.statManager = statManager;
    }

    // ----------------------------------------------------
    // --- A. Mythic Mobs 魔法ダメージ処理 ---
    // ----------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onMythicDamage(MythicDamageEvent e){
        if (!(e.getCaster().getEntity().getBukkitEntity() instanceof Player player)) return;
        if (!(e.getTarget().getBukkitEntity() instanceof LivingEntity targetLiving)) return;

        // ... (既存の魔法ダメージ計算ロジックは変更なし) ...
        if (e.getDamageMetadata().getDamageCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION){
            StatMap attacker = StatManager.getTotalStatsFromEquipment(player);
            StatMap defender = (targetLiving instanceof Player p)
                    ? StatManager.getTotalStatsFromEquipment(p)
                    : new StatMap();

            double magicAttack = attacker.getFinal(StatType.MAGIC_DAMAGE);
            double magicDefense = defender.getFinal(StatType.MAGIC_RESIST);
            double magicPenetration = attacker.getFinal(StatType.MAGIC_PENETRATION);
            double critChance = attacker.getFinal(StatType.CRIT_CHANCE);
            double critDamage = attacker.getFinal(StatType.CRIT_DAMAGE);

            boolean isCrit = Math.random() * 100 < critChance;

            double baseDamage = magicAttack + e.getDamage();
            if (isCrit) {
                baseDamage *= (critDamage / 100.0);
                player.sendMessage("§6§l魔法クリティカル！");
            }

            double effectiveMagicDefense = Math.max(0, magicDefense - magicPenetration);
            double magicDefenseRatio = effectiveMagicDefense / (effectiveMagicDefense + 100.0);
            double finalMagicDamage = baseDamage * (1.0 - magicDefenseRatio);

            if (targetLiving instanceof Player && isPvPPrevented(player, targetLiving)) {
                e.setDamage(0.0);
                e.setCancelled(true);
                return;
            }

            player.sendMessage("§b§l魔法ダメージ！ §c+" + Math.round(finalMagicDamage));

            applyCustomDamage(targetLiving,finalMagicDamage,player);
            e.setDamage(0.0);

            if (targetLiving instanceof Player targetPlayer) {
                processPlayerDamageWithAbsorption(targetPlayer, finalMagicDamage, player.getName()); // プレイヤー名を渡す
                e.setCancelled(true);
            }
        }
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
        boolean isCrit = Math.random() * 100 < critChance;

        // ここで元のイベントをキャンセルし、独自にダメージを計算・適用
        e.setDamage(0.0);

        // クリティカルダメージ計算 (e.getDamage()は使わず、StatMapのATTACK_DAMAGEを基点とする)
        double baseDamage = attack * (critDamage / 100.0);
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
            // クールダウンが完了していない場合、ダメージに進行度を掛け合わせる
            // 例: クールダウン50%の場合、ダメージは半分になる
            finalDamage *= attackCooldown;

            // メッセージ (デバッグ/フィードバック用)
            player.sendMessage("§c攻撃クールダウン中！ §7ダメージが §c" + Math.round((1.0 - attackCooldown) * 100) + "% §7減少しました。");

            // 最低ダメージを保証
            finalDamage = Math.max(0.1, finalDamage);
        }

        if (isCrit) {
            player.sendMessage("§6§lクリティカルヒット！ §c+" + Math.round(finalDamage));
        }

        player.getWorld().spawnParticle(Particle.CRIT, targetLiving.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);

        // --- ダメージ適用 ---
        applyCustomDamage(targetLiving, finalDamage, player);

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
        boolean isCrit = Math.random() * 100 < critChance;
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

        // MOB/環境ダメージに対するカスタム軽減を適用

        // 防御計算 (防御力ベースの軽減を再計算)
        double defenseRatio = defense / (defense + 100.0);
        double finalDamage = incomingDamage * (1.0 - defenseRatio); // ここは要件に応じて調整
        finalDamage = Math.max(0.1, finalDamage);

        e.setDamage(0.0);

        // 4. HP圧縮ロジックの適用
        String attackerName = (attacker != null) ? attacker.getName() : "環境";

        // ★ ここで HP 圧縮ヘルパーメソッドを呼び出す
        processPlayerDamageWithAbsorption(player, finalDamage, attackerName);
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
}