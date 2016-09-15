package com.codeski.simplyuhc;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.google.common.base.Joiner;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

public class SimplyUHC extends JavaPlugin implements Listener {
    private class SimplyTimer extends TimerTask {
        private int count = 0;

        @Override
        public void run() {
            if (count >= 60 && count % 6 == 0)
                server.broadcastMessage(count / 6 + " hours passed.");
            else if (count >= 60)
                server.broadcastMessage(count / 6 + " hours, " + count % 6 * 10 + " minutes passed.");
            else if (count > 0)
                server.broadcastMessage(count * 10 + " minutes passed.");
            else {
                for (Player p : players) {
                    SimplyUHC.this.healPlayer(p);
                    SimplyUHC.this.feedPlayer(p);
                    SimplyUHC.this.clearInventory(p);
                    SimplyUHC.this.unfreezePlayer(p);
                }
                world.setGameRuleValue("naturalRegeneration", "true");
                world.setGameRuleValue("doDaylightCycle", "false");
                world.setGameRuleValue("doMobSpawning", "false");
                scoreboard.registerNewObjective("deaths", "deathCount");
                scoreboard.getObjective("deaths").setDisplayName("Deaths");
                scoreboard.registerNewObjective("health", "health");
                scoreboard.getObjective("health").setDisplayName("Health");
                scoreboard.registerNewObjective("kills", "totalPlayerKills");
                scoreboard.getObjective("kills").setDisplayName("Kills");
                if (configuration.getString("display.belowName") != null)
                    scoreboard.getObjective(configuration.getString("display.belowName")).setDisplaySlot(DisplaySlot.BELOW_NAME);
                if (configuration.getString("display.list") != null)
                    scoreboard.getObjective(configuration.getString("display.list")).setDisplaySlot(DisplaySlot.PLAYER_LIST);
                if (configuration.getString("display.sidebar") != null)
                    scoreboard.getObjective(configuration.getString("display.sidebar")).setDisplaySlot(DisplaySlot.SIDEBAR);
                server.broadcastMessage("The game has been started. Good luck!");
            }
            ++count;
        }
    }

    private FileConfiguration configuration;
    private final PotionEffectType[] effects = {
        PotionEffectType.SLOW,
        PotionEffectType.SLOW_DIGGING,
        PotionEffectType.HEAL,
        PotionEffectType.REGENERATION,
        PotionEffectType.DAMAGE_RESISTANCE,
        PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.WATER_BREATHING,
        PotionEffectType.INVISIBILITY,
        PotionEffectType.BLINDNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.SATURATION
    };
    private boolean inProgress = false;
    private ArrayList<Player> players;
    private Server server;
    private Scoreboard scoreboard;
    private Timer timer;
    private World world;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("uhc"))
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /uhc <command>");
                sender.sendMessage(ChatColor.RED + "Commands: start stop");
                return true;
            } else if (args[0].equalsIgnoreCase("start")) {
                if (inProgress)
                    sender.sendMessage("There is already a game in progress.");
                else if (server.getOnlinePlayers().size() < 2)
                    sender.sendMessage("You need at least two players to start.");
                else {
                    int countdown = 30;
                    switch (args.length) {
                        case 3:
                            countdown = Math.abs(Integer.parseInt(args[2]));
                        case 2:
                            this.start(Math.abs(Integer.parseInt(args[1])), countdown * 20);
                            break;
                        default:
                            sender.sendMessage(ChatColor.RED + "Usage: /uhc start <size> [countdown]");
                    }
                }
                return true;
            } else if (args[0].equalsIgnoreCase("stop")) {
                if (!inProgress)
                    sender.sendMessage("There is no game in progress.");
                else
                    this.stop();
                return true;
            }
        return false;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        configuration = this.getConfig();
        configuration.options().copyDefaults(true);
        this.saveConfig();
        server = this.getServer();
        scoreboard = server.getScoreboardManager().getMainScoreboard();
        world = server.getWorld("world");
        server.getPluginManager().registerEvents(this, this);
        server.setDefaultGameMode(GameMode.SURVIVAL);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRuleValue("naturalRegeneration", "true");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setFullTime(0);
        for (Entity e : server.getWorld("world").getLivingEntities())
            if (e instanceof Monster)
                e.remove();
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!inProgress)
            return;
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMetadata = (SkullMeta) skull.getItemMeta();
        skullMetadata.setOwner(event.getEntity().getName());
        skullMetadata.setDisplayName(ChatColor.RESET + event.getEntity().getName());
        skull.setItemMeta(skullMetadata);
        event.getDrops().add(skull);
        players.remove(event.getEntity());
        event.getEntity().setGameMode(GameMode.SPECTATOR);
        if (players.size() < 2) {
            Firework firework = (Firework) world.spawnEntity(players.get(0).getLocation(), EntityType.FIREWORK);
            FireworkMeta fireworkMetadata = firework.getFireworkMeta();
            FireworkEffect fireworkEffect = FireworkEffect.builder().with(Type.BALL_LARGE).withColor(Color.YELLOW).withTrail().flicker(true).build();
            fireworkMetadata.addEffect(fireworkEffect);
            fireworkMetadata.setPower(0);
            firework.setFireworkMeta(fireworkMetadata);
            for (Player p : server.getOnlinePlayers())
                p.setGameMode(GameMode.SPECTATOR);
            server.broadcastMessage(players.get(0).getName() + " is the winner!");
            this.stop();
        }
    }

    @EventHandler
    public void onPlayerLogin(final PlayerLoginEvent event) {
        if (inProgress && !players.contains(event.getPlayer()))
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getEquipment().clear();
        player.getEquipment().setArmorContents(null);
    }

    private void feedPlayer(Player player) {
        player.setFoodLevel(20);
    }

    private void freezePlayer(Player player, int ticks) {
        ArrayList<PotionEffect> potions = new ArrayList<>();
        for (PotionEffectType type : effects)
            potions.add(new PotionEffect(type, ticks, Byte.MAX_VALUE));
        player.addPotionEffects(potions);
    }

    private void healPlayer(Player player) {
        player.setHealth(player.getMaxHealth());
    }

    private void start(int size, int countdown) {
        players = new ArrayList<>(server.getOnlinePlayers());
        world.getWorldBorder().setCenter(world.getSpawnLocation().getX(), world.getSpawnLocation().getZ());
        world.getWorldBorder().setSize(size);
        String[] names = new String[players.size()];
        for (int i = 0; i < names.length; ++i)
            names[i] = players.get(i).getName();
        int min = size / (int) (Math.sqrt(players.size()) + 1) - 1;
        server.dispatchCommand(server.getConsoleSender(), "spreadplayers " + world.getSpawnLocation().getBlockX() + " " + world.getSpawnLocation().getBlockZ() + " " + min + " " + size / 2 + " false " + Joiner.on(' ').join(names));
        for (Player p : players) {
            p.setGameMode(GameMode.SURVIVAL);
            this.freezePlayer(p, countdown);
        }
        server.broadcastMessage("Game begins in " + countdown / 20 + " seconds... Get ready!");
        timer = new Timer();
        timer.scheduleAtFixedRate(new SimplyTimer(), countdown / 20 * 1000, 600000);
        inProgress = true;
    }

    private void stop() {
        server.broadcastMessage("The game has been stopped.");
        inProgress = false;
        players.clear();
        timer.cancel();
        world.setGameRuleValue("naturalRegeneration", "true");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setFullTime(0);
        scoreboard.getObjective("deaths").unregister();
        scoreboard.getObjective("health").unregister();
        scoreboard.getObjective("kills").unregister();
    }

    private void unfreezePlayer(Player player) {
        for (PotionEffectType type : effects)
            player.removePotionEffect(type);
    }
}
