package com.warriorssmp.teams;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Team {

    private final UUID id;
    private String name;
    private UUID leader;
    private UUID manager; // nullable - a team has at most one manager

    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> invited = new LinkedHashSet<>();
    private final Map<UUID, String> applications = new LinkedHashMap<>(); // applicant -> their message

    private double goldDonated = 0;
    private int level = 1;

    private final TeamPortal[] portals = new TeamPortal[3]; // index 0-2 = portal slot 1-3

    private String enderChestBase64 = null; // serialized shared inventory contents, see TeamManager

    private final long createdAt;

    public Team(UUID id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public UUID getManager() {
        return manager;
    }

    public void setManager(UUID manager) {
        this.manager = manager;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean isManager(UUID uuid) {
        return manager != null && manager.equals(uuid);
    }

    public boolean isOfficer(UUID uuid) {
        return isLeader(uuid) || isManager(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public Set<UUID> getInvited() {
        return invited;
    }

    public boolean isInvited(UUID uuid) {
        return invited.contains(uuid);
    }

    public Map<UUID, String> getApplications() {
        return applications;
    }

    public double getGoldDonated() {
        return goldDonated;
    }

    public void addGold(double amount) {
        this.goldDonated += amount;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public TeamPortal[] getPortals() {
        return portals;
    }

    public TeamPortal getPortal(int slot1to3) {
        if (slot1to3 < 1 || slot1to3 > 3) return null;
        return portals[slot1to3 - 1];
    }

    public void setPortal(int slot1to3, TeamPortal portal) {
        if (slot1to3 < 1 || slot1to3 > 3) return;
        portals[slot1to3 - 1] = portal;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getEnderChestBase64() {
        return enderChestBase64;
    }

    public void setEnderChestBase64(String enderChestBase64) {
        this.enderChestBase64 = enderChestBase64;
    }
}
