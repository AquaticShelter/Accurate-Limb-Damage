# FAQ and troubleshooting

**Nothing changes when I shoot.**
Check that the target counts (players, or a mob shaped like a player), that the damage has a hit position (not fall
damage, fire and so on), and that `weaponCategories` isn't overriding with -1 values on everything. Turn on
`debug.logHits` and shoot a dummy: the log shows the zone and multiplier of every hit.

**The head multiplier is 1.0. Why do headshots still do more?**
TACZ applies its own headshot bonus before this mod sees the damage. This mod only multiplies on top of that.
If you want to control it fully, enable `weaponCategories.headshotOverride`.

**A shoulder shot counted as a headshot.**
Leave `weaponCategories.headshotGuard` on (it is by default). It corrects TACZ's headshot flag to match the real
player model.

**The weapon icon in the recap is a plain item.**
Only TACZ guns have an HUD texture. Anything else uses the item's own icon.

**The config screen doesn't change my server.**
It edits the local files. Edit the server's `limbdamage-common.json5` and run `/limbdamage reload`.

**The HUD looks blurry or too small.**
Keep `rendering.pixelSnap` on and change `layout.scale`. The size is rounded to whole screen pixels, so
there are only a few real sizes at a given GUI scale.

**I can't join the server after updating.**
Server and clients must run the same major version (2.x), and YACL must be installed on both.

**Crash on start with "yet_another_config_lib_v3".**
YACL is missing or too old. Install the 1.20.1 Forge build 3.6.x.

**Reporting a bug.**
Open an issue with your Minecraft, Forge, Limb Damage, YACL, TACZ and Accurate Hitboxes versions, what you did,
and the `latest.log`. For wrong zones, `/limbdamage probe` output and a `debug.logHits` snippet help a lot.
