package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TeamGUI implements Listener {

    private static final String TITLE_TEAM = ChatColor.translateAlternateColorCodes('&',
            "&4&lWarriors &6&lSMP &8» &7Team");
    private static final String TITLE_NO_TEAM = ChatColor.translateAlternateColorCodes('&',
            "&4&lWarriors &6&lSMP &8» &7No Team");

    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int INFO_BOOK_SLOT = 4;

    private static final int INFO_SLOT = 13;
    private static final int MEMBERS_SLOT = 19;
    private static final int INVITE_SLOT = 20;
    private static final int APPLICATIONS_SLOT = 21;
    private static final int ENDERCHEST_SLOT = 22;
    private static final int PORTALS_SLOT = 23;
    private static final int DONATE_SLOT = 24;
    private static final int PROMOTE_SLOT = 29;
    private static final int LEAVE_SLOT = 31;
    private static final int DISBAND_SLOT = 33;

    private static final int CREATE_SLOT = 22;

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final TeamLevelManager levelManager;
    private final TeamInfoBook infoBook;

    public TeamGUI(TeamsPlugin plugin, TeamManager teamManager, TeamLevelManager levelManager, TeamInfoBook infoBook) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.levelManager = levelManager;
        this.infoBook = infoBook;
    }

    public void open(Player player) {
        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            openNoTeam(player);
        } else {
            openTeam(player, team);
        }
    }

    private void openNoTeam(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, TITLE_NO_TEAM);
        applyBorder(gui);

        gui.setItem(INFO_BOOK_SLOT, GuiUtil.namedItem(Material.WRITTEN_BOOK, "&e&lInfo Book",
                "&7Everything you need to",
                "&7know about teams.",
                "",
                "&e&lClick for a copy!"));

        gui.setItem(CREATE_SLOT, GuiUtil.namedItem(Material.NETHER_STAR, "&a&lCreate a Team",
                "&7Type &f/team create <name>",
                "&7to start your own team."));

        List<String> lockoutLore = new ArrayList<>();
        if (teamManager.isOnLeaveCooldown(player.getUniqueId())) {
            lockoutLore.add("&cYou must wait " + teamManager.formatRemainingLockout(player.getUniqueId()));
            lockoutLore.add("&cbefore joining or creating a team.");
        } else {
            lockoutLore.add("&7Type &f/team apply <team> <message>");
            lockoutLore.add("&7to apply to an existing team.");
        }
        gui.setItem(24, GuiUtil.namedItem(Material.WRITABLE_BOOK, "&e&lApply to a Team", lockoutLore.toArray(new String[0])));

        player.openInventory(gui);
    }

    private void openTeam(Player player, Team team) {
        Inventory gui = Bukkit.createInventory(null, 45, TITLE_TEAM);
        applyBorder(gui);

        gui.setItem(INFO_BOOK_SLOT, GuiUtil.namedItem(Material.WRITTEN_BOOK, "&e&lInfo Book",
                "&7Everything you need to",
                "&7know about teams.",
                "",
                "&e&lClick for a copy!"));

        boolean isLeader = team.isLeader(player.getUniqueId());
        boolean isOfficer = team.isOfficer(player.getUniqueId());
        int cap = levelManager.getMemberCap(team.getLevel());
        int maxPorts = levelManager.getMaxPortals(team.getLevel());
        long nextLevelGold = levelManager.goldNeededForNextLevel(team.getGoldDonated());

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7Level: &6" + team.getLevel() + "&7/&699");
        infoLore.add("&7Members: &f" + team.size() + "&7/&f" + cap);
        infoLore.add("&7Portals: &d" + countSetPortals(team) + "&7/&d" + maxPorts);
        infoLore.add("&7Gold Donated: &a$" + String.format("%,.0f", team.getGoldDonated()));
        if (team.getLevel() < TeamLevelManager.MAX_LEVEL) {
            infoLore.add("&7Next level: &e$" + String.format("%,d", nextLevelGold) + " &7more");
        } else {
            infoLore.add("&6&lMAX LEVEL");
        }
        gui.setItem(INFO_SLOT, GuiUtil.namedItem(Material.NETHER_STAR, "&6&l" + team.getName(), infoLore.toArray(new String[0])));

        gui.setItem(MEMBERS_SLOT, GuiUtil.namedItem(Material.PLAYER_HEAD, "&b&lTeam Members",
                "&7View everyone in your team.",
                isOfficer ? "&7Click a member to kick them." : "&7Only officers can kick.",
                "",
                "&e&lClick to view!"));

        if (isOfficer) {
            gui.setItem(INVITE_SLOT, GuiUtil.namedItem(Material.EMERALD, "&a&lInvite Player",
                    "&7Click, then type a player's",
                    "&7name in chat to invite them.",
                    "",
                    "&e&lClick to invite!"));

            gui.setItem(APPLICATIONS_SLOT, GuiUtil.namedItem(Material.WRITTEN_BOOK, "&e&lApplications",
                    "&7Pending: &f" + team.getApplications().size(),
                    "",
                    "&e&lClick to review!"));
        } else {
            gui.setItem(INVITE_SLOT, GuiUtil.namedItem(Material.GRAY_DYE, "&7Invite Player",
                    "&7Only the leader or manager",
                    "&7can invite players."));
        }

        gui.setItem(ENDERCHEST_SLOT, GuiUtil.namedItem(Material.ENDER_CHEST, "&d&lShared Enderchest",
                "&7Everyone in the team shares",
                "&7the same storage here.",
                "",
                "&e&lClick to open!"));

        List<String> portalLore = new ArrayList<>();
        if (maxPorts == 0) {
            portalLore.add("&7Reach level " + plugin.getConfig().getInt("first-portal-level", 10) + " to unlock your first portal.");
        } else {
            portalLore.add("&7Set: &f" + countSetPortals(team) + "&7/&f" + maxPorts);
            portalLore.add("&75s cast, 15m cooldown per portal.");
            portalLore.add("");
            portalLore.add("&e&lClick to view!");
        }
        gui.setItem(PORTALS_SLOT, GuiUtil.namedItem(Material.ENDER_PEARL, "&5&lTeam Portals", portalLore.toArray(new String[0])));

        gui.setItem(DONATE_SLOT, GuiUtil.namedItem(Material.GOLD_INGOT, "&6&lDonate Gold",
                "&7Fund your team's level with",
                "&7&f/team donate <amount>",
                "&7Costs over $5,000,000,000",
                "&7total to reach level 99."));

        if (isLeader) {
            List<String> promoteLore = new ArrayList<>();
            if (team.getManager() != null) {
                promoteLore.add("&7Manager: &f" + teamManager.nameOf(team.getManager()));
                promoteLore.add("&7Type &f/team demote &7to remove them.");
            } else {
                promoteLore.add("&7No manager set.");
                promoteLore.add("&7Type &f/team promote <player>");
            }
            gui.setItem(PROMOTE_SLOT, GuiUtil.namedItem(Material.IRON_CHESTPLATE, "&b&lManager", promoteLore.toArray(new String[0])));

            gui.setItem(DISBAND_SLOT, GuiUtil.namedItem(Material.TNT, "&4&lDisband Team",
                    "&cPermanently deletes the team",
                    "&cfor every member.",
                    "",
                    "&c&lClick to disband!"));
        }

        gui.setItem(LEAVE_SLOT, GuiUtil.namedItem(Material.BARRIER, "&c&lLeave Team",
                "&7You'll lose enderchest access",
                "&7and can't join another team",
                "&7for " + plugin.getConfig().getInt("leave-lockout-days", 3) + " days.",
                "",
                "&c&lClick to leave!"));

        player.openInventory(gui);
    }

    private int countSetPortals(Team team) {
        int count = 0;
        for (int i = 1; i <= 3; i++) {
            if (team.getPortal(i) != null) count++;
        }
        return count;
    }

    private void applyBorder(Inventory gui) {
        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack accent = GuiUtil.coloredPane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int slot : BORDER_SLOTS) {
            gui.setItem(slot, (slot % 2 == 0) ? border : accent);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isTeamGui = TITLE_TEAM.equals(title);
        boolean isNoTeamGui = TITLE_NO_TEAM.equals(title);
        if (!isTeamGui && !isNoTeamGui) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (isNoTeamGui) {
            if (slot == INFO_BOOK_SLOT) {
                infoBook.give(player);
                player.sendMessage(ChatColor.YELLOW + "Here's a copy of the Team Guide.");
            }
            return; // everything else here is purely informational - actions happen via commands
        }

        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.closeInventory();
            return;
        }

        if (slot == INFO_BOOK_SLOT) {
            infoBook.give(player);
            player.sendMessage(ChatColor.YELLOW + "Here's a copy of the Team Guide.");
        } else if (slot == MEMBERS_SLOT) {
            plugin.getTeamMembersGUI().open(player, team);
        } else if (slot == INVITE_SLOT && team.isOfficer(player.getUniqueId())) {
            player.closeInventory();
            teamManager.beginInviteChatInput(player);
            player.sendMessage(ChatColor.YELLOW + "Type the player's name in chat to invite them.");
        } else if (slot == APPLICATIONS_SLOT && team.isOfficer(player.getUniqueId())) {
            plugin.getTeamApplicationsGUI().open(player, team);
        } else if (slot == ENDERCHEST_SLOT) {
            player.openInventory(teamManager.getEnderChest(team));
        } else if (slot == PORTALS_SLOT) {
            plugin.getTeamPortalsGUI().open(player, team);
        } else if (slot == PROMOTE_SLOT && team.isLeader(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Use /team promote <player> or /team demote.");
        } else if (slot == DISBAND_SLOT && team.isLeader(player.getUniqueId())) {
            player.closeInventory();
            teamManager.disbandTeam(player);
        } else if (slot == LEAVE_SLOT) {
            player.closeInventory();
            teamManager.requestLeave(player);
        }
    }
}
