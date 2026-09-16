package com.minecraftcodex.casino.database;

import java.io.File; import java.sql.*; import java.util.UUID;
public final class Database implements AutoCloseable {
 private Connection connection;
 public Database(File folder) throws SQLException { folder.mkdirs(); connection=DriverManager.getConnection("jdbc:sqlite:"+new File(folder,"casino.db").getPath()); try(Statement s=connection.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS balances (uuid TEXT PRIMARY KEY,balance REAL NOT NULL)");s.executeUpdate("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT,game TEXT,bet REAL,payout REAL,result TEXT,created_at INTEGER)");s.executeUpdate("CREATE TABLE IF NOT EXISTS stats (uuid TEXT,stat TEXT,value INTEGER,PRIMARY KEY(uuid,stat))");} }
 public synchronized double balance(UUID id,double initial) throws SQLException {try(PreparedStatement q=connection.prepareStatement("SELECT balance FROM balances WHERE uuid=?")){q.setString(1,id.toString());ResultSet r=q.executeQuery();if(r.next())return r.getDouble(1);} try(PreparedStatement q=connection.prepareStatement("INSERT INTO balances VALUES(?,?)")){q.setString(1,id.toString());q.setDouble(2,initial);q.executeUpdate();}return initial;}
 public synchronized void balance(UUID id,double value)throws SQLException{try(PreparedStatement q=connection.prepareStatement("INSERT INTO balances(uuid,balance) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")){q.setString(1,id.toString());q.setDouble(2,value);q.executeUpdate();}}
 public synchronized void history(UUID id,String game,double bet,double payout,String result){try(PreparedStatement q=connection.prepareStatement("INSERT INTO history(uuid,game,bet,payout,result,created_at) VALUES(?,?,?,?,?,?)")){q.setString(1,id.toString());q.setString(2,game);q.setDouble(3,bet);q.setDouble(4,payout);q.setString(5,result);q.setLong(6,System.currentTimeMillis());q.executeUpdate();}catch(SQLException ignored){}}
 public synchronized void stat(UUID id,String stat){try(PreparedStatement q=connection.prepareStatement("INSERT INTO stats(uuid,stat,value) VALUES(?,?,1) ON CONFLICT(uuid,stat) DO UPDATE SET value=value+1")){q.setString(1,id.toString());q.setString(2,stat);q.executeUpdate();}catch(SQLException ignored){}}
 public void close() throws SQLException {if(connection!=null)connection.close();}
}
