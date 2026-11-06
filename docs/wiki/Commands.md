# Commands

All commands need op level 2. `/limbrecap` and `/limbdamage` are the same command.

| Command | What it does |
|---------|--------------|
| `/limbdamage dummy [health]` | spawn a training dummy in front of you |
| `/limbdamage dummy clear` | remove all dummies |
| `/limbdamage multipliers` | print the current numbers (global and per gun type) |
| `/limbdamage setmult <scope> <part> <value>` | change a multiplier and save it |
| `/limbdamage reload` | reload `limbdamage-common.json5` |
| `/limbdamage test [attackers]` | show a fake recap (1 to 6 attackers) |
| `/limbdamage testself` | fake recap of self-inflicted damage |
| `/limbdamage testreport` | fake dummy report |
| `/limbdamage testdealt` | fake "damage done" strip under the recap |
| `/limbdamage hit <limb> [damage]` | a real hit on yourself through the whole pipeline |
| `/limbdamage probe` | print how the zones read on your own hitbox |

**setmult**

- `scope`: `global` or a gun type (`pistol`, `rifle`, `sniper`, `rpg`, `smg`, `shotgun`, `mg`)
- `part`: `head`, `body`, `abdomen`, `arms`, `legs`, `feet`
- `value`: a number from 0 to 10, or `inherit` for gun types (clears the override)

**hit** limbs: `head`, `chest`, `abdomen`, `left_arm`, `right_arm`, `left_leg`, `right_leg`, `left_foot`,
`right_foot`, `arms`, `legs`, `feet`, `random`. Careful: it's a real hit, it can kill you.

Also useful: `/effect give @s limbdamage:fragile 10`.
