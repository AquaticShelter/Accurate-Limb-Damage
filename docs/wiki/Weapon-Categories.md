# Weapon categories (TACZ)

With TACZ installed, every gun type can have its own zone numbers. A sniper can punish the chest while a pistol
barely scratches an arm.

Gun types: `pistol`, `rifle`, `sniper`, `rpg`, `smg`, `shotgun`, `mg`. The type of a gun is TACZ's own gun type.

## How the numbers work

For each type and zone there's one number:

- `-1` (shown as "use Damage tab" in the screen): use the global multiplier for that zone.
- `0` or more: use this multiplier for that gun type instead.

So you only fill in what you want to change. In `limbdamage-common.json5`:

```json5
weaponCategories: {
  enabled: true,
  values: {
    sniper: { head: 1.0, body: 1.0, abdomen: 1.0, arms: 0.9, legs: 0.9, feet: 0.9 },
    pistol: { head: -1, body: -1, abdomen: -1, arms: 0.6, legs: 0.6, feet: 0.6 }
  }
}
```

From the game: `/limbdamage setmult sniper arms 0.9`, or `inherit` instead of a number to clear it.

## Putting a gun in a different type

`gunTypeOverrides` takes entries like `"tacz:ak47=rifle"`. Use it when a pack labels a gun with the wrong type.

## Headshot options

- `headshotGuard` (on by default): TACZ's headshot flag follows this mod's verdict. Shoulder hits stop counting
  as headshots, and real headshots TACZ missed are granted.
- `headshotOverride` (off): ignore TACZ's own per-gun headshot multiplier, so only the head number here applies.

Turn `weaponCategories.enabled` off and every gun uses the global numbers.
