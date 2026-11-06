# Configuration

Edit in the game (*Mods > Limb Damage > Config*) or in two files in `config/`. Bad values are clamped when the
file loads, and the files are rewritten so new options show up in them.

## limbdamage-common.json5 (gameplay, server side)

| Section | What it holds |
|---------|---------------|
| `multipliers` | head, body, abdomen, arms, legs, feet. 1.0 = normal, 0.8 = takes 80% |
| `entities` | who gets limb damage: players, mobs, humanoid detection, always / never lists |
| `geometry` | zone borders, feet height, path classification, real proportions, sanity guards |
| `obbClassification` | Accurate Hitboxes tuning |
| `applyToMelee` | limb damage for melee hits |
| `recap` | death recap on/off, fight timeout, cap each hit to remaining health |
| `fragile` | how regeneration is handled while Fragile |
| `dummy` | defaults for new dummies |
| `weaponCategories` | per gun type numbers and the headshot options, see [Weapon categories](Weapon-Categories) |
| `dealtToast` | the "damage done" strip: on/off, how long a player stays listed, how many are shown |
| `debug` | `logHits` (log every hit) and `announceKills` (chat line on a kill) |

On a dedicated server, edit this file and run `/limbdamage reload`, or use `/limbdamage setmult`.

## limbdamage-client.json5 (HUD, client side)

| Section | What it holds |
|---------|---------------|
| `general` | the HUD on/off |
| `layout` | corner, margins, size, opacity, number of rows, name width |
| `content` | what each strip shows, damage decimals, mirror the figure |
| `bodyLevels` | when a limb turns yellow, orange, red |
| `animation` | slide, hold, count-up and fade timings |
| `rendering` | pixel snapping, HUD font, smooth weapon icons |
| `colors` | `#RRGGBB` values |

## The config screen

The screen edits the files on the machine you're playing on. In singleplayer and on LAN that's what the game
uses. On a dedicated server, the screen won't change the server's settings: edit the server's file instead.

Damage values show as "damage taken off" with 0.1 steps, so 55.5% works. In the file they're plain multipliers.
