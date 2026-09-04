package com.warriorssmp.teams;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamPortalManager implements Listener {

    private static class CastSession {
        final Team team;
        final int slot;
        final Location startLocation;
        final BukkitTask task;
        int ticksRemaining;

        CastSession(Team team, int slot, Location startLocation, BukkitTask task, int ticksRemaining) {
            this.team = team;
            this.slot = slot;
            this.startLocation = startLocation;
            this.task = task;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final Map<UUID, CastSession> activeCasts = new HashMap<>();

    public TeamPortalManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    public void usePortal(Player player, int slot) {
        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You aren't in a team.");
            return;
        }
        TeamPortal portal = team.getPortal(slot);
        if (portal == null) {
            player.sendMessage(ChatColor.RED + "Your team doesn't have a portal set in slot " + slot + ".");
            return;
        }
        if (portal.isOnCooldown(player.getUniqueId())) {
            long secs = portal.secondsRemaining(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "That portal is on cooldown for you for " + (secs / 60) + "m " + (secs % 60) + "s.");
            return;
        }
        Location destination = portal.toLocation();
        if (destination == null) {
            player.sendMessage(ChatColor.RED + "That portal's world isn't loaded right now.");
            return;
        }
        if (activeCasts.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You're already channeling a portal.");
            return;
        }

        int castSeconds = plugin.getConfig().getInt("portal-cast-seconds", 5);
        int cooldownMinutes = plugin.getConfig().getInt("portal-cooldown-minutes", 15);
        Location startLoc = player.getLocation().clone();

        player.sendMessage(ChatColor.YELLOW + "Channeling team portal " + slot + "... don't move for " + castSeconds + " seconds.");

        // A repeating 1-second tick rather than a single delayed task, so the
        // player gets a visible countdown instead of just silently waiting
        // out the channel with no feedback until it either completes or gets
        // cancelled by moving/taking damage.
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                CastSession session = activeCasts.get(player.getUniqueId());
                if (session == null) return;

                if (session.ticksRemaining <= 0) {
                    activeCasts.remove(player.getUniqueId());
                    session.task.cancel();
                    completePortal(player, team, slot);
                    return;
                }

                player.sendActionBar(Component.text(ChatColor.AQUA + "Teleporting in " + session.ticksRemaining + "..."));
                session.ticksRemaining--;
            }
        }, 0L, 20L);

        activeCasts.put(player.getUniqueId(), new CastSession(team, slot, startLoc, task, castSeconds));
    }

    private void completePortal(Player player, Team team, int slot) {
        if (!player.isOnline()) return;

        TeamPortal freshPortal = team.getPortal(slot);
        if (freshPortal == null) {
            player.sendMessage(ChatColor.RED + "The portal was removed before it activated.");
            return;
        }
        Location dest = freshPortal.toLocation();
        if (dest == null) {
            player.sendMessage(ChatColor.RED + "That portal's world isn't loaded right now.");
            return;
        }

        int cooldownMinutes = plugin.getConfig().getInt("portal-cooldown-minutes", 15);
        player.teleport(dest);
        player.sendMessage(ChatColor.GREEN + "Whoosh! Teleported to team portal " + slot + ".");
        freshPortal.startCooldown(player.getUniqueId(), cooldownMinutes);
        teamManager.saveTeam(team);
    }

    private void cancelCast(Player player, String reason) {
        CastSession session = activeCasts.remove(player.getUniqueId());
        if (session == null) return;
        session.task.cancel();
        player.sendMessage(ChatColor.RED + "Portal channel cancelled - " + reason);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        CastSession session = activeCasts.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        Location from = session.startLocation;
        Location to = event.getTo();
        if (to == null) return;
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > 0.09) { // ~0.3 blocks
            cancelCast(event.getPlayer(), "you moved.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (activeCasts.containsKey(player.getUniqueId())) {
            cancelCast(player, "you took damage.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Ignore the teleport our own cast performs; cancel for any other kind (ender pearl, plugin, etc).
        CastSession session = activeCasts.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        cancelCast(event.getPlayer(), "you teleported.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CastSession session = activeCasts.remove(event.getPlayer().getUniqueId());
        if (session != null) session.task.cancel();
    }
}
