package com.warriorssmp.teams;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class TeamEnderChestListener implements Listener {

    private final TeamManager teamManager;

    public TeamEnderChestListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Team team = teamManager.findTeamByEnderChestInventory(event.getInventory());
        if (team == null) return;
        teamManager.flushEnderChest(team);
        teamManager.saveTeam(team);
    }
}
