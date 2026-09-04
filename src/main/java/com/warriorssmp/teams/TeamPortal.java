package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single team portal waypoint. Each portal's cooldown is now tracked
 * per-player rather than shared by the whole team - every member gets
 * their own independent cooldown timer for using this specific portal,
 * so one member using it doesn't lock everyone else out.
 */
public class TeamPortal {

    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final Map<UUID, Long> cooldownEndsByPlayer = new HashMap<>();

    public TeamPortal(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static TeamPortal fromLocation(Location loc) {
        return new TeamPortal(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isOnCooldown(UUID player) {
        Long end = cooldownEndsByPlayer.get(player);
        return end != null && System.currentTimeMillis() < end;
    }

    public long secondsRemaining(UUID player) {
        Long end = cooldownEndsByPlayer.get(player);
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void startCooldown(UUID player, long minutes) {
        cooldownEndsByPlayer.put(player, System.currentTimeMillis() + (minutes * 60_000L));
    }

    public Map<UUID, Long> getCooldownEndsByPlayer() {
        return cooldownEndsByPlayer;
    }

    public String getWorldName() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
