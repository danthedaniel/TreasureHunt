package com.danangell.treasurehunt;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lectern;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TreasureHuntGame {
    private static final int LECTERN_MAX_DIST = 5000;
    private static final int LECTERN_DEAD_ZONE = 1000;
    private static final int LECTERN_MIN_HEIGHT = 60;
    private static final int LECTERN_MAX_HEIGHT = 128;

    private static final int TREASURE_MIN_HEIGHT = -50;
    private static final int TREASURE_MAX_HEIGHT = 70;
    private static final int TREASURE_LECTERN_MIN_RADIUS = 100;
    private static final int TREASURE_LECTERN_MAX_RADIUS = 500;

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

    private CommandSender sender;
    private TreasureHunt plugin;
    private World world;
    private ThreadLocalRandom random;
    private @Nullable Block lecturnSpot;
    private @Nullable Block treasureSpot;
    private @Nullable ItemStack treasure;
    private @Nullable String bookContents;

    public TreasureHuntGame(CommandSender sender, TreasureHunt plugin, World world) {
        this.sender = sender;
        this.plugin = plugin;
        this.state = TreasureHuntState.NOT_STARTED;
        this.world = world;
        this.random = ThreadLocalRandom.current();
    }

    public TreasureHuntState getState() {
        return this.state;
    }

    public synchronized void setState(TreasureHuntState state) {
        this.state = state;
        this.stateEnteredOn = new Date();
    }

    private synchronized void setBookContents(String bookContents) {
        this.bookContents = bookContents;
    }

    /**
     * Get the number of milliseconds that the game has been in its current state.
     */
    public long msInState() {
        Date now = new Date();
        return now.getTime() - this.stateEnteredOn.getTime();
    }

    public @Nullable Block getTreasureSpot() {
        return this.treasureSpot;
    }

    public void init() throws TreasureHuntException {
        this.lecturnSpot = findTreasureSpot(randomLecturnChunk(), LECTERN_MIN_HEIGHT, LECTERN_MAX_HEIGHT);
        if (this.lecturnSpot == null) {
            throw new TreasureHuntException("Failed to find a spot for the lecturn");
        }

        this.plugin.getLogger().info("Placing lecturn at (" + this.lecturnSpot.getX() + ", "
                + this.lecturnSpot.getY() + ", " + this.lecturnSpot.getZ() + ")");

        this.treasureSpot = findTreasureSpot(randomChestChunk(this.lecturnSpot.getLocation()), TREASURE_MIN_HEIGHT,
                TREASURE_MAX_HEIGHT);
        if (this.treasureSpot == null) {
            throw new TreasureHuntException("Failed to find a spot for the treasure");
        }

        this.plugin.getLogger().info("Placing treasure at (" + this.treasureSpot.getX() + ", "
                + this.treasureSpot.getY() + ", " + this.treasureSpot.getZ() + ")");

        this.treasure = selectTreasure();

        int roundTo = 16;
        int xApprox = (this.treasureSpot.getX() / roundTo) * roundTo;
        int zApprox = (this.treasureSpot.getZ() / roundTo) * roundTo;

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Please give me flavor text for a treasure hunt describing a ");
        promptBuilder.append("location where a chest is hidden in a Minecraft world.\n");
        promptBuilder.append("\n");
        promptBuilder.append("Details:\n");
        promptBuilder.append("Biome: " + this.treasureSpot.getBiome().toString().replace('_', ' ') + "\n");
        promptBuilder.append("X: ~" + xApprox + "\n");
        promptBuilder.append("Z: ~" + zApprox + "\n");
        promptBuilder.append("Height: " + heightDescription(this.treasureSpot.getY()) + "\n");
        promptBuilder.append("Contents: " + this.treasure.getType().toString().replace('_', ' ') + "\n");
        promptBuilder.append("\n");
        promptBuilder.append("Be concise. This should be no more than 3 sentences. Make sure to include the biome, ");
        promptBuilder.append("coordinates (and that they are approximate), height, and contents. This message is broadcast ");
        promptBuilder.append("to all online players, so tailor it accordingly.\n");
        final String prompt = promptBuilder.toString();

        Plugin plugin = this.plugin;
        TreasureHuntGame game = this;

        BukkitScheduler scheduler = this.plugin.getServer().getScheduler();
        scheduler.runTaskAsynchronously(this.plugin, new Runnable() {
            public void run() {
                try {
                    String response = OpenAIClient.completion(prompt);
                    game.setBookContents(response);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to get response from OpenAI API");
                    game.setState(TreasureHuntState.FAILED);
                    return;
                }

                game.setState(TreasureHuntState.READY_TO_START);
            }
        });
    }

    private void startTreasureHunt() {
        placeLecturn(this.lecturnSpot, bookContents);

        try {
            placeTreasure(this.treasureSpot, treasure);
        } catch (TreasureHuntException e) {
            this.plugin.getLogger().warning("Failed to place treasure: " + e.getMessage());
            setState(TreasureHuntState.FAILED);
            return;
        }

        announce("TREASURE HUNT!", NamedTextColor.GREEN, Set.of(TextDecoration.BOLD));
        String announcement = "You will find directions to buried treasure at ";
        announcement += "(" + this.lecturnSpot.getX() + ", " + this.lecturnSpot.getY() + ", "
                + this.lecturnSpot.getZ() + ")";
        announce(announcement, NamedTextColor.GREEN, Set.of());
        announce(TREASURE_HUNT_DURATION / 1000 + " seconds to find the treasure!", NamedTextColor.RED, Set.of());

        setState(TreasureHuntState.IN_PROGRESS);
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

    private void placeLecturn(Block location, String bookContents) {
        // Place a lecturn at the given location
        BlockState originalState = location.getState();
        originalState.setType(Material.LECTERN);
        originalState.update(true);

        // Put the book in the lecturn
        Lectern lecturnState = (Lectern) location.getState();
        lecturnState.getInventory().setItem(0, new ItemStack(Material.WRITTEN_BOOK));
        BookMeta bookMeta = (BookMeta) lecturnState.getInventory().getItem(0).getItemMeta();
        bookMeta.setTitle("Treasure Hunt");
        bookMeta.setAuthor("Dungeon Master");

        for (Component page : PageBuilder.breakIntoPages(bookContents)) {
            bookMeta.addPages(page);
        }

        lecturnState.getInventory().getItem(0).setItemMeta(bookMeta);
    }

    private void checkTreasure() {
        BlockState chestState = treasureSpot.getState();
        if (treasureSpot.getType() != Material.CHEST) {
            return;
        }

        Chest chest = (Chest) chestState;
        if (!chest.getInventory().isEmpty()) {
            return;
        }

        chestState.setType(Material.AIR);
        chestState.update(true);
        announce("The treasure has been claimed!", NamedTextColor.GREEN, Set.of());
        setState(TreasureHuntState.COMPLETED);
    }

    private void deleteTreasure() {
        BlockState lecturnState = lecturnSpot.getState();
        if (lecturnSpot.getType() == Material.LECTERN) {
            lecturnState.setType(Material.AIR);
            lecturnState.update(true);
        }

        BlockState chestState = treasureSpot.getState();
        if (treasureSpot.getType() == Material.CHEST) {
            chestState.setType(Material.AIR);
            chestState.update(true);
            announce("The treasure has vanished!", NamedTextColor.RED, Set.of());
        }
    }

    public void onTick() {
        switch (this.state) {
            case NOT_STARTED:
                break;
            case READY_TO_START:
                startTreasureHunt();
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
            case FAILED:
                deleteTreasure();
                sender.sendMessage("Treasure hunt failed!");
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
    private Chunk randomLecturnChunk() {
        int distanceRange = LECTERN_MAX_DIST - LECTERN_DEAD_ZONE;
        int x = random.nextInt(-distanceRange, distanceRange + 1);
        x += LECTERN_DEAD_ZONE * (x > 0 ? 1 : -1);

        int z = random.nextInt(-distanceRange, distanceRange + 1);
        z += LECTERN_DEAD_ZONE * (z > 0 ? 1 : -1);

        Location location = new Location(this.world, x, 0, z);

        Block block = getBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return block.getChunk();
    }

    private Chunk randomChestChunk(Location lecternLocation) {
        int distanceRange = TREASURE_LECTERN_MAX_RADIUS - TREASURE_LECTERN_MIN_RADIUS;
        int x = random.nextInt(-distanceRange, distanceRange + 1);
        x += TREASURE_LECTERN_MIN_RADIUS * (x > 0 ? 1 : -1);

        int z = random.nextInt(-distanceRange, distanceRange + 1);
        z += TREASURE_LECTERN_MIN_RADIUS * (z > 0 ? 1 : -1);

        Location location = new Location(this.world, x, 0, z);
        location.add(lecternLocation);

        Block block = getBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return block.getChunk();
    }

    /**
     * Find a location suitable for placing a chest in.
     *
     * @return Block with Material of AIR - or null if no spot was found.
     */
    private @Nullable Block findTreasureSpot(Chunk chunk, int yMin, int yMax) {
        ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    Material baseMaterial = chunkSnapshot.getBlockType(x, y, z);
                    if (!baseMaterial.isSolid()) {
                        continue;
                    }

                    // Look for an air block above it
                    Material chestSpotMaterial = chunkSnapshot.getBlockType(x, y + 1, z);
                    if (chestSpotMaterial != Material.AIR) {
                        continue;
                    }

                    return chunk.getBlock(x, y + 1, z);
                }
            }
        }

        return null;
    }

    /**
     * Announce a message to all players.
     */
    private void announce(String message, TextColor color, Set<TextDecoration> styles) {
        this.plugin.getLogger().info(message);
        Component component = Component.text(message, color, styles);

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
