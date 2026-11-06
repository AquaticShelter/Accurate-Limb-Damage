# Contributing

Bug reports and pull requests are welcome.

## Reporting a bug

Use the bug report form. The most useful things are your versions (Minecraft, Forge, Limb Damage, YACL, TACZ,
Accurate Hitboxes), the steps, and `logs/latest.log`. For a wrong zone, add the output of `/limbdamage probe`
and a few lines from `debug.logHits`.

## Building

JDK 17, the Accurate Hitboxes jar in `libs/`, then `./gradlew build`. See `docs/wiki/Building.md`.

## Pull requests

- Keep one change per pull request, and say what you tested. The checklist in `docs/wiki/Testing.md` helps.
- Fix the cause, not the symptom, and leave unrelated code alone.
- Server code must not load client classes (anything in `net.minecraft.client`, the YACL GUI, the HUD).
- New options go in `LimbSettings` or `ClientSettings`, get a sensible default, a `@SerialEntry` comment, a check
  in `sanitize()`, a `Val` in the facade, and an entry in the config screen. Don't rename existing keys.
- If a packet changes, bump the protocol number in `RecapNetwork`.
- Add a line to `CHANGELOG.md` under the next version.

## License

By contributing you agree that your work is released under the GPL-3.0-only license of this project.
