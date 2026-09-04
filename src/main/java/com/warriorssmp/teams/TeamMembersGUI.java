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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamMembersGUI implements Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "&4&lWSMP &8» &7Team Members");
    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53};

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    // Per-player cache of slot -> member uuid, so clicks can be resolved reliably.
    private final Map<UUID, Map<Integer, UUID>> lastSlotMap = new HashMap<>();

    public TeamMembersGUI(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    public void open(Player viewer, Team team) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack accent = GuiUtil.coloredPane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int slot : BORDER_SLOTS) {
            gui.setItem(slot, (slot % 2 == 0) ? border : accent);
        }
        gui.setItem(4, GuiUtil.namedItem(Material.BARRIER, "&7Back to Team Menu"));

        boolean viewerCanKick = team.isOfficer(viewer.getUniqueId());
        Map<Integer, UUID> slotMap = new HashMap<>();

        int slot = 9;
        for (UUID member : team.getMembers()) {
            if (slot >= 45) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            String name = op.getName() != null ? op.getName() : member.toString();

            List<String> lore = new ArrayList<>();
            if (team.isLeader(member)) {
                lore.add("&6&lLeader");
            } else if (team.isManager(member)) {
                lore.add("&b&lManager");
            } else {
                lore.add("&7Member");
            }
            boolean online = Bukkit.getPlayer(member) != null;
            lore.add(online ? "&aOnline" : "&8Offline");

            boolean canKickThis = viewerCanKick && !team.isLeader(member) && !member.equals(viewer.getUniqueId())
                    && !(team.isManager(viewer.getUniqueId()) && team.isManager(member));
            if (canKickThis) {
                lore.add("");
                lore.add("&c&lClick to kick!");
            }

            gui.setItem(slot, GuiUtil.playerHead(op, "&f" + name, lore.toArray(new String[0])));
            slotMap.put(slot, member);
            slot++;
        }

        lastSlotMap.put(viewer.getUniqueId(), slotMap);
        viewer.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 4) {
            plugin.getTeamGUI().open(player);
            return;
        }

        Map<Integer, UUID> slotMap = lastSlotMap.get(player.getUniqueId());
        if (slotMap == null || !slotMap.containsKey(slot)) return;

        Team team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;

        UUID target = slotMap.get(slot);
        if (target.equals(player.getUniqueId())) return;
        if (!team.isOfficer(player.getUniqueId())) return;
        if (team.isLeader(target)) return;

        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        String name = op.getName() != null ? op.getName() : target.toString();
        plugin.getTeamManager().kickPlayer(player, name);
        open(player, team);
    }
}
