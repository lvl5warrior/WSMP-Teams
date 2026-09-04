package com.warriorssmp.teams;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class TeamChatListener implements Listener {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    public TeamChatListener(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    /** Migrated from the legacy AsyncPlayerChatEvent (String-based
     *  setFormat) to Paper's modern AsyncChatEvent (Component-based
     *  ChatRenderer). The old event is deprecated and, depending on a
     *  server's chat configuration, may not reliably fire at all on
     *  current Paper — silently breaking the team tag with no error. The
     *  new renderer wraps whatever renderer is already set (from a
     *  higher-priority plugin like a rank/chat formatter) rather than
     *  replacing it outright, so this only ever prepends the team tag
     *  onto however the rest of the message is already being rendered. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Capture chat used to answer the GUI "Invite Player" prompt before it hits normal chat.
        if (teamManager.isAwaitingInviteInput(player.getUniqueId())) {
            event.setCancelled(true);
            String typedName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            Bukkit.getScheduler().runTask(plugin, () -> teamManager.consumeInviteChatInput(player, typedName));
            return;
        }

        // Same idea, for the admin panel's "Rename Team" prompt.
        if (plugin.getTeamAdminGUI().isAwaitingRenameInput(player.getUniqueId())) {
            event.setCancelled(true);
            String typedName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getTeamAdminGUI().consumeRenameInput(player, typedName));
            return;
        }

        if (!plugin.getConfig().getBoolean("chat-prefix-enabled", true)) return;

        Team team = teamManager.getTeam(player.getUniqueId());
        if (team == null) return;

        Component tag = LegacyComponentSerializer.legacyAmpersand().deserialize(
                "&a[" + team.getName() + " Lv." + team.getLevel() + "]&r ");
        ChatRenderer previousRenderer = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                tag.append(previousRenderer.render(source, sourceDisplayName, message, viewer)));
    }
}
