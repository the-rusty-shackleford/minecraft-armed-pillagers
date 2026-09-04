#!/bin/sh
# One headless pass of the aptest harness: wipes the dev world, re-plants the
# datapack and the forced-chance config, and runs the server until the harness
# calls /stop.
#
# The harness lives in devtools/ rather than run/world/datapacks so a world wipe
# cannot take it with it, and devtools/test-config.toml pins one weapon at 100%
# so a run is deterministic - edit that file to test a different gun, and edit
# the item id in aptest's tick.mcfunction to match.
#
# Note the harness force-loads its arena. Without that the pillager circles out
# of the spawn chunks, stops ticking mid-fight and looks like it has jammed.
set -e
cd "$(dirname "$0")/.."
rm -rf run/world
mkdir -p run/world/datapacks run/config
cp -r devtools/aptest run/world/datapacks/
cp devtools/test-config.toml run/config/armedpillagers-common.toml
./gradlew runServer --console=plain
