---
title: Armed Pillagers — project
type: overview
layer: store
tags: [overview]
---

# Armed Pillagers

## What this is

A NeoForge 1.21.1 mod: a slice of newly spawned pillagers carry a firearm
instead of a crossbow, and know how to use it. Imported from nfx's 1.0.0,
relicensed AGPL-3.0-or-later with the author's sign-off, and rebuilt to the
MIT 6.031 bar: specs, rep invariants, a pure layer with tests, and a gametest
gate on a real server.

## Why it exists

The first mod the author adds to the shared server's pack, and the first
written to the standard every mod the author writes will carry: it doesn't
matter that it is a small Minecraft mod. Its second job was to be the first
consumer of the Ranged Weapons protocol, so that a future gun mod ("Yet
Another Gun Mod") is compatible the day it exists.

## Shape

Three source sets, one direction of dependency:

- `domain` — the decisions, over plain numbers: fire control (a state machine
  with a five-clause rep invariant proved against the original code), combat
  and drop rules, weighted choice, aim, loadout arithmetic. Compiled against
  the JDK only; a `net.minecraft` import is a compile error.
- `main` — adapters: the goal that reads a mob into the machine and applies
  its outputs, the arming and drops, the loadout table.
- `gametest` — a mod of its own with its own datapack (`decisions/D-0003.md`).

Which guns and how often is data: the `armedpillagers:pillager_loadouts` data
map, admitted per entry at reload only if the item is a weapon by the protocol
and its class is not denied by config. This mod names no gun mod. Another Gun
Mod is supported through an optional bridge subproject
(`decisions/D-0001.md`) and guarded data shipped here (`decisions/D-0002.md`).

## How it is verified

`./gradlew check`: 175 plain-JUnit tests against `domain`, and three gametests
on a headless server that pass with no gun mod present. `devtools/run-test.sh`
is the diagnostic harness through the bridge; its timeline (78-tick revolver
reloads, six rounds, ten `APTEST_*` markers) is what a behaviour change is
measured against.

## Depends on

- `minecraft-ranged-weapons` (nested Jar-in-Jar; built to Maven Local first).
- Another Gun Mod 1.5.4.2, vendored at `bridges/agm/libs/` for the bridge only;
  never redistributed.

## License

AGPL-3.0-or-later; see the protocol's `knowledge/decisions/D-0003.md` for why
the interface carries it too.
