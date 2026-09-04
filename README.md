# Armed Pillagers

NeoForge 1.21.1 addon to F708's **Another Gun Mod** (`anothergunmod`), built for
the V10 pack. Hard-depends on it; does nothing without it.

## What it does

A slice of **newly spawned** pillagers carry a firearm instead of a crossbow:

| Weapon   | Default chance | Behaviour |
|----------|----------------|-----------|
| Revolver | 8%             | 6 rounds, 6 damage, ~0.75 s between shots |
| Rifle    | 5%             | 1 round, 12 damage, longest reach, reloads every shot |
| Shotgun  | **1.5%**       | 5 shells, 5 pellets x 4 damage, has to close to ~12 blocks |

Auto-guns, machine guns and flamethrowers are never handed out.

Vanilla pillager AI only knows how to work a crossbow, so a gun in the main hand
would leave the mob harmless. `GunAttackGoal` replaces `RangedCrossbowAttackGoal`
in the same priority slot: the pillager closes to its weapon's range, holds line
of sight, circles while it shoots, spends real rounds out of the gun's own ammo
container, and stands still through a full reload once empty. Damage, fire rate,
pellet count, magazine size and reload time all come from Another Gun Mod's own
config, so retuning the guns there retunes the pillagers with them.

Kill one and it can drop the gun (with most of its durability intact, unlike
vanilla's battered equipment drops), loose rounds of its own ammunition, and a
partly loaded magazine. All three need the kill credited to a player, exactly
like vanilla mob equipment.

Only pillagers spawned after the mod is installed are eligible - the roll is
marked during `finalizeSpawn`, so ones already saved in a world keep their
crossbows.

Everything is tunable in `config/armedpillagers-common.toml`.

## Building

```
./gradlew build
```
then copy `build/libs/armedpillagers-<version>.jar` into the pack's `mods/`.
`libs/anothergunmod-*.jar` is vendored for compilation only and is never bundled.

## Testing

`devtools/run-test.sh` runs one headless pass: it spawns a pillager and a
stationary 100 HP dummy on a force-loaded platform and prints `APTEST_*` lines
plus the mod's own DEBUG log as the fight plays out. A dummy kill proves
reloading works, since 100 HP is more than any of the three guns holds.
