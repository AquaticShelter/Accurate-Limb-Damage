# Damage zones

A hit is sorted into one of six zones, and each zone has a damage multiplier.

| Zone | Default | Notes |
|------|---------|-------|
| Head | x1.0 | TACZ already adds its own headshot bonus, so this stays at 1.0 unless you want both |
| Chest | x1.0 | upper half of the torso (`body` in the config file) |
| Abdomen | x1.0 | lower half of the torso |
| Arms | x0.8 | both arms |
| Legs | x0.8 | both legs |
| Feet | x0.8 | the bottom of the legs, height set by `geometry.feetHeight` |

A multiplier of 0.8 means the part takes 80% of the damage. In the config screen the same number is shown as
"damage taken off": 20% for 0.8, a negative value for extra damage.

## Who gets it

Players, plus mobs that are built like a player (zombies, skeletons, villagers, piglins, and so on). The check
uses the hitbox shape, so it also catches modded humanoids. You can force or exclude entities with the
`extraEntityIds` and `blacklistEntityIds` lists.

## Which damage counts

Damage that has a hit position: bullets, arrows, tridents and other projectiles, and melee hits. Everything else
(fall damage, fire, drowning, potions, explosions) is left unchanged.

## How the hit position is found

- **TACZ bullets:** the exact point the bullet touched. With Accurate Hitboxes the bone that was hit is used.
- **Vanilla projectiles:** the impact point.
- **Melee:** where the attacker's aim line meets the target.
- **Without TACZ's exact data:** the mod follows the bullet's path through the player model instead of using one
  point, so grazing shots on a shoulder count as arm hits, not headshots.

Zone borders use the real Minecraft player model proportions by default, and scale with the target's size.
Crouching and swimming humanoids still count.

## Fragile effect

`limbdamage:fragile` (try `/effect give @s limbdamage:fragile 10`). While it's active, arms, legs and feet take
full damage (x1.0), and regeneration waits: the vanilla Regeneration effect pauses, natural regen is blocked, and
the "Regeneration tweak" mod's timer is pushed back. Each of those is a config option.
