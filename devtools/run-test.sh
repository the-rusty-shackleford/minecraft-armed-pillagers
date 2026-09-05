#!/bin/sh
# One headless pass of the aptest harness: wipes the dev world, re-plants the
# datapack and the forced-chance config, and runs the server until the harness
# calls /stop at tick 800.
#
# The harness lives in devtools/ rather than run/world/datapacks so a world wipe
# cannot take it with it, and devtools/test-config.toml pins one weapon at 100%
# so every run arms the pillager - edit that file to test a different gun, and
# edit the item id in aptest's tick.mcfunction to match. Runs are not otherwise
# reproducible: every entity seeds its random source from the clock, so bullet
# spread, and with it the number of shots to a kill, varies from run to run.
# The reload lengths, the magazine structure and the APTEST_* milestones do not.
#
# Note the harness force-loads its arena. Without that the pillager circles out
# of the spawn chunks, stops ticking mid-fight and looks like it has jammed.
set -e
cd "$(dirname "$0")/.."
rm -rf run/world
mkdir -p run/world/datapacks run/config
cp -r devtools/aptest run/world/datapacks/
cp devtools/test-config.toml run/config/armedpillagers-common.toml

# report.mcfunction ends the run with /stop, a permission-level-4 command.
# Functions execute at function-permission-level, which vanilla defaults to 2,
# so without this the function fails to load and the server runs forever.
if [ -f run/server.properties ]; then
    sed -i 's/^function-permission-level=.*/function-permission-level=4/' run/server.properties
    grep -q '^function-permission-level=' run/server.properties \
        || echo 'function-permission-level=4' >> run/server.properties
else
    echo 'function-permission-level=4' > run/server.properties
fi

./gradlew runServer --console=plain
