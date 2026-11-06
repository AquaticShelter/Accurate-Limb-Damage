# Compatibility

## TACZ (Timeless and Classics Zero)

Optional. With it:

- bullets carry an exact hit position (the mod reads it from TACZ's own hit result),
- every gun type can have its own numbers,
- TACZ's headshot flag can follow this mod's zones.

There's no compile-time dependency on TACZ, and the TACZ hooks are skipped when it isn't installed.

## Accurate Hitboxes

Optional. With it, hits are sorted by the real bone that was hit. The mod was built against Accurate Hitboxes
2.3.1. If a bone verdict is physically impossible (a "head" hit at foot height), the sanity guards fix it.

## Regeneration tweak

If it's installed, Fragile pushes its after-damage timer back as well (`fragile.syncRegenTweakMod`).

## Revive / down-but-not-out mods

Handled. The damage-done strip only marks a player DEAD or KILL after the death is confirmed a tick later, so
revived players aren't shown as dead.

## Multiplayer

The server calculates damage and sends the recap to the player who died. Clients only draw it. The mod and YACL
must be on both sides.

## Other

The mod works in Forge's damage events at the lowest priority, so it runs after most other mods and sits fine
alongside armor and damage mods. If another mod changes damage after it, the recap shows what this mod saw.
