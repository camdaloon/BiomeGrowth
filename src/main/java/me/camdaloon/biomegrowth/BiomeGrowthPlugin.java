package me.camdaloon.biomegrowth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class BiomeGrowthPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.GREEN + "[Biome Growth] " + ChatColor.RESET;
    private static final List<String> SPEED_ALIASES = List.of("Slow", "Normal", "Fast");
    private static final Map<String, Integer> SPEED_VALUES = Map.of("slow", 5, "normal", 20, "fast", 50);

    // Biomes belonging to the Nether or End. They are deliberately excluded
    // from command validation and tab completion until those dimensions are supported.
    private static final Set<String> END_NETHER_BIOMES = Set.of(
            "the_end", "end_barrens", "end_highlands", "end_midlands", "small_end_islands", "the_void",
            "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas"
    );

    private final Map<String, PlantRule> rules = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRules();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("plantspeedset")).setExecutor(this);
        Objects.requireNonNull(getCommand("plantspeedset")).setTabCompleter(this);

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

                String plant = parts[0].toLowerCase(Locale.ROOT);
                String biome = parts[1].toLowerCase(Locale.ROOT);
                if (!isKnownOverworldBiome(biome)) {
                    getLogger().warning("Ignoring unsupported/non-overworld biome in plant-speeds entry: " + raw);
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

    private PlantRule findRule(Material material, String biomeKey) {
        return rules.get(key(material.name(), biomeKey));
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

        int speed = rule.speed();

        // 20 is the vanilla baseline. Lower values slow natural growth by chance.
        if (speed < 20) {
            if (Math.random() > speed / 20.0) event.setCancelled(true);
            return;
        }

        // Higher values allow the normal growth and add simulated bone-meal attempts.
        int extraAttempts = Math.max(0, speed / 20 - 1);
        for (int i = 0; i < extraAttempts; i++) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (block.getType() == event.getBlock().getType()) {
                    block.applyBoneMeal(BlockFace.UP);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrowth(StructureGrowEvent event) {
        if (!getConfig().getBoolean("enabled", true)) return;

        Block block = event.getLocation().getBlock();
        if (isEndOrNether(block)) return;

        PlantRule rule = findRule(block.getType(), biomeKey(block));
        if (rule == null) return;

        int speed = rule.speed();
        if (speed < 20) {
            if (Math.random() > speed / 20.0) event.setCancelled(true);
            return;
        }

        if (speed > 20 && block.getBlockData() instanceof Sapling) {
            event.setCancelled(true);
            int attempts = Math.max(1, (int) Math.ceil(speed / 20.0));
            for (int i = 0; i < attempts; i++) {
                if (block.getBlockData() instanceof Sapling && block.applyBoneMeal(BlockFace.UP)) {
                    break;
                }
            }
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

        String plant = args[0].toLowerCase(Locale.ROOT);
        String biome = args[1].toLowerCase(Locale.ROOT);

        Material material = Material.matchMaterial(plant);
        if (material == null || !material.isBlock()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown plant/block: " + args[0]);
            player.sendMessage(PREFIX + ChatColor.GRAY + "Use the first tab-completion list to see supported plant blocks.");
            return true;
        }

        if (!isPlantLike(material)) {
            player.sendMessage(PREFIX + ChatColor.RED + "That block is not a supported plant.");
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

        PlantRule rule = new PlantRule(plant, biome, speed);
        rules.put(key(plant, biome), rule);
        saveRules();

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
                .forEach(rule -> entries.add("-" + rule.plant() + " " + rule.biome() + " " + rule.speed()));
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

    private boolean isPlantLike(Material material) {
        String n = material.name();
        return n.endsWith("_SAPLING") || n.equals("BAMBOO") || n.equals("SUGAR_CANE") || n.equals("CACTUS")
                || n.endsWith("_CROP") || n.equals("WHEAT") || n.equals("CARROTS") || n.equals("POTATOES")
                || n.equals("BEETROOTS") || n.equals("COCOA") || n.equals("SWEET_BERRY_BUSH")
                || n.equals("KELP") || n.equals("KELP_PLANT") || n.equals("CAVE_VINES") || n.equals("CAVE_VINES_PLANT")
                || n.equals("TWISTING_VINES") || n.equals("WEEPING_VINES") || n.equals("CHORUS_FLOWER")
                || n.equals("MANGROVE_PROPAGULE") || n.equals("TORCHFLOWER_CROP") || n.equals("PITCHER_CROP");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(Material.values())
                    .filter(this::isPlantLike)
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
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
}
