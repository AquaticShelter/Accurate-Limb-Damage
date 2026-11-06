# Testing checklist

For checking a build or a config by hand. Set `debug.logHits = true` in `limbdamage-common.json5` (then
`/limbdamage reload`) so every hit prints its zone and multiplier to the log. TACZ and Accurate Hitboxes are
best, with a sniper or DMR (easiest to aim). The training dummy (`/limbdamage dummy`) is the easiest target.

1. **Legs:** shoot the lower half. Log shows `LEGS x0.8`, damage down 20%.
2. **Chest / abdomen:** shoot the middle. `BODY` or `ABDOMEN x1.0`, damage unchanged.
3. **Arms:** shoot the outer edge at chest height. `ARMS x0.8`. With Accurate Hitboxes also shoot an arm that
   sticks out past the vanilla hitbox (running, aiming). It must still say ARMS.
4. **Head:** `HEAD x1.0`. Damage keeps TACZ's headshot bonus, with nothing extra on top.
5. **Neck line:** shoot at neck height, centred. TACZ and this mod must agree on headshot or not.
6. **Shoulders:** shoot the shoulder or a raised arm at head height. `ARMS`, not `HEAD`. Compare the damage with
   a centre-chest shot at the same range: it should be about 0.8 times that, nowhere near headshot damage.
7. **Front-on hits:** shoot straight at the middle of the chest and of the head of a still mob. Never `ARMS`.
8. **Vanilla bow:** repeat 1 to 4 with a bow. The position comes from the arrow's impact.
9. **Melee:** hit the dummy at different heights. Zones follow where you aim.
10. **Fragile:** `/effect give @s limbdamage:fragile 30`, take arm and leg hits: x1.0. Natural regen pauses.
11. **Recap:** `/limbdamage test 3`, then die for real to a friend. Strips, weapon icons and body colours show.
12. **Damage done:** hit a player, die, check the second strip. Kill them and die: KILL badge. Let someone else
    kill them: DEAD badge. The hits column always shows the count.
13. **Dummy:** equip it, change the sliders, shoot it, let it die with each death mode, check the gear comes back.
14. **Server:** run steps 1 to 4 on a dedicated server, change a number in the file, `/limbdamage reload`, shoot again.

If a zone looks wrong, post `/limbdamage probe` output and the `logHits` lines in an issue.
