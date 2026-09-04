package com.warriorssmp.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeamApplicationsGUI implements Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "&4&lWSMP &8» &7Applications");
    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53};

    private final TeamsPlugin plugin;
    private final Map<UUID, Map<Integer, UUID>> lastSlotMap = new HashMap<>();

    public TeamApplicationsGUI(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Team team) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack accent = GuiUtil.coloredPane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int slot : BORDER_SLOTS) {
            gui.setItem(slot, (slot % 2 == 0) ? border : accent);
        }
        gui.setItem(4, GuiUtil.namedItem(Material.BARRIER, "&7Back to Team Menu"));

        Map<Integer, UUID> slotMap = new HashMap<>();
        int slot = 9;
        for (Map.Entry<UUID, String> entry : team.getApplications().entrySet()) {
            if (slot >= 45) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String name = op.getName() != null ? op.getName() : entry.getKey().toString();

            List<String> lore = new ArrayList<>();
            lore.add("&7\"" + entry.getValue() + "\"");
            lore.add("");
            lore.add("&a&lLeft-click to accept");
            lore.add("&c&lRight-click to deny");

            gui.setItem(slot, GuiUtil.playerHead(op, "&f" + name, lore.toArray(new String[0])));
            slotMap.put(slot, entry.getKey());
            slot++;
        }

        if (team.getApplications().isEmpty()) {
            gui.setItem(22, GuiUtil.namedItem(Material.BARRIER, "&7No pending applications"));
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

        UUID applicant = slotMap.get(slot);
        OfflinePlayer op = Bukkit.getOfflinePlayer(applicant);
        String name = op.getName() != null ? op.getName() : applicant.toString();

        if (event.getClick() == ClickType.RIGHT) {
            plugin.getTeamManager().denyApplication(player, name);
        } else {
            plugin.getTeamManager().acceptApplication(player, name);
        }
        open(player, team);
    }
}
