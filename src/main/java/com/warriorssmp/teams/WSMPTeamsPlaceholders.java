package com.warriorssmp.teams;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

public class WSMPTeamsPlaceholders extends PlaceholderExpansion {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final TeamLevelManager levelManager;

    public WSMPTeamsPlaceholders(TeamsPlugin plugin, TeamManager teamManager, TeamLevelManager levelManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.levelManager = levelManager;
    }

    @Override
    public String getIdentifier() {
        return "wsmpteams";
    }

    @Override
    public String getAuthor() {
        return "WarriorsSMP";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // keep this expansion registered across /papi reload
    }

    /**
     * Available placeholders - built for dropping straight into TAB's config.yml
     * (tablist-name-formatting, nametag prefix/suffix, or chat-format if you use
     * TAB's chat module) rather than relying on our own scoreboard team, which
     * would fight TAB for control of the same slot.
     *
     * %wsmpteams_team%       -> team name, or "" if none
     * %wsmpteams_tag%        -> ready-made colored "[TeamName] " (or "" if none) - drop this straight in
     * %wsmpteams_tag_leveled% -> same, but "[TeamName Lv.5] " - also "" if none, safe for Discord/TAB alike
     * %wsmpteams_role%       -> "Leader" / "Manager" / "Member" / ""
     * %wsmpteams_level%      -> team level, or "0" if none
     * %wsmpteams_members%    -> current member count, or "0"
     * %wsmpteams_membercap%  -> current member cap, or "0"
     */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        Team team = teamManager.getTeam(player.getUniqueId());

        switch (params.toLowerCase()) {
            case "team":
                return team == null ? "" : team.getName();

            case "tag":
                if (team == null) return "";
                return ChatColor.translateAlternateColorCodes('&', "&a[" + team.getName() + "]&r ");

            case "tag_leveled":
                if (team == null) return "";
                return ChatColor.translateAlternateColorCodes('&', "&a[" + team.getName() + " Lv." + team.getLevel() + "]&r ");

            case "role":
                if (team == null) return "";
                if (team.isLeader(player.getUniqueId())) return "Leader";
                if (team.isManager(player.getUniqueId())) return "Manager";
                return "Member";

            case "level":
                return team == null ? "0" : String.valueOf(team.getLevel());

            case "members":
                return team == null ? "0" : String.valueOf(team.size());

            case "membercap":
                return team == null ? "0" : String.valueOf(levelManager.getMemberCap(team.getLevel()));

            default:
                return null; // let PlaceholderAPI know this placeholder isn't ours
        }
    }
}
