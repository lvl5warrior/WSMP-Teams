package com.warriorssmp.teams;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamManager {

    private final TeamsPlugin plugin;
    private final TeamLevelManager levelManager;
    private final File teamsFolder;
    private final File playersFile;

    private final Map<UUID, Team> teamsById = new HashMap<>();
    private final Map<String, UUID> nameToId = new HashMap<>(); // lowercase name -> id
    private final Map<UUID, UUID> playerToTeam = new HashMap<>();
    private final Map<UUID, Long> leaveCooldownEnds = new HashMap<>();
    private final Map<UUID, Long> pendingLeaveConfirm = new HashMap<>();
    private final Map<UUID, Inventory> enderChestCache = new HashMap<>(); // teamId -> live shared inventory

    /** Players who clicked "Invite" in the GUI and need to type a name next in chat. */
    private final Map<UUID, Boolean> awaitingInviteInput = new HashMap<>();

    // If TAB is installed, it manages its own scoreboard teams for tablist/nametag sorting.
    // Registering our own scoreboard team on top of that fights TAB for the same slot and
    // one of us silently loses. So when TAB is present we skip our scoreboard team entirely
    // and rely on the %wsmpteams_team% / %wsmpteams_tag% / %wsmpteams_role% placeholders
    // (see WSMPTeamsPlaceholders) that you can drop into TAB's own config instead.
    private final boolean tabPluginPresent;

    public TeamManager(TeamsPlugin plugin, TeamLevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
        this.tabPluginPresent = plugin.getServer().getPluginManager().getPlugin("TAB") != null;
        this.teamsFolder = new File(plugin.getDataFolder(), "teams");
        if (!teamsFolder.exists()) teamsFolder.mkdirs();
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        loadAll();
    }

    // ---------------------------------------------------------------- lookups

    public Team getTeam(UUID playerUuid) {
        UUID teamId = playerToTeam.get(playerUuid);
        return teamId == null ? null : teamsById.get(teamId);
    }

    public Team getTeamByName(String name) {
        UUID id = nameToId.get(name.toLowerCase());
        return id == null ? null : teamsById.get(id);
    }

    public boolean isInTeam(UUID playerUuid) {
        return playerToTeam.containsKey(playerUuid);
    }

    public Collection<Team> getAllTeams() {
        return teamsById.values();
    }

    public int getEnderChestSize() {
        int size = plugin.getConfig().getInt("enderchest-size", 54);
        size = (size / 9) * 9;
        return Math.max(9, Math.min(54, size));
    }

    // ---------------------------------------------------------------- create / disband

    public void createTeam(Player leader, String rawName) {
        if (isInTeam(leader.getUniqueId())) {
            leader.sendMessage(err("You're already in a team. Leave it first with /team leave."));
            return;
        }
        if (isOnLeaveCooldown(leader.getUniqueId())) {
            leader.sendMessage(err("You must wait " + formatRemainingLockout(leader.getUniqueId()) + " before joining or creating another team."));
            return;
        }
        String name = rawName.trim();
        if (name.length() < 3 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            leader.sendMessage(err("Team names must be 3-16 characters, letters/numbers/underscores only."));
            return;
        }
        if (nameToId.containsKey(name.toLowerCase())) {
            leader.sendMessage(err("A team named '" + name + "' already exists."));
            return;
        }

        Team team = new Team(UUID.randomUUID(), name, leader.getUniqueId());
        teamsById.put(team.getId(), team);
        nameToId.put(name.toLowerCase(), team.getId());
        playerToTeam.put(leader.getUniqueId(), team.getId());

        ensureScoreboardTeam(team);
        addPlayerToScoreboard(team, leader);

        leader.sendMessage(ok("Team '" + name + "' created! You are the leader."));
        saveTeam(team);
        savePlayers();
    }

    public void disbandTeam(Player actor) {
        Team team = getTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isLeader(actor.getUniqueId()) && !actor.hasPermission("wsmpteams.admin")) {
            actor.sendMessage(err("Only the team leader can disband the team."));
            return;
        }

        for (UUID member : new ArrayList<>(team.getMembers())) {
            playerToTeam.remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(err("Your team '" + team.getName() + "' has been disbanded."));
                removePlayerFromScoreboard(online);
            }
        }
        deleteScoreboardTeam(team);
        enderChestCache.remove(team.getId());
        nameToId.remove(team.getName().toLowerCase());
        teamsById.remove(team.getId());

        File file = teamFile(team.getId());
        if (file.exists()) file.delete();
        savePlayers();
    }

    // ---------------------------------------------------------------- invites

    public void invitePlayer(Player actor, String targetName) {
        Team team = getTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isOfficer(actor.getUniqueId())) {
            actor.sendMessage(err("Only the leader or manager can invite players."));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            actor.sendMessage(err("Player '" + targetName + "' isn't online."));
            return;
        }
        if (isInTeam(target.getUniqueId())) {
            actor.sendMessage(err(target.getName() + " is already in a team."));
            return;
        }
        int cap = levelManager.getMemberCap(team.getLevel());
        if (team.size() >= cap) {
            actor.sendMessage(err("Your team is full (" + cap + " members). Level up to raise the cap."));
            return;
        }
        if (team.isInvited(target.getUniqueId())) {
            actor.sendMessage(err(target.getName() + " already has a pending invite."));
            return;
        }

        team.getInvited().add(target.getUniqueId());
        actor.sendMessage(ok("Invited " + target.getName() + " to '" + team.getName() + "'."));
        target.sendMessage(ok("You've been invited to join team '" + team.getName() + "'! Type /team acceptinvite "
                + team.getName() + " to join, or /team declineinvite " + team.getName() + " to decline."));
        saveTeam(team);
    }

    public void acceptInvite(Player player, String teamName) {
        if (isInTeam(player.getUniqueId())) {
            player.sendMessage(err("You're already in a team."));
            return;
        }
        if (isOnLeaveCooldown(player.getUniqueId())) {
            player.sendMessage(err("You must wait " + formatRemainingLockout(player.getUniqueId()) + " before joining another team."));
            return;
        }
        Team team = getTeamByName(teamName);
        if (team == null || !team.isInvited(player.getUniqueId())) {
            player.sendMessage(err("You don't have a pending invite from that team."));
            return;
        }
        int cap = levelManager.getMemberCap(team.getLevel());
        if (team.size() >= cap) {
            player.sendMessage(err("That team is full now."));
            return;
        }

        team.getInvited().remove(player.getUniqueId());
        team.getMembers().add(player.getUniqueId());
        playerToTeam.put(player.getUniqueId(), team.getId());
        addPlayerToScoreboard(team, player);

        player.sendMessage(ok("You joined '" + team.getName() + "'!"));
        broadcastToTeam(team, ok(player.getName() + " has joined the team."), player.getUniqueId());
        saveTeam(team);
        savePlayers();
    }

    public void declineInvite(Player player, String teamName) {
        Team team = getTeamByName(teamName);
        if (team == null || !team.isInvited(player.getUniqueId())) {
            player.sendMessage(err("You don't have a pending invite from that team."));
            return;
        }
        team.getInvited().remove(player.getUniqueId());
        player.sendMessage(ok("Declined the invite to '" + team.getName() + "'."));
        saveTeam(team);
    }

    // ---------------------------------------------------------------- applications

    public void applyToJoin(Player player, String teamName, String message) {
        if (isInTeam(player.getUniqueId())) {
            player.sendMessage(err("You're already in a team."));
            return;
        }
        if (isOnLeaveCooldown(player.getUniqueId())) {
            player.sendMessage(err("You must wait " + formatRemainingLockout(player.getUniqueId()) + " before joining another team."));
            return;
        }
        Team team = getTeamByName(teamName);
        if (team == null) {
            player.sendMessage(err("No team named '" + teamName + "' exists."));
            return;
        }
        team.getApplications().put(player.getUniqueId(), message);
        player.sendMessage(ok("Application sent to '" + team.getName() + "'."));

        String notify = ChatColor.YELLOW + player.getName() + " applied to join your team: " + ChatColor.GRAY + "\"" + message + "\"";
        Player leaderOnline = Bukkit.getPlayer(team.getLeader());
        if (leaderOnline != null) leaderOnline.sendMessage(notify);
        if (team.getManager() != null) {
            Player managerOnline = Bukkit.getPlayer(team.getManager());
            if (managerOnline != null) managerOnline.sendMessage(notify);
        }
        saveTeam(team);
    }

    public void acceptApplication(Player actor, String applicantName) {
        Team team = getTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isOfficer(actor.getUniqueId())) {
            actor.sendMessage(err("Only the leader or manager can accept applications."));
            return;
        }
        OfflinePlayer applicant = resolvePlayer(applicantName);
        if (applicant == null || !team.getApplications().containsKey(applicant.getUniqueId())) {
            actor.sendMessage(err("No pending application from that player."));
            return;
        }
        if (isInTeam(applicant.getUniqueId())) {
            team.getApplications().remove(applicant.getUniqueId());
            actor.sendMessage(err(applicantName + " already joined a different team."));
            return;
        }
        int cap = levelManager.getMemberCap(team.getLevel());
        if (team.size() >= cap) {
            actor.sendMessage(err("Your team is full (" + cap + " members)."));
            return;
        }

        team.getApplications().remove(applicant.getUniqueId());
        team.getMembers().add(applicant.getUniqueId());
        playerToTeam.put(applicant.getUniqueId(), team.getId());

        Player onlineApplicant = applicant.getPlayer();
        if (onlineApplicant != null) {
            addPlayerToScoreboard(team, onlineApplicant);
            onlineApplicant.sendMessage(ok("Your application to '" + team.getName() + "' was accepted!"));
        }
        actor.sendMessage(ok("Accepted " + applicantName + " into the team."));
        broadcastToTeam(team, ok(applicantName + " has joined the team."), applicant.getUniqueId());
        saveTeam(team);
        savePlayers();
    }

    public void denyApplication(Player actor, String applicantName) {
        Team team = getTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isOfficer(actor.getUniqueId())) {
            actor.sendMessage(err("Only the leader or manager can deny applications."));
            return;
        }
        OfflinePlayer applicant = resolvePlayer(applicantName);
        if (applicant == null || !team.getApplications().containsKey(applicant.getUniqueId())) {
            actor.sendMessage(err("No pending application from that player."));
            return;
        }
        team.getApplications().remove(applicant.getUniqueId());
        actor.sendMessage(ok("Denied " + applicantName + "'s application."));
        Player online = applicant.getPlayer();
        if (online != null) online.sendMessage(err("Your application to '" + team.getName() + "' was denied."));
        saveTeam(team);
    }

    // ---------------------------------------------------------------- kick / promote / demote

    public void kickPlayer(Player actor, String targetName) {
        Team team = getTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(err("You aren't in a team."));
            return;
        }
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null || !team.isMember(target.getUniqueId())) {
            actor.sendMessage(err(targetName + " isn't in your team."));
            return;
        }
        if (team.isLeader(target.getUniqueId())) {
            actor.sendMessage(err("You can't kick the team leader."));
            return;
        }
        boolean actorIsLeader = team.isLeader(actor.getUniqueId());
        boolean actorIsManager = team.isManager(actor.getUniqueId());
        if (!actorIsLeader && !actorIsManager) {
            actor.sendMessage(err("Only the leader or manager can kick players."));
            return;
        }
        // A manager can't kick another manager - only the leader outranks a manager.
        if (actorIsManager && team.isManager(target.getUniqueId())) {
            actor.sendMessage(err("Only the leader can remove the manager."));
            return;
        }

        removeMember(team, target.getUniqueId());
        actor.sendMessage(ok("Kicked " + targetName + " from the team."));
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(err("You were kicked from '" + team.getName() + "'."));
            removePlayerFromScoreboard(onlineTarget);
        }
        broadcastToTeam(team, err(targetName + " was kicked from the team."), null);
        saveTeam(team);
        savePlayers();
    }

    public void promote(Player leader, String targetName) {
        Team team = getTeam(leader.getUniqueId());
        if (team == null) {
            leader.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(err("Only the leader can promote a manager."));
            return;
        }
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null || !team.isMember(target.getUniqueId())) {
            leader.sendMessage(err(targetName + " isn't in your team."));
            return;
        }
        if (team.isLeader(target.getUniqueId())) {
            leader.sendMessage(err("They're already the leader."));
            return;
        }
        team.setManager(target.getUniqueId());
        leader.sendMessage(ok(targetName + " is now the team manager."));
        broadcastToTeam(team, ok(targetName + " was promoted to manager."), leader.getUniqueId());
        saveTeam(team);
    }

    public void demote(Player leader) {
        Team team = getTeam(leader.getUniqueId());
        if (team == null) {
            leader.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(err("Only the leader can remove the manager."));
            return;
        }
        if (team.getManager() == null) {
            leader.sendMessage(err("Your team doesn't have a manager."));
            return;
        }
        team.setManager(null);
        leader.sendMessage(ok("Manager role removed."));
        saveTeam(team);
    }

    // ---------------------------------------------------------------- leave (with confirmation)

    public void requestLeave(Player player) {
        Team team = getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(err("You aren't in a team."));
            return;
        }
        int windowSeconds = plugin.getConfig().getInt("leave-confirmation-window-seconds", 30);
        int lockoutDays = plugin.getConfig().getInt("leave-lockout-days", 3);
        pendingLeaveConfirm.put(player.getUniqueId(), System.currentTimeMillis() + windowSeconds * 1000L);

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Leaving '" + team.getName() + "'?");
        player.sendMessage(ChatColor.YELLOW + "You'll lose access to the shared enderchest and won't be able to "
                + "join or create another team for " + lockoutDays + " days.");
        player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/team leaveconfirm" + ChatColor.YELLOW
                + " within " + windowSeconds + " seconds to confirm.");
    }

    public void confirmLeave(Player player) {
        Long expiry = pendingLeaveConfirm.remove(player.getUniqueId());
        if (expiry == null || System.currentTimeMillis() > expiry) {
            player.sendMessage(err("You don't have a pending leave request. Use /team leave first."));
            return;
        }
        Team team = getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(err("You aren't in a team."));
            return;
        }

        boolean wasLeader = team.isLeader(player.getUniqueId());
        boolean wasManager = team.isManager(player.getUniqueId());
        removeMember(team, player.getUniqueId());
        removePlayerFromScoreboard(player);

        int lockoutDays = plugin.getConfig().getInt("leave-lockout-days", 3);
        leaveCooldownEnds.put(player.getUniqueId(), System.currentTimeMillis() + lockoutDays * 86_400_000L);

        player.sendMessage(ok("You left '" + team.getName() + "'."));

        if (wasManager) {
            team.setManager(null);
        }

        if (wasLeader) {
            if (team.getMembers().isEmpty()) {
                // No one left - disband automatically.
                deleteScoreboardTeam(team);
                enderChestCache.remove(team.getId());
                nameToId.remove(team.getName().toLowerCase());
                teamsById.remove(team.getId());
                File file = teamFile(team.getId());
                if (file.exists()) file.delete();
                savePlayers();
                return;
            }
            UUID successor = team.getManager() != null ? team.getManager() : team.getMembers().iterator().next();
            team.setLeader(successor);
            team.setManager(null);
            broadcastToTeam(team, ok(nameOf(successor) + " is now the team leader."), null);
        } else {
            broadcastToTeam(team, err(player.getName() + " has left the team."), null);
        }

        saveTeam(team);
        savePlayers();
    }

    private void removeMember(Team team, UUID uuid) {
        team.getMembers().remove(uuid);
        playerToTeam.remove(uuid);
        if (team.isManager(uuid)) team.setManager(null);
    }

    public boolean isOnLeaveCooldown(UUID uuid) {
        Long end = leaveCooldownEnds.get(uuid);
        return end != null && System.currentTimeMillis() < end;
    }

    public String formatRemainingLockout(UUID uuid) {
        Long end = leaveCooldownEnds.get(uuid);
        if (end == null) return "0 days";
        long millisLeft = Math.max(0, end - System.currentTimeMillis());
        long hours = millisLeft / 3_600_000L;
        long days = hours / 24;
        long remHours = hours % 24;
        if (days > 0) return days + "d " + remHours + "h";
        return remHours + "h";
    }

    // ---------------------------------------------------------------- gold / leveling

    public void donateGold(Player player, double amount) {
        Team team = getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(err("You aren't in a team."));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(err("Enter an amount greater than 0."));
            return;
        }
        Economy econ = plugin.getEconomy();
        if (econ.getBalance(player) < amount) {
            player.sendMessage(err("You don't have that much gold."));
            return;
        }

        econ.withdrawPlayer(player, amount);
        int oldLevel = team.getLevel();
        team.addGold(amount);
        int newLevel = levelManager.levelForGold(team.getGoldDonated());
        team.setLevel(newLevel);

        player.sendMessage(ok("Donated $" + String.format("%,.2f", amount) + " to '" + team.getName() + "'."));
        broadcastToTeam(team, ChatColor.GOLD + player.getName() + " donated $" + String.format("%,.2f", amount)
                + " to the team!", player.getUniqueId());

        if (newLevel > oldLevel) {
            broadcastToTeam(team, ChatColor.GOLD + "" + ChatColor.BOLD + "Team level up! " + ChatColor.YELLOW
                    + "'" + team.getName() + "' is now level " + newLevel + ".", null);

            int oldCap = levelManager.getMemberCap(oldLevel);
            int newCap = levelManager.getMemberCap(newLevel);
            if (newCap > oldCap) {
                broadcastToTeam(team, ChatColor.AQUA + "Team size cap increased to " + newCap + "!", null);
            }
            int oldPorts = levelManager.getMaxPortals(oldLevel);
            int newPorts = levelManager.getMaxPortals(newLevel);
            if (newPorts > oldPorts) {
                broadcastToTeam(team, ChatColor.LIGHT_PURPLE + "A new team portal slot has unlocked! ("
                        + newPorts + "/" + levelManager.getMaxPortals(TeamLevelManager.MAX_LEVEL) + ")", null);
            }
        }

        saveTeam(team);
    }

    // ---------------------------------------------------------------- portals

    public void setPortal(Player leader, int slot, Location loc) {
        Team team = getTeam(leader.getUniqueId());
        if (team == null) {
            leader.sendMessage(err("You aren't in a team."));
            return;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(err("Only the leader can set a team portal."));
            return;
        }
        int maxPortals = levelManager.getMaxPortals(team.getLevel());
        if (slot < 1 || slot > maxPortals) {
            leader.sendMessage(err("Your team can have at most " + maxPortals + " portal(s) at level "
                    + team.getLevel() + ". Level up to unlock more."));
            return;
        }
        team.setPortal(slot, TeamPortal.fromLocation(loc));
        leader.sendMessage(ok("Team portal " + slot + " set to your current location."));
        saveTeam(team);
    }

    // ---------------------------------------------------------------- scoreboard / chat prefix

    void ensureScoreboardTeam(Team team) {
        if (tabPluginPresent) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String sbName = scoreboardTeamName(team);
        org.bukkit.scoreboard.Team sbTeam = board.getTeam(sbName);
        if (sbTeam == null) {
            sbTeam = board.registerNewTeam(sbName);
        }
        sbTeam.setPrefix(ChatColor.translateAlternateColorCodes('&', "&a[" + team.getName() + "]&r "));
        sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);
        sbTeam.setAllowFriendlyFire(false);
    }

    void addPlayerToScoreboard(Team team, Player player) {
        if (tabPluginPresent) return;
        if (!plugin.getConfig().getBoolean("tablist-prefix-enabled", true)) return;
        ensureScoreboardTeam(team);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team sbTeam = board.getTeam(scoreboardTeamName(team));
        if (sbTeam != null) sbTeam.addEntry(player.getName());
    }

    void removePlayerFromScoreboard(Player player) {
        if (tabPluginPresent) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team sbTeam : board.getTeams()) {
            if (sbTeam.getName().startsWith("wsmp_") && sbTeam.hasEntry(player.getName())) {
                sbTeam.removeEntry(player.getName());
            }
        }
    }

    void deleteScoreboardTeam(Team team) {
        if (tabPluginPresent) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team sbTeam = board.getTeam(scoreboardTeamName(team));
        if (sbTeam != null) sbTeam.unregister();
    }

    public boolean isTabPluginPresent() {
        return tabPluginPresent;
    }

    private String scoreboardTeamName(Team team) {
        // Scoreboard team names are capped at 16 chars in older versions - keep it short and unique.
        String shortId = team.getId().toString().replace("-", "").substring(0, 10);
        return "wsmp_" + shortId;
    }

    /** Rebuilds scoreboard entries for a player who just joined the server. */
    public void onPlayerJoin(Player player) {
        Team team = getTeam(player.getUniqueId());
        if (team != null) {
            addPlayerToScoreboard(team, player);
        }
    }

    public void onPlayerQuit(Player player) {
        // Membership persists - just clear any in-progress leave confirmation / invite input.
        pendingLeaveConfirm.remove(player.getUniqueId());
        awaitingInviteInput.remove(player.getUniqueId());
    }

    // ---------------------------------------------------------------- pending chat-input (GUI invite flow)

    public void beginInviteChatInput(Player actor) {
        awaitingInviteInput.put(actor.getUniqueId(), true);
    }

    public boolean isAwaitingInviteInput(UUID uuid) {
        return awaitingInviteInput.containsKey(uuid);
    }

    public void consumeInviteChatInput(Player actor, String typedName) {
        awaitingInviteInput.remove(actor.getUniqueId());
        invitePlayer(actor, typedName);
    }

    // ---------------------------------------------------------------- ender chest

    public Inventory getEnderChest(Team team) {
        return enderChestCache.computeIfAbsent(team.getId(), id -> {
            Inventory inv = Bukkit.createInventory(null, getEnderChestSize(),
                    ChatColor.translateAlternateColorCodes('&', "&4&lWSMP &8» &7" + team.getName() + " Enderchest"));
            ItemStack[] contents = deserializeItems(team.getEnderChestBase64());
            if (contents != null) {
                inv.setContents(trimOrPad(contents, inv.getSize()));
            }
            return inv;
        });
    }

    public void flushEnderChest(Team team) {
        Inventory inv = enderChestCache.get(team.getId());
        if (inv == null) return;
        team.setEnderChestBase64(serializeItems(inv.getContents()));
    }

    /** Finds which team owns a currently-open shared enderchest inventory, if any. */
    public Team findTeamByEnderChestInventory(Inventory inv) {
        for (Map.Entry<UUID, Inventory> entry : enderChestCache.entrySet()) {
            if (entry.getValue().equals(inv)) {
                return teamsById.get(entry.getKey());
            }
        }
        return null;
    }

    private ItemStack[] trimOrPad(ItemStack[] contents, int size) {
        if (contents.length == size) return contents;
        ItemStack[] resized = new ItemStack[size];
        System.arraycopy(contents, 0, resized, 0, Math.min(contents.length, size));
        return resized;
    }

    private String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOut = new BukkitObjectOutputStream(byteOut);
            dataOut.writeInt(items.length);
            for (ItemStack item : items) {
                dataOut.writeObject(item);
            }
            dataOut.close();
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to serialize enderchest contents: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] deserializeItems(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            ByteArrayInputStream byteIn = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
            BukkitObjectInputStream dataIn = new BukkitObjectInputStream(byteIn);
            int length = dataIn.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataIn.readObject();
            }
            dataIn.close();
            return items;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize enderchest contents: " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- misc helpers

    public void broadcastToTeam(Team team, String message, UUID excluding) {
        for (UUID uuid : team.getMembers()) {
            if (excluding != null && excluding.equals(uuid)) continue;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.sendMessage(message);
        }
    }

    public String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString();
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        // Fall back to any offline player the server has seen before with this exact name.
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    private String ok(String message) {
        return ChatColor.GREEN + message;
    }

    private String err(String message) {
        return ChatColor.RED + message;
    }

    // ---------------------------------------------------------------- persistence

    private File teamFile(UUID id) {
        return new File(teamsFolder, id.toString() + ".yml");
    }

    public void saveTeam(Team team) {
        flushEnderChest(team);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", team.getId().toString());
        yaml.set("name", team.getName());
        yaml.set("leader", team.getLeader().toString());
        yaml.set("manager", team.getManager() != null ? team.getManager().toString() : null);
        yaml.set("goldDonated", team.getGoldDonated());
        yaml.set("level", team.getLevel());
        yaml.set("createdAt", team.getCreatedAt());
        yaml.set("enderChest", team.getEnderChestBase64());

        List<String> memberStrings = new ArrayList<>();
        for (UUID m : team.getMembers()) memberStrings.add(m.toString());
        yaml.set("members", memberStrings);

        List<String> invitedStrings = new ArrayList<>();
        for (UUID i : team.getInvited()) invitedStrings.add(i.toString());
        yaml.set("invited", invitedStrings);

        for (Map.Entry<UUID, String> app : team.getApplications().entrySet()) {
            yaml.set("applications." + app.getKey(), app.getValue());
        }

        for (int slot = 1; slot <= 3; slot++) {
            TeamPortal portal = team.getPortal(slot);
            if (portal == null) continue;
            String base = "portals.slot" + slot;
            yaml.set(base + ".world", portal.getWorldName());
            yaml.set(base + ".x", portal.getX());
            yaml.set(base + ".y", portal.getY());
            yaml.set(base + ".z", portal.getZ());
            yaml.set(base + ".yaw", portal.getYaw());
            yaml.set(base + ".pitch", portal.getPitch());
            yaml.set(base + ".cooldownEnd", portal.getCooldownEndMillis());
        }

        try {
            yaml.save(teamFile(team.getId()));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save team " + team.getName() + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Team team : teamsById.values()) {
            saveTeam(team);
        }
        savePlayers();
    }

    private void savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : leaveCooldownEnds.entrySet()) {
            yaml.set("leaveCooldowns." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player index: " + e.getMessage());
        }
    }

    private void loadAll() {
        File[] files = teamsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                loadTeamFile(file);
            }
        }
        if (playersFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
            if (yaml.isConfigurationSection("leaveCooldowns")) {
                for (String key : yaml.getConfigurationSection("leaveCooldowns").getKeys(false)) {
                    try {
                        leaveCooldownEnds.put(UUID.fromString(key), yaml.getLong("leaveCooldowns." + key));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private void loadTeamFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            UUID id = UUID.fromString(yaml.getString("id"));
            String name = yaml.getString("name");
            UUID leader = UUID.fromString(yaml.getString("leader"));

            Team team = new Team(id, name, leader);
            team.getMembers().clear(); // constructor auto-adds leader; rebuild fully from file
            for (String m : yaml.getStringList("members")) {
                team.getMembers().add(UUID.fromString(m));
            }
            String managerStr = yaml.getString("manager");
            if (managerStr != null) team.setManager(UUID.fromString(managerStr));

            team.addGold(yaml.getDouble("goldDonated", 0));
            team.setLevel(yaml.getInt("level", 1));
            team.setEnderChestBase64(yaml.getString("enderChest"));

            for (String i : yaml.getStringList("invited")) {
                team.getInvited().add(UUID.fromString(i));
            }
            if (yaml.isConfigurationSection("applications")) {
                for (String key : yaml.getConfigurationSection("applications").getKeys(false)) {
                    team.getApplications().put(UUID.fromString(key), yaml.getString("applications." + key));
                }
            }
            for (int slot = 1; slot <= 3; slot++) {
                String base = "portals.slot" + slot;
                if (!yaml.isConfigurationSection("portals") || !yaml.isConfigurationSection(base)) continue;
                TeamPortal portal = new TeamPortal(
                        yaml.getString(base + ".world"),
                        yaml.getDouble(base + ".x"),
                        yaml.getDouble(base + ".y"),
                        yaml.getDouble(base + ".z"),
                        (float) yaml.getDouble(base + ".yaw"),
                        (float) yaml.getDouble(base + ".pitch")
                );
                portal.setCooldownEndMillis(yaml.getLong(base + ".cooldownEnd", 0));
                team.setPortal(slot, portal);
            }

            teamsById.put(team.getId(), team);
            nameToId.put(team.getName().toLowerCase(), team.getId());
            for (UUID member : team.getMembers()) {
                playerToTeam.put(member, team.getId());
            }
            ensureScoreboardTeam(team);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load team file " + file.getName() + ": " + e.getMessage());
        }
    }
}
