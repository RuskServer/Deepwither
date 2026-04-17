package com.lunar_prototype.deepwither.modules.minerun;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * MineRun（ネガティブレイヤー）における特殊な演出効果を管理するクラス。
 * NMSを使用してパケットレベルでの微調整を行うことで、バニラでは不可能な「異質さ」を演出する。
 */
public class MineRunEffects {
    
    private static final Random RANDOM = new Random();
    
    /**
     * 指定されたプレイヤーに歪んだ環境音を再生します。
     * 
     * @param bukkitPlayer 対象のプレイヤー
     */
    public static void playDistortedAmbient(Player bukkitPlayer) {
        if (!(bukkitPlayer instanceof CraftPlayer)) return;
        
        net.minecraft.server.level.ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
        
        // エンドの不気味な音や洞窟の環境音
        String[] sounds = {
            "ambient.cave",
            "entity.enderman.stare",
            "block.end_portal.spawn",
            "entity.warden.heartbeat",
            "block.respawn_anchor.deplete"
        };
        
        String soundName = sounds[RANDOM.nextInt(sounds.length)];
        ResourceLocation location = ResourceLocation.withDefaultNamespace(soundName);
        
        // 1.21.x では Optional<Holder.Reference<SoundEvent>> が返されるため、適切に取得
        BuiltInRegistries.SOUND_EVENT.get(location).ifPresent(holder -> {
            SoundEvent soundEvent = holder.value();
            
            // ピッチを通常（1.0）より極端に低くすることで「不気味」「巨大」「歪み」を演出
            // 0.2〜0.6あたりの低いピッチは人間にとって本能的に恐怖を感じるような歪んだ音になることが多い
            float pitch = 0.2f + RANDOM.nextFloat() * 0.4f; 
            
            ClientboundSoundPacket packet = new ClientboundSoundPacket(
                Holder.direct(soundEvent),
                SoundSource.AMBIENT,
                nmsPlayer.getX(), nmsPlayer.getY(), nmsPlayer.getZ(),
                0.8f, // 音量
                pitch, // 歪んだピッチ
                RANDOM.nextLong() // ランダムシード
            );
            
            nmsPlayer.connection.send(packet);
        });
    }
}
