package com.danangell.treasurehunt;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TreasureHuntGame {
    private static final int TREASURE_MAX_DIST = 5000;
    private static final int TREASURE_DEAD_ZONE = 1000;
    private static final int TREASURE_MAX_HEIGHT = 70;
    private static final int TREASURE_MIN_HEIGHT = -50;
    private static final int TREASURE_HUNT_DURATION = 2 * 60 * 1000; // 10 minutes

    private static final List<ItemStack> TREASURE_ITEMS = Arrays.asList(
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.ELYTRA, 1),
            new ItemStack(Material.NETHERITE_INGOT, 1),
            new ItemStack(Material.NETHERITE_SWORD, 1),
            new ItemStack(Material.NETHERITE_PICKAXE, 1),
            new ItemStack(Material.NETHERITE_AXE, 1),
            new ItemStack(Material.NETHERITE_SHOVEL, 1),
            new ItemStack(Material.NETHERITE_HOE, 1),
            new ItemStack(Material.NETHERITE_HELMET, 1),
            new ItemStack(Material.NETHERITE_CHESTPLATE, 1),
            new ItemStack(Material.NETHERITE_LEGGINGS, 1),
            new ItemStack(Material.NETHERITE_BOOTS, 1));

    private TreasureHuntState state;
    private Date stateEnteredOn;

    private TreasureHunt plugin;
    private World world;
    private ThreadLocalRandom random;
    private @Nullable Block treasureSpot;

    public TreasureHuntGame(TreasureHunt plugin, World world) {
        this.plugin = plugin;
        this.state = TreasureHuntState.NOT_STARTED;
        this.world = world;
        this.random = ThreadLocalRandom.current();
    }

    public TreasureHuntState getState() {
        return this.state;
    }

    public void setState(TreasureHuntState state) {
        this.state = state;
        this.stateEnteredOn = new Date();
    }

    public long msInState() {
        Date now = new Date();
        return now.getTime() - this.stateEnteredOn.getTime();
    }

    public @Nullable Block getTreasureSpot() {
        return this.treasureSpot;
    }

    public void init() throws TreasureHuntException {
        this.treasureSpot = findTreasureSpot();
        ItemStack treasure = selectTreasure();

        int roundTo = 16;
        int xApprox = (this.treasureSpot.getX() / roundTo) * roundTo;
        int zApprox = (this.treasureSpot.getZ() / roundTo) * roundTo;

        String prompt = "";
        prompt += "Please give me flavor text for a treasure hunt describing a";
        prompt += " location where a chest is hidden in a Minecraft world.\n";
        prompt += "\n";
        prompt += "Details:\n";
        prompt += "Biome: " + this.treasureSpot.getBiome().toString().replace('_', ' ') + "\n";
        prompt += "X: ~" + xApprox + "\n";
        prompt += "Z: ~" + zApprox + "\n";
        prompt += "Height: " + heightDescription(this.treasureSpot.getY()) + "\n";
        prompt += "Contents: " + treasure.getType().toString().replace('_', ' ') + "\n";
        prompt += "\n";
        prompt += "Be concise. This should be no more than 3 sentences. Make sure to include the biome, ";
        prompt += "coordinates (and that they are approximate), height, and contents. This message is broadcast ";
        prompt += "to all online players, so tailor it accordingly.\n";

        String response;
        try {
            response = OpenAIClient.completion(prompt);
        } catch (IOException e) {
            throw new TreasureHuntException("Failed to get response from OpenAI API");
        }

        placeTreasure(this.treasureSpot, treasure);
        announce("TREASURE HUNT!", NamedTextColor.GREEN, Set.of(TextDecoration.BOLD));
        announce(response, NamedTextColor.GREEN, Set.of());
        announce(TREASURE_HUNT_DURATION / 1000 + " seconds to find the treasure!", NamedTextColor.RED, Set.of());

        setState(TreasureHuntState.IN_PROGRESS);
    }

    private void checkTreasure() {
        BlockState blockState = treasureSpot.getState();
        if (treasureSpot.getType() != Material.CHEST) {
            return;
        }

        Chest chest = (Chest) blockState;
        if (!chest.getInventory().isEmpty()) {
            return;
        }

        blockState.setType(Material.AIR);
        blockState.update(true);
        announce("The treasure has been claimed!", NamedTextColor.GREEN, Set.of());
        setState(TreasureHuntState.COMPLETED);
    }

    private void deleteTreasure() {
        BlockState blockState = treasureSpot.getState();
        if (treasureSpot.getType() != Material.CHEST) {
            return;
        }

        blockState.setType(Material.AIR);
        blockState.update(true);
        announce("The treasure has vanished!", NamedTextColor.RED, Set.of());
    }

    public void onTick() {
        switch (this.state) {
            case NOT_STARTED:
                break;
            case IN_PROGRESS:
                checkTreasure();

                if (msInState() > TREASURE_HUNT_DURATION) {
                    deleteTreasure();
                    setState(TreasureHuntState.COMPLETED);
                    break;
                }
                break;
            case COMPLETED:
                deleteTreasure();
                break;
        }
    }

    private String heightDescription(int y) {
        if (y < -40) {
            return "Close to bedrock";
        }
        if (y < 0) {
            return "Deepslate level";
        }
        if (y < 63) {
            return "Below ground";
        }

        return "Above ground";
    }

    private ItemStack selectTreasure() {
        return TREASURE_ITEMS.get(random.nextInt(TREASURE_ITEMS.size()));
    }

    private void placeTreasure(Block location, ItemStack treasure) throws TreasureHuntException {
        // Place a chest at the given location
        BlockState originalState = location.getState();
        originalState.setType(Material.CHEST);
        if (!originalState.update(true)) {
            throw new TreasureHuntException("Failed to place the chest");
        }

        // Put the treasure in the chest
        Chest chestState = (Chest) location.getState();
        int centerSlot = 13;
        chestState.getInventory().setItem(centerSlot, treasure);
    }

    /**
     * Get a block at the given coordinates, loading the chunk if necessary.
     */
    private Block getBlock(int x, int y, int z) {
        Block block = this.world.getBlockAt(x, y, z);
        Chunk chunk = block.getChunk();

        if (!chunk.isLoaded()) {
            chunk.load();
        }

        return block;
    }

    /**
     * Get a random location within the treasure hunt area.
     */
    private Location randomSpot() {
        int distanceRange = TREASURE_MAX_DIST - TREASURE_DEAD_ZONE;
        int x = random.nextInt(-distanceRange, distanceRange + 1);
        x += TREASURE_DEAD_ZONE * (x > 0 ? 1 : -1);

        int z = random.nextInt(-distanceRange, distanceRange + 1);
        z += TREASURE_DEAD_ZONE * (z > 0 ? 1 : -1);

        return new Location(this.world, x, 0, z);
    }

    /**
     * Find a location suitable for placing a chest in.
     *
     * @return Block with Material of AIR - or null if no spot was found.
     */
    private @Nullable Block findTreasureSpot() {
        Location location = randomSpot();
        Block block = getBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Chunk chunk = block.getChunk();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = TREASURE_MIN_HEIGHT; y < TREASURE_MAX_HEIGHT; y++) {
                    // Look for a solid block
                    Block baseBlock = chunk.getBlock(x, y, z);
                    Material baseMaterial = baseBlock.getType();
                    if (baseMaterial == Material.AIR) {
                        continue;
                    }
                    if (baseMaterial == Material.WATER) {
                        continue;
                    }
                    if (baseMaterial == Material.LAVA) {
                        continue;
                    }

                    // Look for an air block above it
                    Block chestBlock = chunk.getBlock(x, y + 1, z);
                    if (chestBlock.getType() != Material.AIR) {
                        continue;
                    }

                    return chestBlock;
                }
            }
        }

        return null;
    }

    /**
     * Announce a message to all players.
     */
    private void announce(String message, TextColor color, Set<TextDecoration> styles) {
        Component component = Component.text(message, color, styles);

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
