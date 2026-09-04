package com.warriorssmp.teams;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class TeamProtectionListener implements Listener {

    private final TeamManager teamManager;

    public TeamProtectionListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        Team victimTeam = teamManager.getTeam(victim.getUniqueId());
        if (victimTeam == null) return;
        if (!victimTeam.isMember(attacker.getUniqueId())) return;

        event.setCancelled(true);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource source = ((Projectile) event.getDamager()).getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }
}
