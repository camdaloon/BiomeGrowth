[![GitHub Downloads](https://img.shields.io/github/downloads/camdaloon/BiomeGrowth/total?logo=github&label=GitHub%20downloads)](https://github.com/camdaloon/BiomeGrowth/releases)

# Biome Growth

Paper plugin for configuring plant growth speed by biome.

## Compatibility

Designed as a single JAR for Paper **1.21.11 through 26.2**.

The project compiles against the 1.21.11 Paper API using Java 21 and sticks to Bukkit/Paper API calls that remain available across the requested versions.

## Commands

- `/plantspeedset <plantname> <biomename> <speed>`
- `/pss <plantname> <biomename> <speed>`

Only operators can use these commands.

## Speed values

The speed value is a relative growth-speed value: **higher = faster**. Plant names use the Minecraft seed/item name where applicable (for example `wheat_seeds`, `pumpkin_seeds`, `melon_seeds`, `beetroot_seeds`, `torchflower_seeds`, and `pitcher_pod`). Tree plants continue to use their sapling names, such as `oak_sapling`.

- `5` = Slow
- `20` = Normal
- `50` = Fast
- Any positive whole number is accepted.

The speed tab completion intentionally shows only `Slow`, `Normal`, and `Fast`. Those aliases map to 5, 20, and 50. Numbers such as `23` or `125` can still be typed manually.

## Biomes

Only Overworld biomes are available for customization right now. End and Nether customization is intentionally unavailable.

If an End or Nether biome is entered, the player receives:

`The End and Nether growth speed customization isn't available yet but will come soon!`

## Config

`plugins/BiomeGrowth/config.yml` starts enabled and includes every supported seed/crop type in every Overworld biome at the normal speed of `20`.

Rules are saved automatically when `/plantspeedset` is used.

## Default behavior

Any plant/biome combination without a configured rule keeps vanilla growth behavior.

Configured growth is adjusted through Bukkit/Paper growth events and simulated bone-meal growth for faster growth attempts. The plugin does not change the global `randomTickSpeed`, so one biome's rule does not affect unrelated plants or biomes.
