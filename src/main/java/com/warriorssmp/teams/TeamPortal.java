package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A single team portal waypoint. Each portal has its own independent cooldown -
 * it's a property of the portal itself, shared by the whole team, not per-player.
 */
public class TeamPortal {

    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private long cooldownEndMillis = 0L;

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

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownEndMillis;
    }

    public long secondsRemaining() {
        return Math.max(0, (cooldownEndMillis - System.currentTimeMillis()) / 1000);
    }

    public void startCooldown(long minutes) {
        this.cooldownEndMillis = System.currentTimeMillis() + (minutes * 60_000L);
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

    public long getCooldownEndMillis() {
        return cooldownEndMillis;
    }

    public void setCooldownEndMillis(long millis) {
        this.cooldownEndMillis = millis;
    }
}
