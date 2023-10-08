package com.danangell.treasurehunt;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

import org.bukkit.Server;
import org.bukkit.command.Command;

public class TreasureHuntCommand implements CommandExecutor, TabCompleter {
    private TreasureHunt plugin;

    public TreasureHuntCommand(TreasureHunt treasureHunt) {
        this.plugin = treasureHunt;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("start", "status", "stop");
        }

        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }

        if (args[0].equals("start")) {
            sender.sendMessage("Starting a treasure hunt...");
            if (this.plugin.getGame() != null) {
                sender.sendMessage("Treasure hunt already in progress!");
                return true;
            }

            Server server = this.plugin.getServer();
            TreasureHuntGame game = new TreasureHuntGame(this.plugin, server.getWorld("world"));
            this.plugin.setGame(game);
            try {
                game.init();
            } catch (TreasureHuntException e) {
                sender.sendMessage("Failed to start treasure hunt: " + e.getMessage());
                this.plugin.setGame(null);
                return true;
            }

            sender.sendMessage("Treasure placed at (" + game.getTreasureSpot().getX() + ", "
                    + game.getTreasureSpot().getY() + ", " + game.getTreasureSpot().getZ() + ")");

            return true;
        }

        if (args[0].equals("status")) {
            if (this.plugin.getGame() == null) {
                sender.sendMessage("No treasure hunt in progress!");
                return true;
            }

            TreasureHuntGame game = this.plugin.getGame();
            sender.sendMessage("Treasure hunt is " + game.getState().toString().toLowerCase() + " for "
                    + game.msInState() / 1000 + " seconds.");
            return true;
        }

        if (args[0].equals("stop")) {
            if (this.plugin.getGame() == null) {
                sender.sendMessage("No treasure hunt in progress!");
                return true;
            }

            this.plugin.getGame().setState(TreasureHuntState.COMPLETED);
            sender.sendMessage("Treasure hunt stopped.");
            return true;
        }

        return false;
    }
}