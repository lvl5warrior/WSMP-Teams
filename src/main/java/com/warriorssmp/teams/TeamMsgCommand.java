package com.warriorssmp.teams;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /teammsg <message> - a dedicated team-only chat command, separate from the
 * [TeamName] tag TeamChatListener adds to normal global chat. This is the
 * real "message just my team" feature - Minecraft's own vanilla /teammsg
 * only knows about vanilla scoreboard teams, which WSMP-Teams deliberately
 * doesn't register when TAB is present (see TeamManager's tabPluginPresent
 * checks), so vanilla /teammsg has no idea a WSMP team membership exists at
 * all. This command is what actually checks WSMP team membership instead.
 */
public class TeamMsgCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public TeamMsgCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You aren't in a team.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /teammsg <message>");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }

        String formatted = ChatColor.DARK_AQUA + "[Team] " + ChatColor.AQUA + player.getName()
                + ChatColor.GRAY + ": " + ChatColor.WHITE + sb;

        player.sendMessage(formatted);
        teamManager.broadcastToTeam(team, formatted, player.getUniqueId());
        return true;
    }
}
