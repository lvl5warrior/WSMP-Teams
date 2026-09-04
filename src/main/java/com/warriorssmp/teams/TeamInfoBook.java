package com.warriorssmp.teams;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A real written book explaining how teams work end to end, handed to the
 * player from the GUI's Info Book button (or /team info). Every number on
 * these pages is pulled from config.yml and TeamLevelManager's actual
 * formulas at the time the book is generated, rather than hardcoded, so it
 * never drifts out of sync if those values are ever changed.
 */
public class TeamInfoBook {

    private final TeamsPlugin plugin;
    private final TeamLevelManager levelManager;

    public TeamInfoBook(TeamsPlugin plugin, TeamLevelManager levelManager) {
        this.plugin = plugin;
        this.levelManager = levelManager;
    }

    public void give(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Team Guide");
        meta.setAuthor("WarriorsSMP");

        int leaveDays = plugin.getConfig().getInt("leave-lockout-days", 3);
        int castSeconds = plugin.getConfig().getInt("portal-cast-seconds", 5);
        int portalCooldown = plugin.getConfig().getInt("portal-cooldown-minutes", 15);
        int enderchestSize = plugin.getConfig().getInt("enderchest-size", 45);
        int firstPortalLevel = plugin.getConfig().getInt("first-portal-level", 10);
        int secondPortalLevel = plugin.getConfig().getInt("second-portal-level", 40);
        int thirdPortalLevel = plugin.getConfig().getInt("third-portal-level", 70);
        int startingCap = plugin.getConfig().getInt("starting-member-cap", 10);
        int maxCap = plugin.getConfig().getInt("max-member-cap", 50);
        long maxGold = plugin.getConfig().getLong("target-max-gold", 5_030_000_000L);
        int sosCooldown = plugin.getConfig().getInt("sos-cooldown-minutes", 60);
        int sosWindow = plugin.getConfig().getInt("sos-window-seconds", 60);

        List<String> pages = new ArrayList<>();

        pages.add(color("&1&lTeam Guide\n\n"
                + "&7A team is a group of\n"
                + "players who share a\n"
                + "storage chest, up to "
                + levelManager.getMaxPortals(TeamLevelManager.MAX_LEVEL) + "\n"
                + "fast-travel portals,\n"
                + "and a shared level."));

        pages.add(color("&1&lGetting Started\n\n"
                + "&7No team yet? Use:\n"
                + "&1/team create <name>\n\n"
                + "&7To join one instead:\n"
                + "&1/team apply <team>\n"
                + "&1<message>"));

        pages.add(color("&1&lRoles\n\n"
                + "&7Every team has one\n"
                + "&1Leader&7 and up to one\n"
                + "&1Manager&7. Both count as\n"
                + "officers and can invite\n"
                + "players and review\n"
                + "applications."));

        pages.add(color("&1&lRoles (2)\n\n"
                + "&7Only the Leader can\n"
                + "promote/demote a\n"
                + "Manager or disband\n"
                + "the team entirely.\n\n"
                + "&1/team promote <name>\n"
                + "&1/team demote"));

        pages.add(color("&1&lTeam Chat\n\n"
                + "&7Your normal chat shows\n"
                + "[TeamName] to everyone\n"
                + "on the server.\n\n"
                + "&7To message only your\n"
                + "own team instead:\n"
                + "&1/teammsg <message>\n"
                + "&7(or &1/tm&7 for short)"));

        pages.add(color("&1&lSOS Flare\n\n"
                + "&7In trouble? Anyone on\n"
                + "the team can send one -\n"
                + "no rank needed.\n\n"
                + "&1/team sos\n\n"
                + "&7Alerts every online\n"
                + "teammate for " + sosWindow + "s."));

        pages.add(color("&1&lSOS Flare (2)\n\n"
                + "&7Teammates get a popup\n"
                + "with Accept/Decline -\n"
                + "or type:\n\n"
                + "&1/team sosaccept\n\n"
                + "&7You get your own\n"
                + sosCooldown + "-minute cooldown\n"
                + "after sending one."));

        pages.add(color("&1&lLeveling\n\n"
                + "&7Teams level up by\n"
                + "donating gold:\n"
                + "&1/team donate <amt>\n\n"
                + "&7Reaching level " + TeamLevelManager.MAX_LEVEL + " takes\n"
                + "&1$" + String.format("%,d", maxGold) + "\n"
                + "&7total, donated by\n"
                + "anyone on the team."));

        pages.add(color("&1&lLeveling (2)\n\n"
                + "&7Your member cap\n"
                + "grows as you level:\n\n"
                + "&1Level 1: &7" + startingCap + " members\n"
                + "&1Level " + TeamLevelManager.MAX_LEVEL + ": &7" + maxCap + " members\n\n"
                + "&7Growth is smooth\n"
                + "between those."));

        pages.add(color("&1&lPortals\n\n"
                + "&7Portals let your whole\n"
                + "team fast-travel to a\n"
                + "set location.\n\n"
                + "&1Unlocks:\n"
                + "&7Lv." + firstPortalLevel + " - 1st portal\n"
                + "&7Lv." + secondPortalLevel + " - 2nd portal\n"
                + "&7Lv." + thirdPortalLevel + " - 3rd portal"));

        pages.add(color("&1&lPortals (2)\n\n"
                + "&7Using one takes " + castSeconds + "s\n"
                + "to channel - moving\n"
                + "or taking damage\n"
                + "cancels it.\n\n"
                + "&7You then get your own\n"
                + portalCooldown + "-minute cooldown\n"
                + "on that portal - other\n"
                + "members can still use it."));

        pages.add(color("&1&lShared Storage\n\n"
                + "&7Every member shares\n"
                + "one " + enderchestSize + "-slot ender chest.\n"
                + "Anything one member\n"
                + "stores, everyone can\n"
                + "take back out.\n\n"
                + "&7Open it from the\n"
                + "team GUI anytime."));

        pages.add(color("&1&lLeaving\n\n"
                + "&7/team leave starts a\n"
                + "confirmation window -\n"
                + "you must then run\n"
                + "&1/team leaveconfirm\n\n"
                + "&7Leaving locks you out\n"
                + "of joining or creating\n"
                + "another team for\n"
                + "&c" + leaveDays + " day" + (leaveDays == 1 ? "" : "s") + "&7."));

        pages.add(color("&1&lDisbanding\n\n"
                + "&cOnly the Leader can\n"
                + "disband a team, and\n"
                + "it's permanent for\n"
                + "every member.\n\n"
                + "&7There's no undo -\n"
                + "the GUI will ask you\n"
                + "to confirm first."));

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
