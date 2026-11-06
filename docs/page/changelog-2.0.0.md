## Limb Damage 2.0.0

**Breaking:** YACL (3.6+) is now required, on the server and on clients. The network protocol changed
(5 -> 6), so server and clients must update together. Old `limbdamage-*.toml` files are imported once.

### Config
- YACL now, and required (server and clients). Two files: `limbdamage-common.json5` and `limbdamage-client.json5`, plus an in-game config screen.
- Damage sliders go in 0.1 steps (55.5% works). Stored as multipliers like before.
- Weapon-type sliders have a "use Damage tab" position at the far left.
- `/limbdamage reload`.

### HUD
- New strip layout: header, one strip per attacker (name, weapon, DMG // HITS), body card.
- Drawn in real screen pixels in one batched pass instead of per-pixel fills. Text only at whole multiples of the font.
- Body figure: square head, lightly squared-off limbs.
- New options: pixel snapping, HUD font, smooth weapon icons.
- KILL / DEAD are badges by the name. The hits column always shows hits.

### Dummy
- Right-click opens a screen: armor, both hands, live preview. Gear shows on the dummy and survives respawns.
- Per-dummy health, damage reduction, auto-heal, death behaviour.

### Fixes
- "Damage done" showing KILL for someone you only hit: deaths are confirmed a tick later and only if the player is really dead (revive mods cancel deaths). A hit on someone who died since your last hit starts fresh.
- Kill message could blame an old hit for a fire / explosion / fall death.
- A projectile's hit location could be used by an unrelated hit within 2 ticks.
- A limb saved for a hit that never became damage could be used by the next hit (same for the dummy).
- TACZ gun type from 2 ticks ago could apply to a melee or fire hit.
- One failed classification no longer turns off exact TACZ hit positions for the whole session.
