package me.camdaloon.biomegrowth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class BiomeGrowthPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.GREEN + "[Biome Growth] " + ChatColor.RESET;
    private static final List<String> SPEED_ALIASES = List.of("Slow", "Normal", "Fast");
    private static final Map<String, Integer> SPEED_VALUES = Map.of("slow", 5, "normal", 20, "fast", 50);
    private static final Map<String, Material> PLANT_BLOCKS = createPlantBlocks();
    private static final Set<String> END_NETHER_BIOMES = Set.of(
            "the_end", "end_barrens", "end_highlands", "end_midlands", "small_end_islands", "the_void",
            "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas"
    );
    private static final Map<Material, String> BLOCK_PLANT_NAMES = createBlockPlantNames();

    private final Map<String, PlantRule> rules = new HashMap<>();
    private final Set<BlockKey> acceleratedPlants = new HashSet<>();
    private final Deque<ChunkKey> pendingChunkRefreshes = new ArrayDeque<>();
    private final Set<ChunkKey> queuedChunkRefreshes = new HashSet<>();

    private static Map<String, Material> createPlantBlocks() {
        Map<String, Material> map = new LinkedHashMap<>();
        map.put("wheat_seeds", Material.WHEAT);
        map.put("carrot", Material.CARROTS);
        map.put("potato", Material.POTATOES);
        map.put("beetroot_seeds", Material.BEETROOTS);
        map.put("melon_seeds", Material.MELON_STEM);
        map.put("pumpkin_seeds", Material.PUMPKIN_STEM);
        map.put("torchflower_seeds", Material.TORCHFLOWER_CROP);
        map.put("pitcher_pod", Material.PITCHER_CROP);

        for (Material material : Material.values()) {
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.endsWith("_sapling") || name.equals("bamboo") || name.equals("sugar_cane")
                    || name.equals("cactus") || name.equals("cocoa") || name.equals("sweet_berry_bush")
                    || name.equals("kelp") || name.equals("cave_vines") || name.equals("twisting_vines")
                    || name.equals("weeping_vines") || name.equals("chorus_flower")
                    || name.equals("mangrove_propagule")) {
                map.putIfAbsent(name, material);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Material, String> createBlockPlantNames() {
        Map<Material, String> map = new EnumMap<>(Material.class);
        for (Map.Entry<String, Material> entry : PLANT_BLOCKS.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }
        map.put(Material.KELP_PLANT, "kelp");
        map.put(Material.CAVE_VINES_PLANT, "cave_vines");
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, String> LEGACY_PLANT_NAMES = Map.ofEntries(
            Map.entry("wheat", "wheat_seeds"),
            Map.entry("carrots", "carrot"),
            Map.entry("potatoes", "potato"),
            Map.entry("beetroots", "beetroot_seeds"),
            Map.entry("melon_stem", "melon_seeds"),
            Map.entry("pumpkin_stem", "pumpkin_seeds"),
            Map.entry("torchflower_crop", "torchflower_seeds"),
            Map.entry("pitcher_crop", "pitcher_pod"),
            Map.entry("kelp_plant", "kelp"),
            Map.entry("cave_vines_plant", "cave_vines")
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRules();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("plantspeedset")).setExecutor(this);
        Objects.requireNonNull(getCommand("plantspeedset")).setTabCompleter(this);

        Bukkit.getScheduler().runTaskTimer(this, this::tickAcceleratedPlants, 20L, 20L);
        queueLoadedChunkRefreshes();

        getLogger().info("Biome Growth enabled with " + rules.size() + " configured rule(s).");
    }

    private void loadRules() {
        rules.clear();
        List<String> entries = getConfig().getStringList("plant-speeds");
        for (String raw : entries) {
            String line = raw.trim();
            if (line.startsWith("-")) line = line.substring(1).trim();
            String[] parts = line.split("\\s+");
            if (parts.length != 3) {
                getLogger().warning("Ignoring invalid plant-speeds entry: " + raw);
                continue;
            }

            try {
                int speed = Integer.parseInt(parts[2]);
                if (speed <= 0) throw new NumberFormatException();

                String plant = normalizePlantName(parts[0]);
                String biome = parts[1].toLowerCase(Locale.ROOT);
                if (!isKnownOverworldBiome(biome)) {
                    getLogger().warning("Ignoring unsupported/non-overworld biome in plant-speeds entry: " + raw);
                    continue;
                }
                if (!PLANT_BLOCKS.containsKey(plant)) {
                    getLogger().warning("Ignoring unsupported plant in plant-speeds entry: " + raw);
                    continue;
                }
                rules.put(key(plant, biome), new PlantRule(plant, biome, speed));
            } catch (NumberFormatException ex) {
                getLogger().warning("Ignoring invalid speed in plant-speeds entry: " + raw);
            }
        }
    }

    private String key(String plant, String biome) {
        return plant.toLowerCase(Locale.ROOT) + "|" + biome.toLowerCase(Locale.ROOT);
    }

    private PlantRule findRule(Material material, String biome) {
        String plant = plantNameForBlock(material);
        if (plant == null) return null;
        return rules.get(key(plant, biome));
    }

    private String plantNameForBlock(Material material) {
        return BLOCK_PLANT_NAMES.get(material);
    }

    private String normalizePlantName(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        return LEGACY_PLANT_NAMES.getOrDefault(name, name);
    }

    private String biomeKey(Block block) {
        return block.getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private boolean isEndOrNether(Block block) {
        String environment = block.getWorld().getEnvironment().name();
        return environment.equals("THE_END") || environment.equals("NETHER");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalGrowth(BlockGrowEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;

        Block block = event.getBlock();
        if (isEndOrNether(block)) return;

        PlantRule rule = findRule(block.getType(), biomeKey(block));
        if (rule == null) return;

        acceleratedPlants.add(BlockKey.of(block));

        int speed = rule.speed();
        if (speed < 20 && Math.random() > speed / 20.0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlantPlaced(BlockPlaceEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;

        Block block = event.getBlockPlaced();
        if (isEndOrNether(block)) return;

        PlantRule rule = findRule(block.getType(), biomeKey(block));
        if (rule != null && rule.speed() > 20) {
            acceleratedPlants.add(BlockKey.of(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;
        queueChunkRefresh(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrowth(StructureGrowEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;

        Block block = event.getLocation().getBlock();
        if (isEndOrNether(block)) return;

        PlantRule rule = findRule(block.getType(), biomeKey(block));
        if (rule == null) return;

        if (rule.speed() < 20 && Math.random() > rule.speed() / 20.0) {
            event.setCancelled(true);
        }
    }

    private void tickAcceleratedPlants() {
        if (!getConfig().getBoolean("enabled", true)) return;

        refreshOneChunk();

        if (acceleratedPlants.isEmpty()) return;

        Iterator<BlockKey> iterator = acceleratedPlants.iterator();
        while (iterator.hasNext()) {
            BlockKey key = iterator.next();
            Block block = key.getBlock();

            if (block == null || !block.getChunk().isLoaded()) {
                iterator.remove();
                continue;
            }

            if (isEndOrNether(block)) {
                iterator.remove();
                continue;
            }

            String plant = plantNameForBlock(block.getType());
            if (plant == null) {
                iterator.remove();
                continue;
            }

            PlantRule rule = rules.get(key(plant, biomeKey(block)));
            if (rule == null || rule.speed() <= 20) {
                iterator.remove();
                continue;
            }

            int speed = rule.speed();
            double extraTicksPerSecond = (speed - 20) / 10.0;
            int extraTicks = (int) Math.floor(extraTicksPerSecond);
            if (Math.random() < extraTicksPerSecond - extraTicks) {
                extraTicks++;
            }

            for (int i = 0; i < extraTicks; i++) {
                if (!isBlockForPlant(plant, block.getType())) {
                    iterator.remove();
                    break;
                }
                block.randomTick();
            }
        }
    }

    private void refreshOneChunk() {
        ChunkKey key = pendingChunkRefreshes.pollFirst();
        if (key == null) return;
        queuedChunkRefreshes.remove(key);

        org.bukkit.World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) return;

        scanChunk(world.getChunkAt(key.x(), key.z()));
    }

    private void scanChunk(org.bukkit.Chunk chunk) {
        org.bukkit.World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();
                    String plant = plantNameForBlock(type);
                    if (plant == null) continue;

                    PlantRule rule = rules.get(key(plant, biomeKey(block)));
                    BlockKey blockKey = BlockKey.of(block);
                    if (rule != null && rule.speed() > 20) {
                        acceleratedPlants.add(blockKey);
                    } else {
                        acceleratedPlants.remove(blockKey);
                    }
                }
            }
        }
    }

    private boolean isBlockForPlant(String plant, Material material) {
        if (plant.equals("kelp")) return material == Material.KELP || material == Material.KELP_PLANT;
        if (plant.equals("cave_vines")) return material == Material.CAVE_VINES || material == Material.CAVE_VINES_PLANT;
        return PLANT_BLOCKS.get(plant) == material;
    }

    private void queueLoadedChunkRefreshes() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                queueChunkRefresh(chunk);
            }
        }
    }

    private void queueChunkRefresh(org.bukkit.Chunk chunk) {
        if (!chunk.isLoaded()) return;
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (queuedChunkRefreshes.add(key)) {
            pendingChunkRefreshes.addLast(key);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "This command can only be used by a player.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(PREFIX + ChatColor.RED + "You must be an operator to use this command.");
            return true;
        }

        if (args.length != 3) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /" + label + " <plantname> <biomename> <speed>");
            player.sendMessage(PREFIX + "Speed: " + ChatColor.GRAY + "Slow=5, Normal=20, Fast=50, or any positive number.");
            return true;
        }

        String plant = normalizePlantName(args[0]);
        String biome = args[1].toLowerCase(Locale.ROOT);

        if (!PLANT_BLOCKS.containsKey(plant)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown or unsupported plant/seed: " + args[0]);
            player.sendMessage(PREFIX + ChatColor.GRAY + "Use the first tab-completion list to see supported plant names.");
            return true;
        }

        if (isSpecialBiome(biome)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "The End and Nether growth speed customization isn't available yet but will come soon!");
            return true;
        }

        if (!isKnownOverworldBiome(biome)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown or unavailable biome: " + args[1]);
            return true;
        }

        int speed = parseSpeed(args[2]);
        if (speed <= 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Speed must be a positive whole number.");
            return true;
        }

        rules.put(key(plant, biome), new PlantRule(plant, biome, speed));
        saveRules();
        queueChunkRefresh(player.getChunk());
        queueLoadedChunkRefreshes();

        player.sendMessage(PREFIX + ChatColor.GREEN + "Set " + plant + " growth speed in " + biome + " to " + speed + ".");
        return true;
    }

    private int parseSpeed(String input) {
        Integer preset = SPEED_VALUES.get(input.toLowerCase(Locale.ROOT));
        if (preset != null) return preset;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void saveRules() {
        List<String> entries = new ArrayList<>();
        rules.values().stream()
                .sorted(Comparator.comparing(PlantRule::plant).thenComparing(PlantRule::biome))
                .forEach(rule -> entries.add(rule.plant() + " " + rule.biome() + " " + rule.speed()));
        getConfig().set("plant-speeds", entries);
        saveConfig();
    }

    private boolean isSpecialBiome(String biome) {
        return END_NETHER_BIOMES.contains(biome)
                || biome.equals("end")
                || biome.equals("nether")
                || biome.equals("the_nether");
    }

    private boolean isKnownOverworldBiome(String biome) {
        if (isSpecialBiome(biome)) return false;
        try {
            org.bukkit.block.Biome.valueOf(biome.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isSupportedPlantName(String name) {
        return PLANT_BLOCKS.containsKey(normalizePlantName(name));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return PLANT_BLOCKS.keySet().stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(org.bukkit.block.Biome.values())
                    .map(b -> b.name().toLowerCase(Locale.ROOT))
                    .filter(this::isKnownOverworldBiome)
                    .filter(b -> b.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return SPEED_ALIASES.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        return Collections.emptyList();
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block getBlock() {
            org.bukkit.World world = Bukkit.getWorld(worldId);
            return world == null ? null : world.getBlockAt(x, y, z);
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {}
}
