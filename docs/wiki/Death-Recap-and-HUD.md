# Death recap and HUD

## The recap

When you die, a panel slides in from the corner of the screen:

- One strip per attacker: name, weapon, total damage, and number of hits.
- A body figure next to it. Each limb that took damage is coloured by how bad it was (white, yellow, orange,
  red, based on that limb's damage compared to your max health). Untouched limbs stay grey.
- Self-inflicted damage shows up with your own name, in a different colour.

A fight lasts until you go `recap.fightTimeoutSeconds` (30 by default) without taking damage.

## Damage done

A second strip appears under the recap with the players you had hit before you died, with the damage you did
to each and how many hits. Next to a name you may see a badge:

- **KILL**: you landed the final blow on that player.
- **DEAD**: they died, but someone else landed the last hit.

The badge never replaces the hit count. A death only counts once it has been confirmed, so mods that revive or
"down" players instead of killing them don't produce false KILL badges.

## The look

The HUD is laid out in real screen pixels, so edges stay sharp at any GUI scale. Curved edges are smoothed,
straight edges are not.

- `rendering.pixelSnap`: rounds the HUD size to whole pixels (recommended).
- `rendering.font`: any font id. A resource pack with a TTF font (for example `mypack:hud`) changes the HUD text.
- `rendering.smoothWeaponIcons`: smooths TACZ weapon icons when they're drawn smaller than their texture. It
  also softens that texture in TACZ's own screens.
- Layout, colours, animation timings and what each panel shows are all in the HUD tabs of the config screen.

Test it any time with `/limbdamage test`, `/limbdamage testself` and `/limbdamage testdealt`.
