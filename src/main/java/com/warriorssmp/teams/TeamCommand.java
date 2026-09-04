package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final TeamPortalManager portalManager;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "disband", "invite", "acceptinvite", "declineinvite", "apply",
            "applications", "acceptapp", "denyapp", "kick", "leave", "leaveconfirm",
            "promote", "demote", "donate", "portal", "enderchest", "info", "list", "gui", "guide", "sos", "sosaccept", "admin"
    );

    public TeamCommand(TeamsPlugin plugin, TeamManager teamManager, TeamPortalManager portalManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.portalManager = portalManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getTeamGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team create <name>");
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                return true;

            case "disband":
                teamManager.disbandTeam(player);
                return true;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team invite <player>");
                    return true;
                }
                teamManager.invitePlayer(player, args[1]);
                return true;

            case "acceptinvite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team acceptinvite <team>");
                    return true;
                }
                teamManager.acceptInvite(player, args[1]);
                return true;

            case "declineinvite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team declineinvite <team>");
                    return true;
                }
                teamManager.declineInvite(player, args[1]);
                return true;

            case "apply":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team apply <team> <message>");
                    return true;
                }
                teamManager.applyToJoin(player, args[1], joinFrom(args, 2));
                return true;

            case "applications": {
                Team team = teamManager.getTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(ChatColor.RED + "You aren't in a team.");
                    return true;
                }
                if (!team.isOfficer(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Only the leader or manager can view applications.");
                    return true;
                }
                plugin.getTeamApplicationsGUI().open(player, team);
                return true;
            }

            case "acceptapp":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team acceptapp <player>");
                    return true;
                }
                teamManager.acceptApplication(player, args[1]);
                return true;

            case "denyapp":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team denyapp <player>");
                    return true;
                }
                teamManager.denyApplication(player, args[1]);
                return true;

            case "kick":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team kick <player>");
                    return true;
                }
                teamManager.kickPlayer(player, args[1]);
                return true;

            case "leave":
                teamManager.requestLeave(player);
                return true;

            case "leaveconfirm":
                teamManager.confirmLeave(player);
                return true;

            case "promote":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team promote <player>");
                    return true;
                }
                teamManager.promote(player, args[1]);
                return true;

            case "demote":
                teamManager.demote(player);
                return true;

            case "donate":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /team donate <amount>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    teamManager.donateGold(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "That's not a valid amount.");
                }
                return true;

            case "portal":
                handlePortal(player, args);
                return true;

            case "enderchest": {
                Team team = teamManager.getTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(ChatColor.RED + "You aren't in a team.");
                    return true;
                }
                player.openInventory(teamManager.getEnderChest(team));
                return true;
            }

            case "info":
                showInfo(player);
                return true;

            case "list":
                showTeamList(player);
                return true;

            case "gui":
                plugin.getTeamGUI().open(player);
                return true;

            case "guide":
                plugin.getTeamInfoBook().give(player);
                player.sendMessage(ChatColor.YELLOW + "Here's a copy of the Team Guide.");
                return true;

            case "sos":
                teamManager.triggerSos(player);
                return true;

            case "sosaccept":
                teamManager.acceptSos(player);
                return true;

            case "admin":
                if (!player.hasPermission("wsmpteams.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                    return true;
                }
                plugin.getTeamAdminGUI().openMain(player);
                return true;

            default:
                player.sendMessage(ChatColor.YELLOW + "Unknown subcommand. Type /team for the menu.");
                return true;
        }
    }

    private void handlePortal(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /team portal <1-3> | /team portal set <1-3>");
            return;
        }
        if (args[1].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /team portal set <1-3>");
                return;
            }
            int slot = parseSlot(args[2]);
            if (slot == -1) {
                player.sendMessage(ChatColor.RED + "Portal slot must be 1, 2, or 3.");
                return;
            }
            teamManager.setPortal(player, slot, player.getLocation());
            return;
        }
        int slot = parseSlot(args[1]);
        if (slot == -1) {
            player.sendMessage(ChatColor.RED + "Portal slot must be 1, 2, or 3.");
            return;
        }
        portalManager.usePortal(player, slot);
    }

    private int parseSlot(String s) {
        try {
            int slot = Integer.parseInt(s);
            return (slot >= 1 && slot <= 3) ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void showInfo(Player player) {
        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You aren't in a team.");
            return;
        }
        TeamLevelManager lvl = plugin.getTeamLevelManager();
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + team.getName());
        player.sendMessage(ChatColor.GRAY + "Leader: " + ChatColor.WHITE + teamManager.nameOf(team.getLeader()));
        if (team.getManager() != null) {
            player.sendMessage(ChatColor.GRAY + "Manager: " + ChatColor.WHITE + teamManager.nameOf(team.getManager()));
        }
        player.sendMessage(ChatColor.GRAY + "Level: " + ChatColor.GOLD + team.getLevel() + ChatColor.GRAY + "/99");
        player.sendMessage(ChatColor.GRAY + "Members: " + ChatColor.WHITE + team.size() + "/" + lvl.getMemberCap(team.getLevel()));
        player.sendMessage(ChatColor.GRAY + "Gold Donated: " + ChatColor.GREEN + "$" + String.format("%,.2f", team.getGoldDonated()));
    }

    private void showTeamList(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Teams:");
        for (Team team : teamManager.getAllTeams()) {
            player.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + team.getName()
                    + ChatColor.GRAY + " (Lv." + team.getLevel() + ", " + team.size() + " members)");
        }
    }

    private String joinFrom(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) options.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("invite", "kick", "promote", "acceptapp", "denyapp").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) options.add(p.getName());
                }
            } else if (Arrays.asList("acceptinvite", "declineinvite", "apply").contains(sub)) {
                for (Team team : teamManager.getAllTeams()) {
                    if (team.getName().toLowerCase().startsWith(args[1].toLowerCase())) options.add(team.getName());
                }
            } else if (sub.equals("portal")) {
                options.addAll(Arrays.asList("1", "2", "3", "set"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("portal") && args[1].equalsIgnoreCase("set")) {
            options.addAll(Arrays.asList("1", "2", "3"));
        }
        return options;
    }
}
