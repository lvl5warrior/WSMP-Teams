package com.warriorssmp.teams;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamsPlugin extends JavaPlugin {

    private Economy economy;

    private TeamLevelManager teamLevelManager;
    private TeamManager teamManager;
    private TeamPortalManager teamPortalManager;
    private TeamInfoBook teamInfoBook;

    private TeamGUI teamGUI;
    private TeamMembersGUI teamMembersGUI;
    private TeamApplicationsGUI teamApplicationsGUI;
    private TeamPortalsGUI teamPortalsGUI;
    private TeamSosGUI teamSosGUI;
    private TeamAdminGUI teamAdminGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling WSMP-Teams. Make sure Vault + an economy plugin is installed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        teamLevelManager = new TeamLevelManager(this);
        teamManager = new TeamManager(this, teamLevelManager);
        teamPortalManager = new TeamPortalManager(this, teamManager);
        teamInfoBook = new TeamInfoBook(this, teamLevelManager);

        teamGUI = new TeamGUI(this, teamManager, teamLevelManager, teamInfoBook);
        teamMembersGUI = new TeamMembersGUI(this, teamManager);
        teamApplicationsGUI = new TeamApplicationsGUI(this);
        teamPortalsGUI = new TeamPortalsGUI(this, teamManager, teamLevelManager, teamPortalManager);
        teamSosGUI = new TeamSosGUI(this, teamManager);
        teamAdminGUI = new TeamAdminGUI(this, teamManager);

        getServer().getPluginManager().registerEvents(teamGUI, this);
        getServer().getPluginManager().registerEvents(teamMembersGUI, this);
        getServer().getPluginManager().registerEvents(teamApplicationsGUI, this);
        getServer().getPluginManager().registerEvents(teamPortalsGUI, this);
        getServer().getPluginManager().registerEvents(teamSosGUI, this);
        getServer().getPluginManager().registerEvents(teamAdminGUI, this);
        getServer().getPluginManager().registerEvents(teamPortalManager, this);
        getServer().getPluginManager().registerEvents(new TeamProtectionListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(this, teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamJoinQuitListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamEnderChestListener(teamManager), this);

        TeamCommand teamCommand = new TeamCommand(this, teamManager, teamPortalManager);
        getCommand("team").setExecutor(teamCommand);
        getCommand("team").setTabCompleter(teamCommand);

        getCommand("teammsg").setExecutor(new TeamMsgCommand(teamManager));

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WSMPTeamsPlaceholders(this, teamManager, teamLevelManager).register();
            getLogger().info("Registered PlaceholderAPI placeholders (%wsmpteams_...%)");
        } else {
            getLogger().info("PlaceholderAPI not found - skipping placeholder registration (this is optional).");
        }

        if (teamManager.isTabPluginPresent()) {
            getLogger().info("TAB detected - skipping our own scoreboard team prefixes to avoid fighting TAB "
                    + "for tablist/nametag control. Add %wsmpteams_tag% (or %wsmpteams_team% / %wsmpteams_role%) "
                    + "to TAB's tablist-name-formatting and nametag prefix/suffix in TAB's config.yml instead.");
        }

        int autosaveMinutes = getConfig().getInt("autosave-minutes", 5);
        long ticks = autosaveMinutes * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> teamManager.saveAll(), ticks, ticks);

        getLogger().info("WSMP-Teams enabled!");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveAll();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public TeamLevelManager getTeamLevelManager() {
        return teamLevelManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public TeamPortalManager getTeamPortalManager() {
        return teamPortalManager;
    }

    public TeamInfoBook getTeamInfoBook() {
        return teamInfoBook;
    }

    public TeamGUI getTeamGUI() {
        return teamGUI;
    }

    public TeamMembersGUI getTeamMembersGUI() {
        return teamMembersGUI;
    }

    public TeamApplicationsGUI getTeamApplicationsGUI() {
        return teamApplicationsGUI;
    }

    public TeamPortalsGUI getTeamPortalsGUI() {
        return teamPortalsGUI;
    }

    public TeamSosGUI getTeamSosGUI() {
        return teamSosGUI;
    }

    public TeamAdminGUI getTeamAdminGUI() {
        return teamAdminGUI;
    }
}
