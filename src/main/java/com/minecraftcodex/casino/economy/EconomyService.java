package com.minecraftcodex.casino.economy;

import com.minecraftcodex.casino.database.Database;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;

import java.sql.SQLException;

/** Uses Vault reflectively when installed, avoiding a hard runtime dependency. */
public final class EconomyService {
    private final Database database;
    private final double startingBalance;
    private Object vault;

    public EconomyService(Database database, double startingBalance, ServicesManager services) {
        this.database = database;
        this.startingBalance = startingBalance;
        try {
            Class<?> economyType = Class.forName("net.milkbowl.vault.economy.Economy");
            var registration = services.getRegistration(economyType);
            if (registration != null) vault = registration.getProvider();
        } catch (ClassNotFoundException ignored) { }
    }

    public boolean take(Player player, double amount) {
        if (vault != null) {
            try {
                boolean hasFunds = (boolean) vault.getClass().getMethod("has", OfflinePlayer.class, double.class).invoke(vault, player, amount);
                if (!hasFunds) return false;
                Object response = vault.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(vault, player, amount);
                return (boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
            } catch (ReflectiveOperationException ignored) { }
        }
        try {
            double balance = database.findOrCreateBalance(player.getUniqueId(), startingBalance);
            if (balance < amount) return false;
            database.updateBalance(player.getUniqueId(), balance - amount);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public void give(Player player, double amount) {
        if (vault != null) {
            try {
                vault.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(vault, player, amount);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
        try {
            double balance = database.findOrCreateBalance(player.getUniqueId(), startingBalance);
            database.updateBalance(player.getUniqueId(), balance + amount);
        } catch (SQLException ignored) { }
    }
}
