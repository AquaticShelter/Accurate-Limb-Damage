# Changelog

## 2.0.0

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

### Project
- New logo and banner (`branding/`), wiki pages in `docs/wiki`, mod page text in `docs/page`, GitHub build workflow and issue templates.

### Fixes
- "Damage done" showing KILL for someone you only hit: deaths are confirmed a tick later and only if the player is really dead (revive mods cancel deaths). A hit on someone who died since your last hit starts fresh.
- Kill message could blame an old hit for a fire / explosion / fall death.
- A projectile's hit location could be used by an unrelated hit within 2 ticks.
- A limb saved for a hit that never became damage could be used by the next hit (same for the dummy).
- TACZ gun type from 2 ticks ago could apply to a melee or fire hit.
- One failed classification no longer turns off exact TACZ hit positions for the whole session.

## 1.10.0
Verified against the TACZ 1.1.8-hotfix2 jar (bytecode read), not guessed:
* **TACZ decides "headshot" from a vertical band only**: `eyeHeight - 0.25 < hit.y - feet < eyeHeight + 0.25` (about 1.37-1.87 for a
  player), with no lateral check and no knowledge of the model. That band includes the top ~0.6 px of the shoulders and ends 0.005 below the
  head's top edge. TACZ's `EntityHurtByGunEvent.Pre` exposes `setHeadshot(boolean)`; **headshotGuard now sets TACZ's flag to this mod's
  verdict in both directions** (false headshots on shoulders removed, real headshots TACZ missed granted), and the headshot multiplier
  read back by TACZ afterwards is the one the override cancels.
* **TACZ hurts the target twice per bullet** (a normal part and an armor-piercing part, `invulnerableTime` reset before each). The second part
  used to find no recorded location ("NO recorded location" in the log) and went through without the limb multiplier. A bullet's location now
  lives for both of its damage events and is only used for that bullet; the dummy folds the two parts into ONE report row.
* **Accurate Hitboxes inflates its bone boxes by up to 2 px**, so hits are registered up to 2 px above a shoulder or above the head. For a path
  that touches no real model box: arms get 2 px of extra height (a shoulder skim is an arm hit, never a headshot), the head gets 2 px (the very
  top edge is still a headshot), the head gets 1 px beside it. A path that really enters the head box is always a head hit. Accurate Hitboxes'
  own HEAD verdict is demoted when the real model says "not head" within 2 px of the neck line.
* Fallback location for TACZ bullets that have no recorded hit: where the shooter was aiming (generous box).
* The dummy's hitbox is now 0.6 x 1.8625 (the 1.8 player box plus the 0.0625 TACZ adds above a standing player) so the top pixel of its head can be hit.

New:
* **Dummy die & respawn** (`[dummy] dieAndRespawn`): the dummy really dies, the report (with the killing blow) is sent at once, and it comes back in the
  same spot / direction / skin / max health after `respawnDelaySeconds`.
* **"You dealt" toast** (`[dealtToast]`): a second toast under the death recap listing the players you had just damaged (the one who killed you first,
  then the most recent), up to 2, tracked for 30 s after your last hit on each, with damage, hits, weapon, limbs and KILL / DEAD tags. Nothing damaged
  = no toast. Client: `showDealtToast`, accent `colors.dealt`. Preview: `/limbdamage testdealt`.

## 1.9.1
* **Fix (shoulders counted as headshots):** TACZ grows the box it tests, so rim hits land far outside the vanilla hitbox
  (+-0.38 lateral in real logs). A path that touches no model box (the air above a shoulder, the strip beside the head) used to
  fall to a 3D "nearest box" that favours the 8 px deep head. It is now decided by the SILHOUETTE seen from the side the
  shot comes from (depth ignored), arms winning ties: shoulder = arm, head only where it really is.
* Dummy action bar / report show the damage actually DEALT: `raw x multiplier = after multiplier`, then `reduced to X` if armor
  or anything else lowered it, `DEALT X`, source, and the dummy's remaining hp. Hits with no recorded location are reported as
  "No hit location" instead of staying silent.
* Diagnostics (with `logHits = true`): headshot flag corrections (TACZ/AH said X, this mod says Y, hit height), headshot-guard
  cancellations, and hits that had no location.

## 1.9.0
* **Fix:** the top ~2-3 px of the torso and arms (the shoulders) were classified as HEAD / headshot. Point-only hits are now
  judged by a straight shot through the face they lie on instead of "nearest box" (which favoured the thicker head), and
  Accurate Hitboxes HEAD verdicts below the neck line are demoted. New `headshotGuard` cancels TACZ's headshot bonus when this
  mod decides a bullet was not a head hit.
* Dummy hit report: after `reportDelaySeconds` without hits, a toast lists every hit (limb, weapon, damage, multiplier + source), not stacked.
* `/limbdamage multipliers` and `/limbdamage setmult`; clearer `[weaponCategories]` documentation; `/limbdamage` alias.
* Dummy action bar and report show where each multiplier came from (global / category override / inherited / Fragile).
* Performance: toast renders from a one-time snapshot (no per-frame config reads or formatting); TACZ reflection resolved once and cached;
  Fragile tick fast-path; precomputed model boxes; `PlayerModel` extracted as pure math.
* Docs: README, docs/CONFIG.md.

## 1.8.0
TACZ weapon categories, headshot override, FEET part, ray classification, training dummy, safe event wrappers, per-world state clearing.

## 1.7.0 and earlier
Abdomen zone, left/right limbs, player-model hit zones, Fragile effect (+ regeneration delay), exact damage and recap toast, TACZ mixin fix.
