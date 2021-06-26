/*
 *  This file (XConomy.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy;

import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.caches.Cache;
import me.yic.xconomy.data.caches.CacheSemiOnline;
import me.yic.xconomy.data.sql.SQL;
import me.yic.xconomy.depend.LoadEconomy;
import me.yic.xconomy.depend.Placeholder;
import me.yic.xconomy.depend.economy.VaultHook;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.listeners.ConnectionListeners;
import me.yic.xconomy.listeners.SPPsync;
import me.yic.xconomy.listeners.SPsync;
import me.yic.xconomy.listeners.TabList;
import me.yic.xconomy.task.Baltop;
import me.yic.xconomy.task.Updater;
import me.yic.xconomy.utils.DataBaseINFO;
import me.yic.xconomy.utils.EconomyCommand;
import me.yic.xconomy.utils.ServerINFO;
import me.yic.xconomy.utils.UpdateConfig;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;

public class XConomy extends JavaPlugin {

    private static XConomy instance;
    public static FileConfiguration config;
    private MessagesManager messageManager;
    private static boolean foundvaultpe = false;
    public static boolean foundvaultOfflinePermManager = false;
    private BukkitTask refresherTask = null;
    Metrics metrics = null;
    private Placeholder papiExpansion = null;

    @SuppressWarnings("ConstantConditions")
    public void onEnable() {
        instance = this;
        load();
        DataBaseINFO.load();
        readserverinfo();
        messageManager = new MessagesManager(this);
        messageManager.load();

        if (!LoadEconomy.load()) {
            getLogger().info("No supported dependent plugins were found");
            getLogger().info("[ Vault ][ Enterprise ]");
            logger("XConomy已成功卸载", null);
            return;
        }

        foundvaultOfflinePermManager = checkVaultOfflinePermManager();

        if (Bukkit.getPluginManager().getPlugin("DatabaseDrivers") != null) {
            logger("发现 DatabaseDrivers", null);
            ServerINFO.DDrivers = true;
        }

        allowHikariConnectionPooling();
        if (!DataCon.create()) {
            logger("XConomy已成功卸载", null);
            return;
        }

        Cache.baltop();

        if (checkup()) {
            new Updater().runTaskAsynchronously(this);
        }
        // 检查更新

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            logger("发现 PlaceholderAPI", null);
            if (checkVaultPE()) {
                logger(null, String.join("", Collections.nCopies(70, "=")));
                logger("vault-baltop-tips-a", null);
                logger("vault-baltop-tips-b", null);
                logger(null, String.join("", Collections.nCopies(70, "=")));
                foundvaultpe = true;
            }
            setupPlaceHolderAPI();
        }

        getServer().getPluginManager().registerEvents(new ConnectionListeners(), this);


        metrics = new Metrics(this, 6588);

        Bukkit.getPluginCommand("money").setExecutor(new Commands());
        Bukkit.getPluginCommand("balance").setExecutor(new Commands());
        Bukkit.getPluginCommand("balancetop").setExecutor(new Commands());
        Bukkit.getPluginCommand("pay").setExecutor(new Commands());
        Bukkit.getPluginCommand("xconomy").setExecutor(new Commands());

        this.getCommand("money").setTabCompleter(new TabList());
        this.getCommand("balance").setTabCompleter(new TabList());
        this.getCommand("balancetop").setTabCompleter(new TabList());
        this.getCommand("pay").setTabCompleter(new TabList());
        this.getCommand("xconomy").setTabCompleter(new TabList());

        if (config.getBoolean("Settings.eco-command")) {
            try {
                final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                bukkitCommandMap.setAccessible(true);
                CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
                coveress(commandMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (config.getBoolean("BungeeCord.enable")) {
            if (isBungeecord()) {
                getServer().getMessenger().registerIncomingPluginChannel(this, "xconomy:aca", new SPsync());
                getServer().getMessenger().registerOutgoingPluginChannel(this, "xconomy:acb");
                getServer().getMessenger().registerIncomingPluginChannel(this, "xconomy:global", new SPPsync());
                logger("已开启BungeeCord同步", null);
            } else if (DataBaseINFO.getStorageType() == 0 || DataBaseINFO.getStorageType() == 1) {
                if (DataBaseINFO.gethost().equalsIgnoreCase("Default")) {
                    logger("SQLite文件路径设置错误", null);
                    logger("BungeeCord同步未开启", null);
                }
            }
        }

        DataFormat.load();

        int time = config.getInt("Settings.refresh-time");
        if (time < 30) {
            time = 30;
        }

        refresherTask = new Baltop().runTaskTimerAsynchronously(this, time * 20L, time * 20L);
        logger(null, "===== YiC =====");

    }

    public void onDisable() {
        LoadEconomy.unload();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (NoSuchMethodError ignored) {
            }
        }

        if (isBungeecord()) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, "xconomy:aca", new SPsync());
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, "xconomy:acb");
            getServer().getMessenger().unregisterIncomingPluginChannel(this, "xconomy:global", new SPPsync());
        }

        refresherTask.cancel();
        CacheSemiOnline.save();
        SQL.close();
        logger("XConomy已成功卸载", null);
    }

    public static XConomy getInstance() {
        return instance;
    }

    public void reloadMessages() {
        messageManager.load();
    }

    public void readserverinfo() {
        ServerINFO.Lang = config.getString("Settings.language");
        ServerINFO.IsBungeeCordMode = isBungeecord();
        ServerINFO.IsSemiOnlineMode = config.getBoolean("Settings.semi-online-mode");
        ServerINFO.Sign = config.getString("BungeeCord.sign");
        ServerINFO.InitialAmount = config.getDouble("Settings.initial-bal");
        ServerINFO.IgnoreCase = config.getBoolean("Settings.username-ignore-case");

        ServerINFO.RankingSize = config.getInt("Settings.ranking-size");
        if (ServerINFO.RankingSize > 100){
            ServerINFO.RankingSize = 100;
        }
    }

    public static void allowHikariConnectionPooling() {
        if (foundvaultpe) {
            return;
        }
        if (DataBaseINFO.getStorageType() == 0 || DataBaseINFO.getStorageType() == 1) {
            return;
        }
        ServerINFO.EnableConnectionPool = DataBaseINFO.DataBaseINFO.getBoolean("Settings.usepool");
    }

    public static String getSign() {
        return config.getString("BungeeCord.sign");
    }

    private void setupPlaceHolderAPI() {
        papiExpansion = new Placeholder(this);
        if (papiExpansion.register()) {
            getLogger().info("PlaceholderAPI successfully hooked");
        } else {
            getLogger().info("PlaceholderAPI unsuccessfully hooked");
        }
    }

    public void logger(String tag, String message) {
        if (tag == null) {
            getLogger().info(message);
        } else {
            if (message == null) {
                getLogger().info(MessagesManager.systemMessage(tag));
            } else {
                if (message.startsWith("<#>")) {
                    getLogger().info(message.substring(3) + MessagesManager.systemMessage(tag));
                } else {
                    getLogger().info(MessagesManager.systemMessage(tag) + message);
                }
            }
        }
    }

    public static boolean isBungeecord() {
        if (!config.getBoolean("BungeeCord.enable")) {
            return false;
        }

        if (DataBaseINFO.getStorageType() == 0 || DataBaseINFO.getStorageType() == 1) {
            return !DataBaseINFO.gethost().equalsIgnoreCase("Default");
        }

        return true;

    }


    public static boolean checkup() {
        return config.getBoolean("Settings.check-update");
    }

    private void load() {
        saveDefaultConfig();
        update_config();
        reloadConfig();
        config = getConfig();
    }

    @SuppressWarnings("ConstantConditions")
    private static boolean checkVaultPE() {
        File peFolder = new File(Bukkit.getPluginManager().getPlugin("PlaceholderAPI").getDataFolder(), "config.yml");
        if (!peFolder.exists()) {
            return false;
        }
        FileConfiguration peConfig = YamlConfiguration.loadConfiguration(peFolder);
        if (peConfig.contains("expansions.vault.baltop.enabled")) {
            return peConfig.getBoolean("expansions.vault.baltop.enabled");
        }
        return false;
    }

    private void update_config() {
        File config = new File(this.getDataFolder(), "config.yml");
        boolean update = UpdateConfig.update(getConfig(), config);
        if (update) {
            saveConfig();
        }
    }


    private void coveress(CommandMap commandMap) {
        Command commanda = new EconomyCommand("economy");
        commandMap.register("economy", commanda);
        Command commandb = new EconomyCommand("eco");
        commandMap.register("eco", commandb);
        Command commandc = new EconomyCommand("ebalancetop");
        commandMap.register("ebalancetop", commandc);
        Command commandd = new EconomyCommand("ebaltop");
        commandMap.register("ebaltop", commandd);
        Command commande = new EconomyCommand("eeconomy");
        commandMap.register("eeconomy", commande);
    }

    @SuppressWarnings("all")
    private boolean checkVaultOfflinePermManager() {
        // Check if vault is linked to a permission system that supports offline player checks.
        if (LoadEconomy.vault) {
            switch (VaultHook.vaultPerm.getName()) {
                // Add other plugins that also have an offline player permissions manager.
                case "LuckPerms":
                    return true;
            }
        }
        return false;
    }
}
