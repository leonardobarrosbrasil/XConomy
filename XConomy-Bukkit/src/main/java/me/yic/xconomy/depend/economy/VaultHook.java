package me.yic.xconomy.depend.economy;/*
 *  This file (LoadEconomy.java) is a part of project XConomy
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

import me.yic.xconomy.XConomy;
import me.yic.xconomy.depend.LoadEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.Collection;

public class VaultHook extends XConomy {
    public static Economy econ = null;
    public static Permission vaultPerm = null;

    @SuppressWarnings("ConstantConditions")
    public static void load() {
        econ = new Vault();
        RegisteredServiceProvider<Permission> rsp = getInstance().getServer().getServicesManager().getRegistration(Permission.class);
        vaultPerm = rsp.getProvider();
        getInstance().getServer().getServicesManager().register(Economy.class, econ, getInstance(), ServicePriority.Normal);

        if (config.getBoolean("Settings.disable-essentials")) {
            Collection<RegisteredServiceProvider<Economy>> econs = Bukkit.getPluginManager().getPlugin("Vault").getServer().getServicesManager().getRegistrations(Economy.class);
            for (RegisteredServiceProvider<Economy> econ : econs) {
                if (econ.getProvider().getName().equalsIgnoreCase("Essentials Economy")) {
                    getInstance().getServer().getServicesManager().unregister(econ.getProvider());
                }
            }
        }
    }

    public static void unload() {
        getInstance().getServer().getServicesManager().unregister(econ);
    }
}
