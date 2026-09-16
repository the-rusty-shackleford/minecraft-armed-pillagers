# Armed Pillagers

Pillagers that carry firearms and know how to use them. NeoForge 1.21.1.

Works with any gun mod that speaks the [Ranged Weapons](../minecraft-ranged-weapons)
protocol -- natively through a bridge, or from a datapack profile alone -- and
names none of them. Out of the box it arms pillagers with F708's Another Gun
Mod when that mod is present; a bridge for it is built from this repo.

## What it does

A slice of **newly spawned** pillagers carry a firearm instead of a crossbow.
How often is config (`armedChance`, default 0.145 - about one in seven). Which
gun is the `armedpillagers:pillager_loadouts` data map, a weighted pick that
any datapack can edit; this jar ships:

| Weapon (mod)               | Weight | Share of armed pillagers | Overall at the default | Behaviour |
|----------------------------|--------|--------------------------|------------------------|-----------|
| Revolver (Another Gun Mod) | 80     | 29%                      | 4.2%                   | 6 rounds, 6 damage, ~0.75 s between shots |
| Pistol (Ranged Weapons)    | 60     | 22%                      | 3.2%                   | 15 rounds, 6 damage, one per 0.25 s |
| Rifle (Another Gun Mod)    | 50     | 18%                      | 2.7%                   | 1 round, 12 damage, longest reach, reloads every shot |
| Rifle (Ranged Weapons)     | 30     | 11%                      | 1.6%                   | 30 rounds, 12 damage, one per 0.3 s |
| Shotgun (Ranged Weapons)   | 20     | 7%                       | 1.1%                   | 6 shells, 6 pellets x 4, has to close to ~12 blocks |
| Shotgun (Another Gun Mod)  | 15     | 5%                       | 0.8%                   | 5 shells, 5 pellets x 4 damage, has to close to ~12 blocks |
| Scoped rifle (Ranged Weapons) | 12  | 4%                       | 0.6%                   | 30 rounds, 16 damage, reach to 64, one per second |
| Machine gun (Ranged Weapons) | 6    | 2%                       | **0.3%**               | 75 rounds at 7 a second, 6 damage -- and only with `automatic` taken off `deniedClasses` |

Each mod's rows are present only when that mod is loaded (a
`neoforge:mod_loaded` condition on the entry); with one mod alone its rows
share the whole armed chance among themselves. Lower damage is more common
by design; the machine gun is the rarest thing a pillager can carry, and is
refused by the default config besides.

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
and overridable by any datapack, no code required. The same file names each
gun's ammunition family, and Another Gun Mod's rounds are tagged into the
protocol's families here (`small_bullet` small, `big_bullet` medium, `shell`
shell, as optional tag entries, so nothing breaks without that mod): a gun on
the protocol that takes medium rounds takes a pillager's dropped rifle rounds.

Kill one and it can drop the gun (with most of its durability intact, unlike
vanilla's battered equipment drops), loose rounds of its own ammunition, and a
partly loaded magazine. All three need the kill credited to a player, exactly
like vanilla mob equipment.

Only pillagers spawned after the mod is installed are eligible - the roll is
marked during `finalizeSpawn`, so ones already saved in a world keep their
crossbows.

Everything is tunable in `config/armedpillagers-common.toml`.

Since 1.3, a pillager holding a protocol weapon exposes vanilla's `CROSSBOW_HOLD`
arm pose, including while idle, so Fresh Animations recognizes the held gun.
Non-weapons retain vanilla's pose selection. This is the illager's crossbow hold
for any protocol gun; it does not invent a separate one-handed illager animation.
The Another Gun Mod profiles also declare shared grip metadata (revolver one hand,
rifle/shotgun two), available to all consumers through Ranged Weapons 1.7 or later.

## Two jars, and which you need

**`armedpillagers-<version>.jar`** is the mod. It names no gun mod: everything
it does to a weapon goes through the [Ranged Weapons](../minecraft-ranged-weapons)
protocol, which is nested inside the jar. Any item with a protocol profile is a
weapon a pillager can be issued; the jar ships profiles and loadouts for Another
Gun Mod's revolver, rifle and shotgun, guarded so they only apply when that mod
is present.

**`armedpillagers-agm-<version>.jar`** is the Another Gun Mod bridge, built
from [bridges/agm/](bridges/agm/). It registers the protocol's capability on
every gun the gun mod has, so a pillager's revolver fires the gun mod's own
bullets, with the gun mod's own ammunition and effects. Without it the same
guns still work, on the protocol's fallback tier: a generic tracer that deals
the profile's damage. The bridge depends on the protocol and on the gun mod,
not on this mod, so any mod that arms mobs through the protocol gets it for
free.

## Building

```
./gradlew build
```

produces `build/libs/armedpillagers-<version>.jar` and, if Another Gun Mod's
jar is in `bridges/agm/libs/` (see the README there), also
`bridges/agm/build/libs/armedpillagers-agm-<version>.jar`. Without that jar the
bridge subproject is skipped with a notice and everything else builds and
tests as normal. The gun mod is compiled against only and never bundled.

## Testing

Three tiers, the first two run by `./gradlew check` (and so by `build`):

- `./gradlew test` -- plain JUnit against the `domain` source set, the pure
  layer: fire control, combat and drop rules, weighted choice, aim, loadout
  arithmetic. That source set is compiled against nothing but the JDK, so a
  `net.minecraft` import there is a compile error. Partitions are written at
  the top of each test class.
- `./gradlew runGameTestServer` -- gametests on a real headless server. The
  tests are a mod of their own (`src/gametest`) with their own datapack giving
  a few vanilla items profiles and loadouts, so they exercise the mod from
  outside, the way a pack author would, and pass with no gun mod present: a
  spawned pillager comes out armed and fires; a pillager handed a profiled
  stick shoots it on the fallback tier; the loadout table admits the sidearm
  and refuses the automatic and the item with no profile; protocol guns select
  crossbow hold while non-weapons keep their idle/aggressive vanilla poses. With the bridge
  present the same run hosts it and Another Gun Mod too. **The server's exit
  code is not the assertion** -- it is also zero when no test ran -- so the
  task reads the framework's own "All N required tests passed" line from
  `run/logs/latest.log` and fails without it. `-PskipGameTests` leaves it out
  of `check` for fast iteration on the pure tests.
- `devtools/run-test.sh` -- the diagnostic run, and the only one that needs
  the bridge: one headless pass in which a pillager pinned to the revolver by
  the harness datapack fights a stationary 100 HP dummy on a force-loaded
  platform, printing `APTEST_*` markers and the mod's DEBUG log as it plays
  out. A dummy kill proves reloading works, since 100 HP is more than any of
  the guns holds; the timeline (78-tick revolver reloads, six rounds) is what
  a behaviour change is measured against.

The companion Ranged Weapons Mod's `runPhotoBooth -PboothObservers`, with this
jar in its `run/booth/mods`, verifies the pose on a real observing client and
photographs it beside a player. The booth is also checked with Fresh Animations.
