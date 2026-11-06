# Upload checklist (Modrinth / CurseForge)

Not part of the mod. Notes for publishing a release.

**Files**

- Jar: `build/libs/limbdamage-1.20.1-<version>.jar`
- Icon: `branding/logo-512.png`
- Gallery / banner / GitHub social preview: `branding/banner-1280x640.png`
- Description: `modrinth.md` (Modrinth, Markdown) or `curseforge.html` (CurseForge)
- Short summary (under 256 characters): `summary.txt`
- Version changelog: `changelog-<version>.md`

**Metadata**

- Project name: Limb Damage. Slug: limb-damage (or whatever is free)
- Loader: Forge. Game version: 1.20.1
- License: GPL-3.0-only
- Side: client and server, required on both
- Dependencies: YACL (required); TACZ and Accurate Hitboxes (optional)
- Suggested categories: Modrinth: Game mechanics, Utility. CurseForge: Utility & QoL, Mobs

**Screenshots worth taking** (use `/limbdamage test 3`, `testdealt`, `testreport`)

1. The death recap with 3 attackers
2. The recap with the "damage done" strip under it
3. The dummy screen with armor and the sliders
4. A dummy report after a magazine
5. The config screen, Damage tab
6. The config screen, HUD tab

**Before publishing:** run the [testing checklist](wiki/Testing.md), make sure `mod_version` matches the tag, and
add the links to the wiki and the issue tracker on the project page.
