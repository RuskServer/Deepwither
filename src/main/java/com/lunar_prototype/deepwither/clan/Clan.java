package com.lunar_prototype.deepwither.clan;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Clan {
    private final String id;      // 内部管理用ID (UUIDなど)
    private String name;          // 表示名
    private String tag;           // チャット用タグ (例: [RUSK])
    private UUID owner;           // リーダー
    private final Set<UUID> members = new HashSet<>(); // メンバーリスト

    public Clan(String id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members.add(owner); // リーダーは最初からメンバー
        this.tag = name.substring(0, Math.min(name.length(), 4)).toUpperCase(); // 仮のタグ生成
    }

    // --- ロジック ---

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public void broadcast(String message) {
        for (UUID uuid : members) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§b[Clan] §f" + message);
            }
        }
    }

    // --- Getter / Setter ---
    public String getId() { return id; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
}