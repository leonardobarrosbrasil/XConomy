/*
 *  This file (SQL.java) is a part of project XConomy
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
package me.yic.xconomy.data.sql;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.data.caches.Cache;
import me.yic.xconomy.data.caches.CacheSemiOnline;
import me.yic.xconomy.utils.DataBaseINFO;
import me.yic.xconomy.utils.ServerINFO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;

public class SQLCreateNewAccount extends SQL{

    public static void newPlayer(Player player) {
        Connection connection = database.getConnectionAndCheck();
        boolean doubledata = checkUser(player, connection);
        if (!doubledata) {
            selectUser(player.getUniqueId().toString(), player.getName(), connection);
        }
        database.closeHikariConnection(connection);
    }

    private static boolean checkUser(Player player, Connection connection) {
        boolean doubledata = false;
        try {
            String query;
            if (ServerINFO.IgnoreCase) {
                if (DataBaseINFO.isMySQL()) {
                    query = "select * from " + tableName + " where player = ?";
                } else {
                    query = "select * from " + tableName + " where player = ? COLLATE NOCASE";
                }
            }else {
                if (DataBaseINFO.isMySQL()) {
                    query = "select * from " + tableName + " where binary player = ?";
                } else {
                    query = "select * from " + tableName + " where player = ?";
                }
            }

            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, player.getName());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                if (!player.getUniqueId().toString().equals(rs.getString(1))) {
                    doubledata = true;
                    if (!ServerINFO.IsSemiOnlineMode) {
                        if (player.isOnline()) {
                            Bukkit.getScheduler().runTask(XConomy.getInstance(), () ->
                                    player.kickPlayer("[XConomy] The player with the same username data exists on the server"));
                        }
                    } else {
                        CacheSemiOnline.CacheSubUUID_checkUser(rs.getString(1), player);
                    }
                }
            }

            rs.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return doubledata;
    }


    private static void createAccount(String UID, String user, double amount, Connection co_a) {
        try {
            String query;
            if (DataBaseINFO.isMySQL()) {
                query = "INSERT INTO " + tableName + "(UID,player,balance,hidden) values(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE UID = ?";
            } else {
                query = "INSERT INTO " + tableName + "(UID,player,balance,hidden) values(?,?,?,?) ";
            }

            PreparedStatement statement = co_a.prepareStatement(query);
            statement.setString(1, UID);
            statement.setString(2, user);
            statement.setDouble(3, amount);
            statement.setInt(4, 0);

            if (DataBaseINFO.isMySQL()) {
                statement.setString(5, UID);
            }

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public static void createNonPlayerAccount(String account, double bal, Connection co) {
        try {
            String query;
            if (DataBaseINFO.isMySQL()) {
                query = "INSERT INTO " + tableNonPlayerName + "(account,balance) values(?,?) "
                        + "ON DUPLICATE KEY UPDATE account = ?";
            } else {
                query = "INSERT INTO " + tableNonPlayerName + "(account,balance) values(?,?)";
            }

            PreparedStatement statement = co.prepareStatement(query);
            statement.setString(1, account);
            statement.setDouble(2, bal);

            if (DataBaseINFO.isMySQL()) {
                statement.setString(3, account);
            }

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void updateUser(String UID, String user, Connection co_a) {
        try {
            PreparedStatement statement = co_a.prepareStatement("update " + tableName + " set player = ? where UID = ?");
            statement.setString(1, user);
            statement.setString(2, UID);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private static void selectUser(String UID, String name, Connection connection) {
        String user = "#";

        try {
            PreparedStatement statement = connection.prepareStatement("select * from " + tableName + " where UID = ?");
            statement.setString(1, UID);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                user = rs.getString(2);
            } else {
                user = name;
                createAccount(UID, user, ServerINFO.InitialAmount, connection);
            }
            rs.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (!user.equals(name) && !user.equals("#")) {
            Cache.removeFromUUIDCache(name);
            updateUser(UID, name, connection);
            XConomy.getInstance().logger(" 名称已更改!", "<#>" + name);
        }
    }

}