package com.lunar_prototype.deepwither.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Clan {
    private final String id;
    private String name;
    private String tag;
    private UUID owner;
    private final Set<UUID> members = new HashSet<>();

    public Clan(String id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
        this.tag = name.substring(0, Math.min(name.length(), 4)).toUpperCase();
    }

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
        broadcast(Component.text(message));
    }

    public void broadcast(Component message) {
        Component fullMessage = Component.text("[Clan] ", NamedTextColor.AQUA)
                .append(message.colorIfAbsent(NamedTextColor.WHITE));
        for (UUID uuid : members) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(fullMessage);
            }
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
}
