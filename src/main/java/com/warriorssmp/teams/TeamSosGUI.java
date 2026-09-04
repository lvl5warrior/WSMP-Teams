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

/**
 * Pops open automatically for every online teammate the moment an SOS flare
 * goes off, so accepting/declining is a click instead of having to remember
 * and type /team sosaccept. The actual accept/decline logic still routes
 * through TeamManager - this GUI is just the presentation layer, same as
 * every other menu in this plugin.
 */
public class TeamSosGUI implements Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "&4&lWSMP &8» &cSOS Flare");

    private static final int ACCEPT_SLOT = 11;
    private static final int DECLINE_SLOT = 15;

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    public TeamSosGUI(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    public void open(Player viewer, String callerName, int windowSeconds) {
        Inventory gui = Bukkit.createInventory(null, 27, TITLE);

        ItemStack border = GuiUtil.coloredPane(Material.RED_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 27; slot++) {
            gui.setItem(slot, border);
        }

        gui.setItem(13, GuiUtil.namedItem(Material.FIRE_CHARGE, "&c&l" + callerName + " needs help!",
                "&7Sent an SOS flare.",
                "&7Expires in " + windowSeconds + "s if",
                "&7nobody responds."));

        gui.setItem(ACCEPT_SLOT, GuiUtil.namedItem(Material.LIME_WOOL, "&a&lAccept",
                "&7Teleport to " + callerName + " now.",
                "",
                "&e&lClick to accept!"));

        gui.setItem(DECLINE_SLOT, GuiUtil.namedItem(Material.RED_WOOL, "&c&lDecline",
                "&7Ignore this flare.",
                "",
                "&e&lClick to decline!"));

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == ACCEPT_SLOT) {
            player.closeInventory();
            teamManager.acceptSos(player);
        } else if (slot == DECLINE_SLOT) {
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "You declined the SOS flare.");
        }
    }
}
