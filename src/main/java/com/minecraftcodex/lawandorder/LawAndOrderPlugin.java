package com.minecraftcodex.lawandorder;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public final class LawAndOrderPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private final Map<UUID, Integer> wanted = new HashMap<>();
    private final Map<UUID, Long> jailedUntil = new HashMap<>();
    private final Map<UUID, Boolean> duty = new HashMap<>();
    private final Map<UUID, String> factions = new HashMap<>();
    private final Map<UUID, String> gangs = new HashMap<>(), invitations = new HashMap<>();
    private final Map<String, UUID> leaders = new HashMap<>();
    private File dataFile; private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig(); dataFile = new File(getDataFolder(), "players.yml"); data = YamlConfiguration.loadConfiguration(dataFile); loadData();
        for (String command : List.of("duty", "wanted", "arrest", "fine", "crime", "hide", "gang")) {
            Objects.requireNonNull(getCommand(command)).setExecutor(this); Objects.requireNonNull(getCommand(command)).setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::releaseExpired, 20L, 20L * 30);
    }
    @Override public void onDisable() { saveData(); }

    private void loadData() {
        ConfigurationSection players = data.getConfigurationSection("players"); if (players != null) for (String id : players.getKeys(false)) try {
            UUID uuid = UUID.fromString(id); wanted.put(uuid, players.getInt(id + ".wanted")); long until = players.getLong(id + ".jailed-until"); if (until > 0) jailedUntil.put(uuid, until);
            String gang = players.getString(id + ".gang"); if (gang != null) gangs.put(uuid, gang);
            String faction = players.getString(id + ".faction"); if (faction != null) factions.put(uuid, faction);
        } catch (IllegalArgumentException ignored) { }
        ConfigurationSection storedLeaders = data.getConfigurationSection("gang-leaders"); if (storedLeaders != null) for (String gang : storedLeaders.getKeys(false)) try { leaders.put(gang.toLowerCase(Locale.ROOT), UUID.fromString(storedLeaders.getString(gang, ""))); } catch (IllegalArgumentException ignored) { }
    }
    private void saveData() {
        data = new YamlConfiguration(); Set<UUID> ids = new HashSet<>(); ids.addAll(wanted.keySet()); ids.addAll(jailedUntil.keySet()); ids.addAll(gangs.keySet()); ids.addAll(factions.keySet());
        for (UUID id : ids) { String p = "players." + id; data.set(p + ".wanted", wanted.getOrDefault(id, 0)); data.set(p + ".jailed-until", jailedUntil.getOrDefault(id, 0L)); data.set(p + ".gang", gangs.get(id)); data.set(p + ".faction", factions.get(id)); }
        leaders.forEach((gang, leader) -> data.set("gang-leaders." + gang, leader.toString()));
        try { data.save(dataFile); } catch (IOException e) { getLogger().warning("Could not save players.yml: " + e.getMessage()); }
    }
    private int maxWanted() { return Math.max(1, getConfig().getInt("wanted.max-level", 5)); }
    private int level(UUID id) { return wanted.getOrDefault(id, 0); }
    private void setLevel(UUID id, int value) { wanted.put(id, Math.clamp(value, 0, maxWanted())); saveData(); }
    private boolean police(Player p) { boolean result = p.hasPermission("laworder.admin") || p.hasPermission("laworder.police"); if (result) factions.put(p.getUniqueId(), "POLICE"); return result; }
    private boolean criminal(Player p) { boolean result = p.hasPermission("laworder.admin") || p.hasPermission("laworder.criminal"); if (result && !police(p)) factions.put(p.getUniqueId(), "CRIMINAL"); return result; }
    private void msg(CommandSender s, String key, Map<String, String> values) { String text = getConfig().getString("messages." + key, "&cMissing message: " + key); for (var e : values.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue()); s.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "") + text)); }
    private void msg(CommandSender s, String key) { msg(s, key, Map.of()); }
    private Player target(CommandSender s, String[] args, int index, String usage) { if (args.length <= index) { msg(s, "usage", Map.of("usage", usage)); return null; } Player p = Bukkit.getPlayerExact(args[index]); if (p == null) s.sendMessage(ChatColor.RED + "Player must be online."); return p; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName(); if (!(sender instanceof Player p)) { msg(sender, "players-only"); return true; }
        switch (name) {
            case "duty" -> { if (!police(p)) return deny(p); if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) { msg(p,"usage",Map.of("usage","/duty <on|off>")); return true; } boolean on=args[0].equalsIgnoreCase("on"); duty.put(p.getUniqueId(),on); msg(p,on?"duty-on":"duty-off"); }
            case "wanted" -> { if (!police(p)) return deny(p); Player t=target(p,args,0,"/wanted <player>"); if(t==null)return true; setLevel(t.getUniqueId(),level(t.getUniqueId())+1); msg(p,"wanted-issued",vars(t)); }
            case "crime" -> { if (!criminal(p)) return deny(p); setLevel(p.getUniqueId(), level(p.getUniqueId()) + 1); msg(p,"crime",vars(p)); }
            case "hide" -> hide(p);
            case "arrest" -> arrest(p,args);
            case "fine" -> fine(p,args);
            case "gang" -> gang(p,args);
        } return true;
    }
    private boolean deny(Player p) { msg(p,"no-permission"); return true; }
    private Map<String,String> vars(Player p) { return Map.of("player",p.getName(),"level",String.valueOf(level(p.getUniqueId())),"max",String.valueOf(maxWanted())); }
    private void hide(Player p) { if (!criminal(p)) { deny(p); return; } if(level(p.getUniqueId()) == 0) { msg(p,"hide-no-wanted"); return; } double fee=getConfig().getDouble("hide.fee",0); if(fee>0 && !withdraw(p,fee)) { msg(p,"hide-no-money"); return; } if(Math.random() <= getConfig().getDouble("hide.success-chance",.45)) { setLevel(p.getUniqueId(),0); msg(p,"hide-success"); } else msg(p,"hide-failed"); }
    private void arrest(Player p,String[] args) { if(!police(p)) { deny(p); return; } Player t=target(p,args,0,"/arrest <player>"); if(t==null)return; if(level(t.getUniqueId())==0){msg(p,"not-wanted");return;} if(level(t.getUniqueId()) < maxWanted() && !duty.getOrDefault(p.getUniqueId(),false) && !p.hasPermission("laworder.admin")){msg(p,"duty-required");return;} long until=System.currentTimeMillis()+getConfig().getLong("jail.duration-minutes",15)*60_000L; jailedUntil.put(t.getUniqueId(),until); setLevel(t.getUniqueId(),0); teleportJail(t); saveData(); msg(p,"arrested",Map.of("player",t.getName(),"minutes",String.valueOf(getConfig().getLong("jail.duration-minutes",15)))); msg(t,"jailed",Map.of("minutes",String.valueOf(getConfig().getLong("jail.duration-minutes",15)))); }
    private void fine(Player p,String[] args) { if(!police(p)) { deny(p); return; } Player t=target(p,args,0,"/fine <player> <amount>"); if(t==null)return; if(args.length<2){msg(p,"usage",Map.of("usage","/fine <player> <amount>"));return;} try { double amount=Double.parseDouble(args[1]); if(amount<=0)throw new NumberFormatException(); if(!withdraw(t,amount)){msg(p,"vault-unavailable");return;} msg(p,"fine-success",Map.of("player",t.getName(),"amount",String.format(Locale.ROOT,"%.2f",amount))); } catch(NumberFormatException e){msg(p,"invalid-amount");} }
    private void gang(Player p,String[] a) { if(!criminal(p)){deny(p);return;} if(a.length==0){msg(p,"usage",Map.of("usage","/gang <create|invite|leave>"));return;} UUID id=p.getUniqueId(); switch(a[0].toLowerCase(Locale.ROOT)) {
        case "create" -> { if(a.length<2){msg(p,"usage",Map.of("usage","/gang create <name>"));return;} String g=a[1]; if(leaders.containsKey(g.toLowerCase(Locale.ROOT))){msg(p,"gang-exists");return;} if(gangs.containsKey(id)){msg(p,"gang-none");return;} gangs.put(id,g);leaders.put(g.toLowerCase(Locale.ROOT),id);saveData();msg(p,"gang-created",Map.of("gang",g)); }
        case "invite" -> { String g=gangs.get(id); if(g==null){msg(p,"gang-none");return;} if(!id.equals(leaders.get(g.toLowerCase(Locale.ROOT)))){msg(p,"gang-leader");return;} Player t=target(p,a,1,"/gang invite <player>");if(t==null)return; invitations.put(t.getUniqueId(),g);msg(p,"gang-invited",Map.of("player",t.getName())); msg(t,"gang-invitation",Map.of("gang",g)); }
        case "leave" -> { String g=gangs.remove(id);if(g==null){msg(p,"gang-none");return;} if(id.equals(leaders.get(g.toLowerCase(Locale.ROOT)))) leaders.remove(g.toLowerCase(Locale.ROOT)); saveData();msg(p,"gang-left"); }
        case "join" -> { String g=invitations.remove(id); if(g==null){msg(p,"gang-none");return;} gangs.put(id,g);saveData();msg(p,"gang-joined",Map.of("gang",g)); }
        default -> msg(p,"usage",Map.of("usage","/gang <create|invite|leave>"));
    }}
    private boolean withdraw(Player player, double amount) { try { Class<?> type=Class.forName("net.milkbowl.vault.economy.Economy"); RegisteredServiceProvider<?> registration=getServer().getServicesManager().getRegistration(type); if(registration==null)return false; Object economy=registration.getProvider(); Method has=economy.getClass().getMethod("has", org.bukkit.OfflinePlayer.class,double.class); if(!(Boolean)has.invoke(economy,player,amount))return false; Method withdraw=economy.getClass().getMethod("withdrawPlayer",org.bukkit.OfflinePlayer.class,double.class); Object response=withdraw.invoke(economy,player,amount); return (Boolean)response.getClass().getMethod("transactionSuccess").invoke(response); } catch (ReflectiveOperationException e) { return false; } }
    private void teleportJail(Player p) { World world=Bukkit.getWorld(getConfig().getString("jail.world","world")); if(world==null){getLogger().warning("Jail world is not loaded.");return;} p.teleport(new Location(world,getConfig().getDouble("jail.x"),getConfig().getDouble("jail.y"),getConfig().getDouble("jail.z"),(float)getConfig().getDouble("jail.yaw"),(float)getConfig().getDouble("jail.pitch"))); }
    private void releaseExpired() { long now=System.currentTimeMillis(); for(var entry:new HashMap<>(jailedUntil).entrySet()) if(entry.getValue()<=now) { jailedUntil.remove(entry.getKey()); Player p=Bukkit.getPlayer(entry.getKey()); if(p!=null&&p.isOnline()){p.teleport(p.getWorld().getSpawnLocation());msg(p,"released");} saveData(); } }
    @EventHandler public void onJoin(PlayerJoinEvent e) { if (!police(e.getPlayer()) && !criminal(e.getPlayer())) factions.put(e.getPlayer().getUniqueId(), "CIVILIAN"); saveData(); if(jailedUntil.getOrDefault(e.getPlayer().getUniqueId(),0L)>System.currentTimeMillis()) teleportJail(e.getPlayer()); else releaseExpired(); }
    @EventHandler public void onMove(PlayerMoveEvent e) { if(jailedUntil.getOrDefault(e.getPlayer().getUniqueId(),0L)>System.currentTimeMillis() && e.getTo()!=null && !sameBlock(e.getFrom(),e.getTo())) { e.setCancelled(true); } }
    private boolean sameBlock(Location a,Location b){return a.getBlockX()==b.getBlockX()&&a.getBlockY()==b.getBlockY()&&a.getBlockZ()==b.getBlockZ();}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a) { if(a.length==1 && List.of("wanted","arrest","fine").contains(c.getName())) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(a[0].toLowerCase(Locale.ROOT))).toList(); if(c.getName().equals("duty")&&a.length==1)return List.of("on","off"); if(c.getName().equals("gang")&&a.length==1)return List.of("create","invite","leave","join"); if(c.getName().equals("gang")&&a.length==2&&a[0].equalsIgnoreCase("invite"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(); return List.of(); }
}
