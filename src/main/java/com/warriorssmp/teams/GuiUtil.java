package com.warriorssmp.teams;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Same GUI helper conventions as WSMP-SimpleSell's GuiUtil, so every WSMP menu
 * looks and behaves the same way.
 */
public class GuiUtil {

    public static void hideExtras(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );
    }

    public static ItemStack namedItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack namedItem(Material material, String name, List<String> lore) {
        return namedItem(material, name, lore.toArray(new String[0]));
    }

    public static ItemStack coloredPane(Material material, String name) {
        return namedItem(material, name);
    }

    /** A player-head icon for team member/application menus. */
    public static ItemStack playerHead(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }
}
