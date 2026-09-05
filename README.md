# Armed Pillagers

NeoForge 1.21.1 addon to F708's **Another Gun Mod** (`anothergunmod`), built for
the V10 pack. Hard-depends on it; does nothing without it.

## What it does

A slice of **newly spawned** pillagers carry a firearm instead of a crossbow.
How often is config (`armedChance`, default 0.145 - about one in seven). Which
gun is the `armedpillagers:pillager_loadouts` data map, a weighted pick that
any datapack can edit; this jar ships:

| Weapon   | Weight | Share of armed pillagers | Overall at the default | Behaviour |
|----------|--------|--------------------------|------------------------|-----------|
| Revolver | 80     | 55%                      | 8%                     | 6 rounds, 6 damage, ~0.75 s between shots |
| Rifle    | 50     | 34%                      | 5%                     | 1 round, 12 damage, longest reach, reloads every shot |
| Shotgun  | 15     | 10%                      | **1.5%**               | 5 shells, 5 pellets x 4 damage, has to close to ~12 blocks |

A loadout is a request, not a guarantee. An entry is issued only if the item
is a weapon by the Ranged Weapons protocol - a gun mod provides for it, or a
`rangedweapons:weapons` profile describes it - and its class is not in
`deniedClasses` (default `automatic` and `flame`: sustained automatic fire from
a mob that never has to reload mid-burst is not a fight, and a flamethrower
sets the world alight). Every refusal is logged at reload with its reason,
along with the shares that were admitted.

To change the mix, ship a datapack with
`data/armedpillagers/data_maps/item/pillager_loadouts.json`:

```json
{ "values": { "anothergunmod:rifle": 100, "anothergunmod:revolver": 20 } }
```

Values are bare weights or `{"weight": n}`; `"replace": true` discards every
entry from packs before yours. `devtools/aptest` does exactly that to pin the
test harness to the revolver.

Vanilla pillager AI only knows how to work a crossbow, so a gun in the main hand
would leave the mob harmless. `GunAttackGoal` replaces `RangedCrossbowAttackGoal`
in the same priority slot: the pillager closes to its weapon's range, holds line
of sight, circles while it shoots, spends real rounds out of the gun's own ammo
container, and keeps moving through a full reload once empty - the reload is
the pause in fire, not in movement. Damage, fire rate, pellet count, magazine
size and reload time come from the gun in hand, so retuning the guns in their
own mod's config retunes the pillagers with them. Spread, engagement range and
the projectile's speed and lifetime come from the `rangedweapons:weapons` data
map - shipped in this jar at `data/rangedweapons/data_maps/item/weapons.json`
and overridable by any datapack, no code required.

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
