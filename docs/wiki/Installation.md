# Installation

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer |
| Java | 17 |
| [YACL](https://modrinth.com/mod/yacl) | 3.6 or newer (required) |
| TACZ | optional, adds exact hit positions for guns and per-gun-type damage |
| Accurate Hitboxes | optional, hit zones from the real model bones |

YACL is the last 1.20.1 Forge line (3.6.x). It has to be installed on the server and on every client.

## Clients

Drop the Limb Damage jar and YACL into `mods/`. Settings are in *Mods > Limb Damage > Config*.

## Servers

Install the same two jars on the server. The server decides all damage, so the server's
`config/limbdamage-common.json5` is the file that counts. Edit it and run `/limbdamage reload`, no restart needed.

Install it on clients too. The network channel accepts clients without the mod (they just don't see the recap),
but the mod adds an effect, an entity and a menu, so depending on your setup Forge may refuse mismatched clients.

## Updating from 1.x

- Install YACL first.
- Update the server and all clients together. The network protocol changed in 2.0, so old and new versions
  can't talk to each other.
- Your old `limbdamage-common.toml` and `limbdamage-client.toml` are imported once on first start into the new
  `.json5` files. The old files are left alone, you can delete them afterwards.

## Removing the mod

Remove the jar. Nothing is written to your worlds apart from the training dummy entity and the Fragile effect, which
simply disappear.
