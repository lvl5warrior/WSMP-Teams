package com.warriorssmp.teams;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class TeamEnderChestListener implements Listener {

    private final TeamManager teamManager;

    public TeamEnderChestListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    /** The back button lives in a whole extra row appended past the real
     *  storage size (see TeamManager.getEnderChest) - any click at or past
     *  that boundary is the footer row, never a real storage slot, so it's
     *  always safe to cancel and treat as "close" regardless of which
     *  exact slot in that row was clicked. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Team team = teamManager.findTeamByEnderChestInventory(event.getInventory());
        if (team == null) return;

        int storageSize = teamManager.getEnderChestSize();
        if (event.getRawSlot() >= storageSize && event.getRawSlot() < event.getInventory().getSize()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Team team = teamManager.findTeamByEnderChestInventory(event.getInventory());
        if (team == null) return;
        teamManager.flushEnderChest(team);
        teamManager.saveTeam(team);
    }
}
