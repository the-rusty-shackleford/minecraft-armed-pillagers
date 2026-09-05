# Armed Pillagers — Another Gun Mod bridge

Binds F708's Another Gun Mod to the Ranged Weapons protocol natively, as a mod
of its own: `armedpillagers-agm-<version>.jar`, mod id `armedpillagers_agm`.

## What it does

On `RegisterCapabilitiesEvent` it walks the item registry and registers the
protocol's `WEAPON` capability on every gun the gun mod has -- so a gun added
in a later version of the mod is bridged without a change here -- and
`AMMO_STORE` on the small magazine. The flamethrower is left out: its jet is
not a bullet. With this jar in the pack a pillager's revolver fires the gun
mod's own bullets, carrying whatever effect the next round has, and its
magazine and loose rounds drop as the gun mod's items.

The provider declines a gun no pack has described in the
`rangedweapons:weapons` data map: spread, engagement range and sounds come from
the profile, and inventing them would arm a mob with numbers nobody chose. The
main mod ships profiles for the revolver, rifle and shotgun.

It depends on the protocol and on the gun mod, not on Armed Pillagers: any mod
that arms mobs through the protocol gets the gun mod's guns from this jar.

## Building

Drop `anothergunmod-1.5.4.2.jar` into `libs/` (see `libs/README.txt`; the name
is pinned by `gunmod_jar` in the root `gradle.properties`) and build from the
root: `./gradlew build` produces this jar beside the main one. Without the jar
the root `settings.gradle` skips this subproject with a notice. The gun mod is
compiled against only and never bundled.

## Testing

This is the one piece that cannot be unit-tested: its whole content is calls
into the gun mod's classes, which need a booted game to instantiate. It is
covered by the root project's gametest run when the jar is present (the run
hosts this mod and the gun mod, and the spawn test then fires through it) and
by the mcfunction harness, `devtools/run-test.sh`, which pins the revolver and
reads the gun mod's own bullet entity in its timeline.
