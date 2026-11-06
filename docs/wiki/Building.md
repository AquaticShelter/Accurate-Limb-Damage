# Building from source

## Requirements

- JDK 17
- The Accurate Hitboxes jar for 1.20.1 in `libs/` (see `libs/README.md`). It's compile-only and never bundled.
- Internet for the first build: Forge, Parchment mappings, Mixin and YACL (`https://maven.isxander.dev/releases`).

## Build

```
./gradlew build
```

The jar lands in `build/libs/`. Run configs: `./gradlew runClient`, `./gradlew runServer`.

## Layout

| Package | What's in it |
|---------|--------------|
| `config` | YACL settings classes (`LimbSettings`, `ClientSettings`), the live `Val` views, TOML import |
| `event` | damage handlers: sort the hit, apply the multiplier |
| `compat` + `mixin` | TACZ and Accurate Hitboxes hooks (no compile-time TACZ dependency) |
| `recap` | damage log, the recap packet, commands |
| `recap.client` | the HUD (`RecapClient`, `UiDraw` vector painter) and its settings facade |
| `dummy` | the dummy entity, equipment menu, respawns |
| `dummy.client` | its renderer and screen |
| `client` | the YACL config screen (client only, never loaded on a server) |
| `effect` | the Fragile effect and regen handling |

Notes:

- Config values are read with `LimbConfig.X.get()`. Each one is a live view of the YACL settings object.
- Anything that touches `net.minecraft.client` or the YACL GUI must stay out of classes the server loads.
- Bump the network protocol in `RecapNetwork` whenever a packet changes.

## Releasing

1. Update `mod_version` in `gradle.properties` and `CHANGELOG.md`.
2. `./gradlew build`, then run the [testing checklist](Testing).
3. Tag the commit and attach the jar from `build/libs/`.
4. Paste the text from `docs/page/` into Modrinth and CurseForge.

## Logo

`branding/make_logo.py` regenerates the logo and banner (needs Pillow).
