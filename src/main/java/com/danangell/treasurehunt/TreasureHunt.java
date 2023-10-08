package com.danangell.treasurehunt;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TreasureHunt extends JavaPlugin implements Listener {
    private @Nullable TreasureHuntGame game;
    private @Nullable Integer tickTaskId;

    @Override
    public void onEnable() {
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(this, this);

        // Set OpenAI key
        String apiKey = this.getConfig().getString("openai_key");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OpenAI key is not set!");
        }
        OpenAIClient.setApiKey(apiKey);

        // Register command
        TreasureHuntCommand command = new TreasureHuntCommand(this);
        this.getCommand("treasurehunt").setExecutor(command);
        this.getCommand("treasurehunt").setTabCompleter(command);

        // Add config value for OpenAI key
        this.getConfig().addDefault("openai_key", "");
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();

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

    public @Nullable TreasureHuntGame getGame() {
        return this.game;
    }

    private void registerTickHandler() {
        if (this.tickTaskId != null) {
            throw new IllegalStateException("Tick handler is already registered!");
        }

        long tickDelay = 10l;
        BukkitScheduler scheduler = this.getServer().getScheduler();
        this.tickTaskId = scheduler.scheduleSyncRepeatingTask(this, () -> this.onTick(), tickDelay, tickDelay);
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
            case ERROR:
                this.clearGame();
                break;
            default:
                break;
        }
    }
}
