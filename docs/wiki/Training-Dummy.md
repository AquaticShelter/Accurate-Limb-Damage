# Training dummy

`/limbdamage dummy [health]` spawns a dummy in front of you. It stands still, wears your skin, and takes limb
damage like a player.

## What you see when you hit it

- **Action bar:** the zone, the multiplier, and the damage, for every hit.
- **Report:** once you stop shooting for a few seconds, a strip lists every hit (zone, multiplier, damage). TACZ
  hits twice per bullet; those two parts show as one row.

Both can be turned off in the `dummy` section of the config.

## Equipment

Right-click the dummy to open its screen: helmet, chestplate, leggings, boots, main hand and off hand, with a
live preview. Any item fits any slot (a pumpkin on the head, an elytra, a gun in the hand). Armor on the dummy
reduces damage the normal way.

Crouch + right-click with an item in hand puts it straight on the right slot, with no screen.

Right-clicking with a TACZ gun in hand does nothing, so aiming at the dummy doesn't pop the menu up. Crouch +
right-click opens it.

By default anyone can change the equipment. Turn off `dummy.publicEquipment` to limit that to the owner and ops.

## Stats

The right side of the screen has sliders for each dummy:

- **Max health** (1 to 1024)
- **Damage reduction** (0 to 100%, in 0.1% steps). Applied last, after armor.
- **Auto-heal** (seconds without a hit before it heals, 0 = never)
- **On death:** default (follow the config), restore, respawn, or die

Only the dummy's owner and ops can change stats. New dummies start from the `dummy` section of the config.

## Death

Depending on the setting, a dummy that runs out of health heals back, dies for good, or dies and comes back after
a delay, with its equipment and stats.

`/limbdamage dummy clear` removes all dummies.
