package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TeamPortalsGUI implements Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "&4&lWSMP &8» &7Team Portals");
    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26};
    private static final int[] PORTAL_SLOTS = {11, 13, 15};

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final TeamLevelManager levelManager;
    private final TeamPortalManager portalManager;

    public TeamPortalsGUI(TeamsPlugin plugin, TeamManager teamManager, TeamLevelManager levelManager, TeamPortalManager portalManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.levelManager = levelManager;
        this.portalManager = portalManager;
    }

    public void open(Player viewer, Team team) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE);

        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack accent = GuiUtil.coloredPane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int slot : BORDER_SLOTS) {
            gui.setItem(slot, (slot % 2 == 0) ? border : accent);
        }
        gui.setItem(22, GuiUtil.namedItem(Material.BARRIER, "&7Back to Team Menu"));

        int maxPortals = levelManager.getMaxPortals(team.getLevel());
        boolean isLeader = team.isLeader(viewer.getUniqueId());

        for (int i = 0; i < 3; i++) {
            int portalSlotNumber = i + 1;
            TeamPortal portal = team.getPortal(portalSlotNumber);
            List<String> lore = new ArrayList<>();

            if (portalSlotNumber > maxPortals) {
                lore.add("&7Locked - reach a higher team level.");
                gui.setItem(PORTAL_SLOTS[i], GuiUtil.namedItem(Material.GRAY_DYE, "&7Portal " + portalSlotNumber + " (Locked)", lore.toArray(new String[0])));
            } else if (portal == null) {
                if (isLeader) {
                    lore.add("&7Not set. Stand where you want it");
                    lore.add("&7and type &f/team portal set " + portalSlotNumber);
                } else {
                    lore.add("&7Not set yet by your leader.");
                }
                gui.setItem(PORTAL_SLOTS[i], GuiUtil.namedItem(Material.ENDER_EYE, "&5Portal " + portalSlotNumber + " (Unset)", lore.toArray(new String[0])));
            } else if (portal.isOnCooldown(viewer.getUniqueId())) {
                long secs = portal.secondsRemaining(viewer.getUniqueId());
                lore.add("&cOn cooldown: " + (secs / 60) + "m " + (secs % 60) + "s");
                lore.add("&7(Your own cooldown - other");
                lore.add("&7members may still be able to use it.)");
                gui.setItem(PORTAL_SLOTS[i], GuiUtil.namedItem(Material.ENDER_EYE, "&5Portal " + portalSlotNumber, lore.toArray(new String[0])));
            } else {
                int castSeconds = plugin.getConfig().getInt("portal-cast-seconds", 5);
                lore.add("&7Ready to use.");
                lore.add("&7" + castSeconds + " second cast, don't move.");
                lore.add("");
                lore.add("&e&lClick to teleport!");
                gui.setItem(PORTAL_SLOTS[i], GuiUtil.namedItem(Material.ENDER_PEARL, "&5&lPortal " + portalSlotNumber, lore.toArray(new String[0])));
            }
        }

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 22) {
            plugin.getTeamGUI().open(player);
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (PORTAL_SLOTS[i] != slot) continue;
            int portalSlotNumber = i + 1;
            Team team = teamManager.getTeam(player.getUniqueId());
            if (team == null) return;
            TeamPortal portal = team.getPortal(portalSlotNumber);
            if (portal == null || portal.isOnCooldown(player.getUniqueId())) return;
            player.closeInventory();
            portalManager.usePortal(player, portalSlotNumber);
        }
    }
}
