package com.danangell.treasurehunt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lectern;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TreasureHuntGame {
    private static final int TICKS_PER_SECOND = 20;
    private static final int TREASURE_HUNT_MINUTES = 15;

    private static final int LECTERN_MAX_DIST = 5000;
    private static final int LECTERN_DEAD_ZONE = 1000;
    private static final int LECTERN_MIN_HEIGHT = 60;
    private static final int LECTERN_MAX_HEIGHT = 200;

    private static final int TREASURE_MIN_HEIGHT = -50;
    private static final int TREASURE_MAX_HEIGHT = 70;
    private static final int TREASURE_LECTERN_MIN_RADIUS = 200;
    private static final int TREASURE_LECTERN_MAX_RADIUS = 400;

    private static final int PLACE_ATTEMPTS = 10;

    private static final List<ItemStack> TREASURE_ITEMS = List.of(
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

    private static final String BOOK_HINTS = "Hints:\n"
            + "1. The treasure is always in a cave if it's below the ground\n"
            + "2. The treasure is always within " + TREASURE_LECTERN_MAX_RADIUS + " blocks of the lecturn\n";

    private static final Set<Biome> CAVE_BIOMES = Set.of(
            Biome.DRIPSTONE_CAVES,
            Biome.LUSH_CAVES,
            Biome.DEEP_DARK);

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

    private List<Integer> scheduledTaskIds = new ArrayList<>();

    public TreasureHuntGame(
            CommandSender sender, TreasureHunt plugin,
            World world) {
        this.sender = sender;
        this.plugin = plugin;
        this.state = TreasureHuntState.NOT_STARTED;
        this.stateEnteredOn = new Date();
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
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            Chunk lecturnChunk = randomLecturnChunk();
            this.lecturnSpot = findLecturnSpot(lecturnChunk, LECTERN_MIN_HEIGHT, LECTERN_MAX_HEIGHT);
            if (this.lecturnSpot != null) {
                this.plugin.getLogger().info("Placing lecturn at (" + this.lecturnSpot.getX() + ", "
                        + this.lecturnSpot.getY() + ", " + this.lecturnSpot.getZ() + ")");
                break;
            }
        }
        if (this.lecturnSpot == null) {
            throw new TreasureHuntException("Failed to find a spot for the lecturn");
        }

        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            Chunk chestChunk = randomChestChunk(this.lecturnSpot.getLocation());
            this.treasureSpot = findChestSpot(chestChunk, TREASURE_MIN_HEIGHT, TREASURE_MAX_HEIGHT);
            if (this.treasureSpot != null) {
                this.plugin.getLogger().info("Placing treasure at (" + this.treasureSpot.getX() + ", "
                        + this.treasureSpot.getY() + ", " + this.treasureSpot.getZ() + ")");
                break;
            }
        }
        if (this.treasureSpot == null) {
            throw new TreasureHuntException("Failed to find a spot for the treasure");
        }

        this.treasure = selectTreasure();

        int xApprox = this.treasureSpot.getX() + this.random.nextInt(-8, 8);
        int zApprox = this.treasureSpot.getZ() + this.random.nextInt(-8, 8);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Please give me flavor text for a treasure hunt describing a ");
        promptBuilder.append("location where a chest is hidden in a Minecraft world.\n");
        promptBuilder.append("\n");
        promptBuilder.append("Details:\n");

        Block underChest = treasureSpot.getRelative(0, -1, 0);
        String surfaceDescription = surfaceDescription(underChest);
        String biomeDescription = biomeDescription(underChest);
        if (surfaceDescription != biomeDescription) {
            promptBuilder.append("Surface biome: " + surfaceDescription + "\n");
            promptBuilder.append("Biome at chest: " + biomeDescription + "\n");
        } else {
            promptBuilder.append("Biome: " + biomeDescription + "\n");
        }

        promptBuilder.append("X: ~" + xApprox + "\n");
        promptBuilder.append("Z: ~" + zApprox + "\n");
        promptBuilder.append("Height: " + heightDescription(this.treasureSpot) + "\n");
        promptBuilder.append("Contents: " + this.treasure.getType().toString().toLowerCase().replace('_', ' ') + "\n");
        promptBuilder.append("\n");
        promptBuilder.append("Be concise. This should be no more than 3 sentences. Make sure to include the biome, ");
        promptBuilder.append(
                "coordinates (and that they are approximate), height, and contents. This message is broadcast ");
        promptBuilder.append("to all online players, so tailor it accordingly.\n");
        final String prompt = promptBuilder.toString();

        BukkitScheduler scheduler = this.plugin.getServer().getScheduler();
        scheduler.runTaskAsynchronously(this.plugin, () -> {
            String response;
            try {
                response = OpenAIClient.completion(prompt);
                this.plugin.getLogger().info("Flavor text: " + response.replace("\n", " "));
            } catch (IOException e) {
                this.plugin.getLogger().warning("Failed to get response from OpenAI API");
                sender.sendMessage("Failed to start treasure hunt: " + e.getMessage());
                this.setState(TreasureHuntState.ERROR);
                return;
            }

            this.setBookContents(response);
            this.setState(TreasureHuntState.READY_TO_START);
        });
    }

    private void startTreasureHunt() {
        try {
            placeLecturn(this.lecturnSpot, this.bookContents);
            placeTreasure(this.treasureSpot, this.treasure);
        } catch (TreasureHuntException e) {
            this.plugin.getLogger().warning(e.getMessage());
            sender.sendMessage("Failed to start treasure hunt: " + e.getMessage());
            setState(TreasureHuntState.ERROR);
            return;
        }

        announce("TREASURE HUNT!", NamedTextColor.GREEN, Set.of(TextDecoration.BOLD));
        String announcement = "You will find directions to buried treasure resting on a lectern at ";
        announcement += "(" + this.lecturnSpot.getX() + ", " + this.lecturnSpot.getY() + ", "
                + this.lecturnSpot.getZ() + ")";
        announce(announcement, NamedTextColor.GREEN);
        announce("You have " + TREASURE_HUNT_MINUTES + " minutes to find the treasure!", NamedTextColor.RED);
        setState(TreasureHuntState.IN_PROGRESS);

        scheduleTasks();
    }

    private void scheduleTasks() {
        BukkitScheduler scheduler = this.plugin.getServer().getScheduler();
        List<Integer> warningTimes = List.of(5, 3, 2, 1); // minutes from end

        for (int warningTime : warningTimes) {
            int warningTaskId = scheduler.scheduleSyncDelayedTask(this.plugin, () -> {
                if (this.state != TreasureHuntState.IN_PROGRESS) {
                    return;
                }

                String unit = warningTime == 1 ? "minute" : "minutes";
                announce("You have " + warningTime + " " + unit + " to find the treasure!", NamedTextColor.RED);
            }, (TREASURE_HUNT_MINUTES - warningTime) * 60 * TICKS_PER_SECOND);
            scheduledTaskIds.add(warningTaskId);
        }

        int completionTaskId = scheduler.scheduleSyncDelayedTask(this.plugin, () -> {
            if (this.state != TreasureHuntState.IN_PROGRESS) {
                return;
            }

            setState(TreasureHuntState.COMPLETED);
        }, TREASURE_HUNT_MINUTES * 60 * TICKS_PER_SECOND);
        scheduledTaskIds.add(completionTaskId);
    }

    private void clearScheduledTasks() {
        BukkitScheduler scheduler = this.plugin.getServer().getScheduler();

        for (int taskId : scheduledTaskIds) {
            scheduler.cancelTask(taskId);
        }

        scheduledTaskIds = new ArrayList<>();
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

    private void placeLecturn(Block location, String bookContents) throws TreasureHuntException {
        // Place a lecturn at the given location
        BlockState originalState = location.getState();
        originalState.setType(Material.LECTERN);
        if (!originalState.update(true)) {
            throw new TreasureHuntException("Failed to place the lecturn");
        }

        // Put the book in the lecturn
        Lectern lecturnState = (Lectern) location.getState();
        lecturnState.getInventory().setItem(0, new ItemStack(Material.WRITTEN_BOOK));

        BookMeta bookMeta = (BookMeta) lecturnState.getInventory().getItem(0).getItemMeta();
        bookMeta.setTitle("Treasure Hunt");
        bookMeta.setAuthor("Dungeon Master");
        for (Component page : PageBuilder.breakIntoPages(bookContents)) {
            bookMeta.addPages(page);
        }
        for (Component page : PageBuilder.breakIntoPages(BOOK_HINTS)) {
            bookMeta.addPages(page);
        }

        lecturnState.getInventory().getItem(0).setItemMeta(bookMeta);
    }

    private void checkTreasure() {
        BlockState chestState = treasureSpot.getState();
        if (treasureSpot.getType() == Material.CHEST) {
            Chest chest = (Chest) chestState;
            if (!chest.getInventory().isEmpty()) {
                return;
            }

            chestState.setType(Material.AIR);
            chestState.update(true);
        }

        announce("The treasure has been claimed!", NamedTextColor.GREEN);
        setState(TreasureHuntState.COMPLETED);
    }

    private void deleteTreasure() {
        clearScheduledTasks();

        BlockState lecturnState = lecturnSpot.getState();
        if (lecturnSpot.getType() == Material.LECTERN) {
            lecturnState.setType(Material.AIR);
            lecturnState.update(true);
        }

        BlockState chestState = treasureSpot.getState();
        if (treasureSpot.getType() == Material.CHEST) {
            chestState.setType(Material.AIR);
            chestState.update(true);
            announce("The treasure has vanished!", NamedTextColor.RED);
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
                break;
            case COMPLETED:
                deleteTreasure();
                break;
            case ERROR:
                deleteTreasure();
                sender.sendMessage("Treasure hunt failed!");
                break;
        }
    }

    private String heightDescription(Block block) {
        int y = block.getY();
        if (y < -40) {
            return "close to bedrock";
        }
        if (y < 0) {
            return "deepslate level";
        }
        if (y < 63) {
            return "below ground";
        }

        byte skyLight = block.getLightFromSky();
        if (skyLight > 0) {
            return "ground level";
        }

        if (y > 90) {
            return "in a mountain";
        } else {
            return "in a hill";
        }
    }

    private String biomeDescription(Block block) {
        if (block.getType().toString().toUpperCase().contains("AMETHYST")) {
            return "an amethyst geode";
        }

        Biome biome = block.getBiome();
        if (biome == Biome.DEEP_DARK) {
            return "the Deep Dark";
        }

        return biome.toString().toLowerCase().replace('_', ' ');
    }

    private String surfaceDescription(Block block) {
        Block topBlock = new Location(block.getWorld(), block.getX(), 319, block.getZ()).getBlock();
        return topBlock.getBiome().toString().toLowerCase().replace('_', ' ');
    }

    private ItemStack selectTreasure() {
        return TREASURE_ITEMS.get(random.nextInt(TREASURE_ITEMS.size()));
    }

    /**
     * Get a chunk containing the given coordinates, loading it if necessary.
     */
    private Chunk getChunk(Location location) {
        Block block = this.world.getBlockAt(location);
        Chunk chunk = block.getChunk();

        if (!chunk.isLoaded()) {
            chunk.load();
        }

        return chunk;
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

        return getChunk(location);
    }

    private Chunk randomChestChunk(Location lecternLocation) {
        int distanceRange = TREASURE_LECTERN_MAX_RADIUS - TREASURE_LECTERN_MIN_RADIUS;
        int x = random.nextInt(-distanceRange, distanceRange + 1);
        x += TREASURE_LECTERN_MIN_RADIUS * (x > 0 ? 1 : -1);

        int z = random.nextInt(-distanceRange, distanceRange + 1);
        z += TREASURE_LECTERN_MIN_RADIUS * (z > 0 ? 1 : -1);

        Location location = new Location(this.world, x, 0, z);
        location.add(lecternLocation);

        return getChunk(location);
    }

    /**
     * Find a location suitable for placing a lecturn on.
     *
     * @return Block with Material of AIR - or null if no spot was found.
     */
    private @Nullable Block findLecturnSpot(Chunk chunk, int yMin, int yMax) {
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

                    // Make sure there's sky light to ensure the lecturn is not underground
                    if (chunkSnapshot.getBlockSkyLight(x, z, y) == 0) {
                        continue;
                    }

                    return chunk.getBlock(x, y + 1, z);
                }
            }
        }

        return null;
    }

    /**
     * Find a location suitable for placing a chest on.
     *
     * @return Block with Material of AIR - or null if no spot was found.
     */
    private @Nullable Block findChestSpot(Chunk chunk, int yMin, int yMax) {
        ChunkSnapshot chunkSnapshot = chunk.getChunkSnapshot(false, true, false);

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

                    boolean isCaveBiome = CAVE_BIOMES.contains(chunkSnapshot.getBiome(x, y, z));
                    boolean isAmethystGeode = baseMaterial.toString().toUpperCase().contains("AMETHYST");
                    if (!(isCaveBiome || isAmethystGeode)) {
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
    @SuppressWarnings("unused")
    private void announce(String message) {
        announce(message, NamedTextColor.WHITE);
    }

    private void announce(String message, TextColor color) {
        announce(message, color, Set.of());
    }

    private void announce(String message, TextColor color, Set<TextDecoration> styles) {
        this.plugin.getLogger().info(message);
        Component component = Component.text(message, color, styles);

        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
