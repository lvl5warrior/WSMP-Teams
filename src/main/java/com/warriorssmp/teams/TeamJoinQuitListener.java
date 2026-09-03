package com.warriorssmp.teams;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeamJoinQuitListener implements Listener {

    private final TeamManager teamManager;

    public TeamJoinQuitListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        teamManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamManager.onPlayerQuit(event.getPlayer());
    }
}
