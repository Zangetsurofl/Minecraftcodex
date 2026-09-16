package com.minecraftcodex.casino.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class Database implements AutoCloseable {
    private final Connection connection;

    public Database(File folder) throws SQLException {
        folder.mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "casino.db").getPath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS balances (uuid TEXT PRIMARY KEY,balance REAL NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT,game TEXT,bet REAL,payout REAL,result TEXT,created_at INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stats (uuid TEXT,stat TEXT,value INTEGER,PRIMARY KEY(uuid,stat))");
        }
    }

    public synchronized double findOrCreateBalance(UUID id, double initialBalance) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT balance FROM balances WHERE uuid=?")) {
            query.setString(1, id.toString());
            ResultSet result = query.executeQuery();
            if (result.next()) return result.getDouble(1);
        }
        updateBalance(id, initialBalance);
        return initialBalance;
    }

    public synchronized void updateBalance(UUID id, double value) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("INSERT INTO balances(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")) {
            query.setString(1, id.toString());
            query.setDouble(2, value);
            query.executeUpdate();
        }
    }

    /** Atomically debits an internal balance after creating it when necessary. */
    public synchronized boolean withdraw(UUID id, double initialBalance, double amount) throws SQLException {
        if (amount <= 0) return false;
        double balance = findOrCreateBalance(id, initialBalance);
        if (balance < amount) return false;
        updateBalance(id, balance - amount);
        return true;
    }

    /** Atomically credits an internal balance after creating it when necessary. */
    public synchronized void deposit(UUID id, double initialBalance, double amount) throws SQLException {
        if (amount <= 0) return;
        updateBalance(id, findOrCreateBalance(id, initialBalance) + amount);
    }

    public synchronized void history(UUID id, String game, double bet, double payout, String result) {
        try (PreparedStatement query = connection.prepareStatement("INSERT INTO history(uuid,game,bet,payout,result,created_at) VALUES(?,?,?,?,?,?)")) {
            query.setString(1, id.toString()); query.setString(2, game); query.setDouble(3, bet);
            query.setDouble(4, payout); query.setString(5, result); query.setLong(6, System.currentTimeMillis()); query.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public synchronized void stat(UUID id, String stat) {
        try (PreparedStatement query = connection.prepareStatement("INSERT INTO stats(uuid,stat,value) VALUES(?,?,1) ON CONFLICT(uuid,stat) DO UPDATE SET value=value+1")) {
            query.setString(1, id.toString()); query.setString(2, stat); query.executeUpdate();
        } catch (SQLException ignored) { }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
