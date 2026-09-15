[![GitHub Downloads](https://img.shields.io/github/downloads/camdaloon/BiomeGrowth/total?logo=github&label=GitHub%20downloads)](https://github.com/camdaloon/BiomeGrowth/releases)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/biome-growth?logo=curseforge&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/bukkit-plugins/biome-growth)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/biomegrowth?logo=modrinth&label=Modrinth%20downloads)](https://modrinth.com/plugin/biomegrowth)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/138620?logo=spigotmc&label=Spigot%20downloads)](https://www.spigotmc.org/resources/biome-growth.138620/)

# Biome Growth

Paper plugin for configuring plant growth speed by biome.

## Compatibility

Designed as a single JAR for Paper **1.21.11 through 26.2**.

The project compiles against the 1.21.11 Paper API using Java 21 and sticks to Bukkit/Paper API calls that remain available across the requested versions.

## Commands

- `/plantspeedset <plantname> <biomename> <speed>`
- `/pss <plantname> <biomename> <speed>`
- `/plantspeedinfo <plantname> <biomename>`
- `/psi <plantname> <biomename>`

Only operators can use these commands.

`ALL` can be used as the biome in `/plantspeedset` or `/pss` to set the selected plant's speed for every Overworld biome.

`/plantspeedinfo` and `/psi` report the configured numerical speed for a plant and biome. If no custom rule exists, the plant uses vanilla growth.

## Speed values

The speed value is a relative growth-speed value: **higher = faster**.

Plant names use Minecraft seed/item names where applicable:

- `wheat_seeds`
- `carrot`
- `potato`
- `beetroot_seeds`
- `melon_seeds`
- `pumpkin_seeds`
- `torchflower_seeds`
- `pitcher_pod`

Tree plants use their sapling names, such as `oak_sapling`.

Other supported plants include:

- `bamboo`
- `cactus`
- `cave_vines`
- `chorus_flower`
- `cocoa`
- `kelp`
- `mangrove_propagule`
- `sugar_cane`
- `sweet_berry_bush`
- `twisting_vines`
- `weeping_vines`
- `sea_pickle`
- `brown_mushroom`
- `red_mushroom`
- `glow_berries`
- `seagrass`
- `firefly_bush`
- `azalea`
- `flowering_azalea`

`kelp_plant` and `cave_vines_plant` are handled internally as part of `kelp` and `cave_vines` and are not separate command identifiers. `tall_seagrass` is handled internally as part of `seagrass` and is not a separate command identifier.

- `5` = Slow
- `20` = Normal
- `50` = Fast
- Any positive whole number is accepted.

Tab completion shows `Slow`, `Normal`, and `Fast`. These map to 5, 20, and 50. Numbers such as `23` or `125` can still be typed manually.

## Biomes

Only Overworld biomes are available for customization right now. End and Nether customization is intentionally unavailable.

If an End or Nether biome is entered, the player receives:

`The End and Nether growth speed customization isn't available yet but will come soon!`

## Config

`plugins/BiomeGrowth/config.yml` starts enabled and includes every supported seed/crop type in every Overworld biome at the normal speed of `20`.

Rules are saved automatically when `/plantspeedset` or `/pss` is used.

## Default behavior

Any plant/biome combination without a configured rule keeps vanilla growth behavior.

Configured growth is adjusted through Bukkit/Paper growth events and additional natural random-tick opportunities. The plugin does not change the global `randomTickSpeed`, so one biome's rule does not affect unrelated plants or biomes.
