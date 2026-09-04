package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full admin override panel, gated entirely behind wsmpteams.admin. Every
 * action here calls the adminXxx() methods on TeamManager, which operate on
 * an explicit team/player rather than the caller's own membership - none of
 * the normal leader/officer rules apply once you're in here.
 */
public class TeamAdminGUI implements Listener {

    private static final String MAIN_TITLE = ChatColor.translateAlternateColorCodes('&', "&4&lWSMP Teams Admin");
    private static final String TEAMS_TITLE = ChatColor.translateAlternateColorCodes('&', "&4&lAdmin: Browse Teams");
    private static final String TEAM_EDIT_PREFIX = ChatColor.translateAlternateColorCodes('&', "&4&lEditing Team: ");
    private static final String PLAYERS_TITLE = ChatColor.translateAlternateColorCodes('&', "&4&lAdmin: Browse Players");
    private static final String PLAYER_EDIT_PREFIX = ChatColor.translateAlternateColorCodes('&', "&4&lEditing Player: ");

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    /** Admin UUID -> team ID currently open in the edit screen, so clicks
     *  in that screen know which team they're acting on. */
    private final Map<UUID, UUID> openTeamEdit = new HashMap<>();
    /** Admin UUID -> player UUID currently open in the player edit screen. */
    private final Map<UUID, UUID> openPlayerEdit = new HashMap<>();
    /** Admin UUID -> team ID awaiting a typed new name in chat. */
    private final Map<UUID, UUID> awaitingRenameInput = new HashMap<>();

    public TeamAdminGUI(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    public boolean isAwaitingRenameInput(UUID admin) {
        return awaitingRenameInput.containsKey(admin);
    }

    public void consumeRenameInput(Player admin, String newName) {
        UUID teamId = awaitingRenameInput.remove(admin.getUniqueId());
        if (teamId == null) return;
        Team team = teamManager.getTeamById(teamId);
        if (team == null) {
            admin.sendMessage(ChatColor.RED + "That team no longer exists.");
            return;
        }
        if (teamManager.adminRenameTeam(team, newName)) {
            admin.sendMessage(ChatColor.GREEN + "Renamed team to '" + newName + "'.");
        } else {
            admin.sendMessage(ChatColor.RED + "That name is already taken by another team.");
        }
        Bukkit.getScheduler().runTask(plugin, () -> openTeamEdit(admin, team));
    }

    // ---------------------------------------------------------------- main menu

    public void openMain(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 27, MAIN_TITLE);
        fillBorder(gui);

        gui.setItem(11, GuiUtil.namedItem(Material.NETHER_STAR, "&c&lBrowse Teams",
                "&7View and override any", "&7team on the server.", "", "&e&lClick to open!"));
        gui.setItem(15, GuiUtil.namedItem(Material.NAME_TAG, "&c&lBrowse Players",
                "&7Clear cooldowns or force", "&7a player out of their team.", "", "&e&lClick to open!"));

        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- team browser

    public void openTeamBrowser(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 54, TEAMS_TITLE);
        int slot = 0;
        for (Team team : teamManager.getAllTeams()) {
            if (slot >= 53) break;
            gui.setItem(slot++, GuiUtil.namedItem(Material.NETHER_STAR, "&c" + team.getName(),
                    "&7Level: &f" + team.getLevel(),
                    "&7Members: &f" + team.size(),
                    "&7Gold donated: &f" + String.format("%,.0f", team.getGoldDonated()),
                    "",
                    "&e&lClick to edit!"));
        }
        if (slot == 0) {
            gui.setItem(22, GuiUtil.namedItem(Material.BARRIER, "&7No teams exist yet.", ""));
        }
        gui.setItem(53, GuiUtil.namedItem(Material.ARROW, "&7Back", ""));
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- team edit

    public void openTeamEdit(Player admin, Team team) {
        openTeamEdit.put(admin.getUniqueId(), team.getId());
        Inventory gui = Bukkit.createInventory(null, 54, TEAM_EDIT_PREFIX + team.getName());
        fillBorder(gui);

        gui.setItem(4, GuiUtil.namedItem(Material.NETHER_STAR, "&c" + team.getName(),
                "&7Level: &f" + team.getLevel(),
                "&7Members: &f" + team.size(),
                "&7Gold donated: &f" + String.format("%,.0f", team.getGoldDonated())));

        gui.setItem(19, GuiUtil.namedItem(Material.EXPERIENCE_BOTTLE, "&a+5 Level", ""));
        gui.setItem(20, GuiUtil.namedItem(Material.EXPERIENCE_BOTTLE, "&a+1 Level", ""));
        gui.setItem(21, GuiUtil.namedItem(Material.GLASS_BOTTLE, "&c-1 Level", ""));
        gui.setItem(22, GuiUtil.namedItem(Material.GLASS_BOTTLE, "&c-5 Level", ""));

        gui.setItem(24, GuiUtil.namedItem(Material.GOLD_INGOT, "&a+10,000 Gold", ""));
        gui.setItem(25, GuiUtil.namedItem(Material.GOLD_NUGGET, "&c-10,000 Gold", ""));

        gui.setItem(28, GuiUtil.namedItem(Material.WRITABLE_BOOK, "&e&lRename Team",
                "&7Type the new name in chat", "&7after clicking this.", "", "&e&lClick to rename!"));

        gui.setItem(30, GuiUtil.namedItem(Material.ENDER_EYE, "&b&lClear Portal Cooldowns",
                "&7Resets every member's", "&7cooldown on every portal.", "", "&e&lClick to clear!"));

        gui.setItem(32, GuiUtil.namedItem(Material.ENDER_CHEST, "&5&lWipe Ender Chest",
                "&cPermanently empties the", "&cshared team storage.", "", "&e&lClick to wipe!"));

        gui.setItem(34, GuiUtil.namedItem(Material.TNT, "&4&lForce Disband",
                "&cDisbands this team entirely.", "&cCannot be undone.", "", "&e&lClick to disband!"));

        List<UUID> members = new ArrayList<>(team.getMembers());
        int[] memberSlots = {37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < members.size() && i < memberSlots.length; i++) {
            UUID member = members.get(i);
            String name = nameOf(member);
            String role = team.isLeader(member) ? "Leader" : team.isManager(member) ? "Manager" : "Member";
            List<String> lore = new ArrayList<>();
            lore.add("&7Role: &f" + role);
            lore.add("");
            if (team.isLeader(member) && team.size() == 1) {
                lore.add("&7Sole member - use Force");
                lore.add("&7Disband instead.");
            } else {
                lore.add("&e&lClick to remove from team!");
            }
            gui.setItem(memberSlots[i], GuiUtil.namedItem(Material.PLAYER_HEAD, "&f" + name, lore.toArray(new String[0])));
        }

        gui.setItem(49, GuiUtil.namedItem(Material.ARROW, "&7Back", ""));
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- player browser

    public void openPlayerBrowser(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 54, PLAYERS_TITLE);
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 53) break;
            Team team = teamManager.getTeam(online.getUniqueId());
            gui.setItem(slot++, GuiUtil.namedItem(Material.NAME_TAG, "&f" + online.getName(),
                    "&7Team: &f" + (team == null ? "none" : team.getName()),
                    "",
                    "&e&lClick to edit!"));
        }
        gui.setItem(53, GuiUtil.namedItem(Material.ARROW, "&7Back", ""));
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- player edit

    public void openPlayerEdit(Player admin, UUID targetUuid) {
        openPlayerEdit.put(admin.getUniqueId(), targetUuid);
        String targetName = nameOf(targetUuid);
        Team team = teamManager.getTeam(targetUuid);

        Inventory gui = Bukkit.createInventory(null, 27, PLAYER_EDIT_PREFIX + targetName);
        fillBorder(gui);

        gui.setItem(4, GuiUtil.namedItem(Material.NAME_TAG, "&f" + targetName,
                "&7Team: &f" + (team == null ? "none" : team.getName())));

        gui.setItem(11, GuiUtil.namedItem(Material.BARRIER, "&c&lForce Remove From Team",
                "&7Removes them from whichever", "&7team they're currently on.", "", "&e&lClick to remove!"));

        gui.setItem(13, GuiUtil.namedItem(Material.CLOCK, "&b&lClear Leave Lockout",
                "&7Lets them join or create", "&7a team again immediately.", "", "&e&lClick to clear!"));

        gui.setItem(15, GuiUtil.namedItem(Material.FIRE_CHARGE, "&6&lClear SOS Cooldown",
                "&7Lets them send another", "&7SOS flare immediately.", "", "&e&lClick to clear!"));

        gui.setItem(22, GuiUtil.namedItem(Material.ARROW, "&7Back", ""));
        admin.openInventory(gui);
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean ours = title.equals(MAIN_TITLE) || title.equals(TEAMS_TITLE) || title.startsWith(TEAM_EDIT_PREFIX)
                || title.equals(PLAYERS_TITLE) || title.startsWith(PLAYER_EDIT_PREFIX);
        if (!ours) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();
        if (!admin.hasPermission("wsmpteams.admin")) {
            admin.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = event.getRawSlot();

        if (title.equals(MAIN_TITLE)) {
            if (slot == 11) openTeamBrowser(admin);
            else if (slot == 15) openPlayerBrowser(admin);
            return;
        }

        if (title.equals(TEAMS_TITLE)) {
            if (slot == 53) {
                openMain(admin);
                return;
            }
            String teamName = stripColor(clicked);
            Team team = teamManager.getTeamByName(teamName);
            if (team != null) openTeamEdit(admin, team);
            return;
        }

        if (title.startsWith(TEAM_EDIT_PREFIX)) {
            handleTeamEditClick(admin, slot);
            return;
        }

        if (title.equals(PLAYERS_TITLE)) {
            if (slot == 53) {
                openMain(admin);
                return;
            }
            String targetName = stripColor(clicked);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            openPlayerEdit(admin, target.getUniqueId());
            return;
        }

        if (title.startsWith(PLAYER_EDIT_PREFIX)) {
            handlePlayerEditClick(admin, slot);
        }
    }

    private void handleTeamEditClick(Player admin, int slot) {
        UUID teamId = openTeamEdit.get(admin.getUniqueId());
        if (teamId == null) return;
        Team team = teamManager.getTeamById(teamId);
        if (team == null) {
            admin.closeInventory();
            return;
        }

        switch (slot) {
            case 19 -> { teamManager.adminSetLevel(team, team.getLevel() + 5); openTeamEdit(admin, team); }
            case 20 -> { teamManager.adminSetLevel(team, team.getLevel() + 1); openTeamEdit(admin, team); }
            case 21 -> { teamManager.adminSetLevel(team, team.getLevel() - 1); openTeamEdit(admin, team); }
            case 22 -> { teamManager.adminSetLevel(team, team.getLevel() - 5); openTeamEdit(admin, team); }
            case 24 -> { teamManager.adminAdjustGold(team, 10_000); openTeamEdit(admin, team); }
            case 25 -> { teamManager.adminAdjustGold(team, -10_000); openTeamEdit(admin, team); }
            case 28 -> {
                awaitingRenameInput.put(admin.getUniqueId(), team.getId());
                admin.closeInventory();
                admin.sendMessage(ChatColor.YELLOW + "Type the new team name in chat.");
            }
            case 30 -> {
                teamManager.adminClearAllPortalCooldowns(team);
                admin.sendMessage(ChatColor.GREEN + "Cleared every member's portal cooldowns.");
                openTeamEdit(admin, team);
            }
            case 32 -> {
                teamManager.adminWipeEnderChest(team);
                admin.sendMessage(ChatColor.GREEN + "Wiped the shared ender chest.");
                openTeamEdit(admin, team);
            }
            case 34 -> {
                teamManager.adminDisbandTeam(team);
                admin.sendMessage(ChatColor.GREEN + "Disbanded '" + team.getName() + "'.");
                openTeamBrowser(admin);
            }
            case 49 -> openTeamBrowser(admin);
            case 37, 38, 39, 40, 41, 42, 43 -> {
                List<UUID> members = new ArrayList<>(team.getMembers());
                int[] memberSlots = {37, 38, 39, 40, 41, 42, 43};
                int index = -1;
                for (int i = 0; i < memberSlots.length; i++) if (memberSlots[i] == slot) index = i;
                if (index >= 0 && index < members.size()) {
                    UUID target = members.get(index);
                    if (team.isLeader(target) && team.size() == 1) return; // use Force Disband instead
                    teamManager.adminKickMember(team, target);
                    admin.sendMessage(ChatColor.GREEN + "Removed " + nameOf(target) + " from the team.");
                    Team refreshed = teamManager.getTeamById(teamId);
                    if (refreshed != null) openTeamEdit(admin, refreshed);
                    else openTeamBrowser(admin);
                }
            }
            default -> {}
        }
    }

    private void handlePlayerEditClick(Player admin, int slot) {
        UUID targetUuid = openPlayerEdit.get(admin.getUniqueId());
        if (targetUuid == null) return;

        switch (slot) {
            case 11 -> {
                boolean removed = teamManager.adminForceRemoveFromTeam(targetUuid);
                admin.sendMessage(removed
                        ? ChatColor.GREEN + "Removed " + nameOf(targetUuid) + " from their team."
                        : ChatColor.YELLOW + nameOf(targetUuid) + " wasn't in a team.");
                openPlayerEdit(admin, targetUuid);
            }
            case 13 -> {
                teamManager.adminClearLeaveLockout(targetUuid);
                admin.sendMessage(ChatColor.GREEN + "Cleared their leave lockout.");
                openPlayerEdit(admin, targetUuid);
            }
            case 15 -> {
                teamManager.adminClearSosCooldown(targetUuid);
                admin.sendMessage(ChatColor.GREEN + "Cleared their SOS cooldown.");
                openPlayerEdit(admin, targetUuid);
            }
            case 22 -> openPlayerBrowser(admin);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- helpers

    private void fillBorder(Inventory gui) {
        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, border);
        }
    }

    private String stripColor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return "";
        return ChatColor.stripColor(meta.getDisplayName());
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }
}
