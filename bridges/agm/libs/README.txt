Put anothergunmod-1.5.4.2.jar here to build the bridge.

It is F708's "Another Gun Mod", licensed All Rights Reserved, so it is not
redistributed with this source. Grab the same jar the pack uses (the jar name is
pinned by gunmod_jar in the root gradle.properties) and drop it in this folder.

The root settings.gradle includes the bridges:agm subproject only when the jar
is here; without it the main mod still builds and its tests still run, on the
protocol's fallback tier. With it, build.gradle takes the jar as compileOnly -
nothing from it is bundled into the built bridge - and the root project copies
it into run/mods so the dev runs can satisfy the bridge's hard dependency.
