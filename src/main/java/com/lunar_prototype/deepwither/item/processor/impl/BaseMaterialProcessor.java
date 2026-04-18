package com.lunar_prototype.deepwither.item.processor.impl;

import com.lunar_prototype.deepwither.item.processor.ItemLoadContext;
import com.lunar_prototype.deepwither.item.processor.ItemProcessor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.UUID;

public class BaseMaterialProcessor implements ItemProcessor {
    @Override
    public void process(ItemLoadContext context) {
        String key = context.getKey();
        YamlConfiguration config = context.getConfig();

        String materialName = config.getString(key + ".material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            System.err.println("Invalid material for item: " + key);
            context.setValid(false);
            return;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            System.err.println("ItemMeta is null for: " + key);
            context.setValid(false);
            return;
        }

        if (material == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) meta;
            String textureUrl = config.getString(key + ".texture-url");
            if (textureUrl != null) {
                try {
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(textureUrl));
                    profile.setTextures(textures);
                    skullMeta.setOwnerProfile(profile);
                } catch (Exception e) {
                    System.err.println("Failed to set custom texture for player head: " + key);
                    e.printStackTrace();
                }
            }
        }

        int customModelData = config.getInt(key + ".custom_mode_data");
        if (customModelData != 0) {
            meta.setCustomModelData(customModelData);
        }

        context.setMaterial(material);
        context.setItem(item);
        context.setMeta(meta);
    }
}
