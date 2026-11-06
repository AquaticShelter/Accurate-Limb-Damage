# Limb Damage

Hit the leg and it hurts less than the chest, the way it works in Call of Duty.

Every body part has its own damage number. TACZ guns can have theirs per gun type, so a sniper can punish the
chest while a pistol barely scratches an arm. When you die you get a recap of who hit you, with what, and where.
And when you'd rather test your setup than wait for a fight, there's a training dummy you can dress up and shoot.

Works with vanilla melee and projectiles. TACZ and Accurate Hitboxes are optional, and make hit locations exact.

## What's in it

**Six hit zones.** Head, chest, abdomen, arms, legs and feet, each with its own multiplier. Arms, legs and feet
take 20% less by default, everything else is normal. Damage with no hit position (fall, fire, explosions,
potions) is left alone.

**Per gun type numbers (TACZ).** Pistol, rifle, sniper, RPG, SMG, shotgun, MG. Fill in only what you want to
change, everything else follows the global numbers.

**Death recap.** One strip per attacker with weapon, damage and hits, next to a body that shows where you got
hit. A second strip lists the players you had hurt, with a KILL or DEAD badge by their name.

**Training dummy.** `/limbdamage dummy` spawns one. Right-click it to put armor, items, a pumpkin, an elytra on
it, and set its health, damage reduction (0.1% steps), auto-heal and what happens when it dies. Hits show the
zone, multiplier and damage, and a report lists every shot once you stop.

**Fragile effect.** Arms, legs and feet take full damage and regeneration waits.

**Settings that make sense.** Everything is in a YACL screen: damage sliders, zones, the dummy, and how the HUD
looks. Damage reads as "damage taken off" in 0.1% steps (55.5% works). Servers use a plain file and
`/limbdamage reload`.

**Sharp HUD.** The panels are drawn in real screen pixels, so they stay crisp at any GUI scale. You can even
give the HUD your own font from a resource pack.

## Requirements

- Minecraft 1.20.1, Forge 47.4.10 or newer
- [YACL](https://modrinth.com/mod/yacl) 3.6.x (required)
- Optional: TACZ, Accurate Hitboxes

Install it on the server and on clients. The server decides the damage, clients draw the feedback.

## Coming from 1.x?

Install YACL, update the server and clients together, and your old settings are imported on first start.

## Help

Setup, every option and troubleshooting are in the wiki. If a zone looks wrong, `/limbdamage probe` and
`debug.logHits` in the config tell you why, and make a good bug report.
