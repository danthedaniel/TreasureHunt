package com.danangell.treasurehunt;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TreasureHunt extends JavaPlugin implements Listener {
    private TreasureHuntGame game;
    private @Nullable Integer tickTaskId;

    @Override
    public void onEnable() {
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(this, this);

        TreasureHuntCommand command = new TreasureHuntCommand(this);
        this.getCommand("treasurehunt").setExecutor(command);
        this.getCommand("treasurehunt").setTabCompleter(command);

        registerTickHandler();
    }

    @Override
    public void onDisable() {
        clearTickHandler();
    }

    public void setGame(@NotNull TreasureHuntGame game) {
        this.game = game;
    }

    public void clearGame() {
        this.game = null;
    }

    public TreasureHuntGame getGame() {
        return this.game;
    }

    private void registerTickHandler() {
        if (this.tickTaskId != null) {
            throw new IllegalStateException("Tick handler is already registered!");
        }

        BukkitScheduler scheduler = this.getServer().getScheduler();
        TreasureHunt plugin = this;
        this.tickTaskId = scheduler.scheduleSyncRepeatingTask(this,
                new Runnable() {
                    public void run() {
                        plugin.onTick();
                    }
                }, 1l, 1l);
    }

    private void clearTickHandler() {
        if (this.tickTaskId == null) {
            return;
        }

        this.getServer().getScheduler().cancelTask(this.tickTaskId);
        this.tickTaskId = null;
    }

    private void onTick() {
        if (this.game == null) {
            return;
        }

        this.game.onTick();

        switch (this.game.getState()) {
            case COMPLETED:
            case FAILED:
                this.clearGame();
                break;
            default:
                break;
        }
    }
}
