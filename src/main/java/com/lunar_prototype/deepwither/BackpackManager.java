package com.lunar_prototype.deepwither;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@DependsOn({})
public class BackpackManager implements IManager {

    private final JavaPlugin plugin;
    private final Map<UUID, BackpackSession> activeSessions = new HashMap<>();

    // 内部クラス（変更なし）
    private static class BackpackSession {
        final int entityId;
        final int taskId;
        final int modelData;

        BackpackSession(int entityId, int taskId, int modelData) {
            this.entityId = entityId;
            this.taskId = taskId;
            this.modelData = modelData;
        }
    }

    public BackpackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {}

    @Override
    public void shutdown() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                unequipBackpack(p);
            }
        }
        activeSessions.clear();
    }

    public boolean hasBackpack(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public int getModelData(Player player) {
        BackpackSession session = activeSessions.get(player.getUniqueId());
        return session != null ? session.modelData : 0;
    }

    public void equipBackpack(Player player, int modelData) {
        unequipBackpack(player); // 既存があれば消す

        int entityId = ThreadLocalRandom.current().nextInt(1000000, Integer.MAX_VALUE);

        // --- 定期更新タスクの開始 ---
        // タスクは全員に対してパケットを送るため、ここで作成してOK
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                unequipBackpack(player);
                return;
            }
            // セッション情報が消えていたらタスクも停止
            if (!activeSessions.containsKey(player.getUniqueId())) {
                // ここでcancelできない場合もあるので、
                return;
            }

            float currentBodyYaw = player.getBodyYaw();
            WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(entityId, currentBodyYaw);
            WrapperPlayServerEntityRotation rotationPacket = new WrapperPlayServerEntityRotation(entityId, currentBodyYaw, 0, true);

            for (Player p : player.getWorld().getPlayers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, headLookPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, rotationPacket);
            }
        }, 0L, 1L);

        // セッション保存
        BackpackSession session = new BackpackSession(entityId, task.getTaskId(), modelData);
        activeSessions.put(player.getUniqueId(), session);

        // 全員（自分含む）にスポーンパケットを送信
        for (Player p : player.getWorld().getPlayers()) {
            sendSpawnPackets(player, session, p);
        }
    }

    /**
     * 追加メソッド: 既存のバックパック(owner)を、特定のプレイヤー(receiver)に見せる
     */
    public void showBackpackToPlayer(Player owner, Player receiver) {
        BackpackSession session = activeSessions.get(owner.getUniqueId());
        if (session == null) return;

        // 同じワールドにいる場合のみ送信
        if (owner.getWorld().equals(receiver.getWorld())) {
            sendSpawnPackets(owner, session, receiver);
        }
    }

    /**
     * パケット生成と送信ロジックを共通化
     */
    private void sendSpawnPackets(Player owner, BackpackSession session, Player receiver) {
        Location loc = owner.getLocation();
        float bodyYaw = owner.getBodyYaw();

        // 1. Spawn Entity
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                session.entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.ARMOR_STAND,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                bodyYaw,
                bodyYaw,
                0,
                Optional.empty()
        );

        // 2. Metadata
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20)); // Invisible
        metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x10)); // Marker
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(session.entityId, metadata);

        // 3. Equipment
        ItemStack helmetItem = ItemStack.builder()
                .type(ItemTypes.PAPER)
                .component(ComponentTypes.CUSTOM_MODEL_DATA, session.modelData)
                .build();
        List<Equipment> equipmentList = Collections.singletonList(new Equipment(EquipmentSlot.HELMET, helmetItem));
        WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(session.entityId, equipmentList);

        // 4. Mount
        WrapperPlayServerSetPassengers mountPacket = new WrapperPlayServerSetPassengers(
                owner.getEntityId(),
                new int[]{session.entityId}
        );

        // 送信
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, metadataPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, equipmentPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, mountPacket);
    }

    public void unequipBackpack(Player player) {
        BackpackSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        Bukkit.getScheduler().cancelTask(session.taskId);

        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(new int[]{session.entityId});
        for (Player p : player.getWorld().getPlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, destroyPacket);
        }
    }

    public void cleanup(Player player) {
        unequipBackpack(player);
    }
}