Put anothergunmod-1.5.4.2.jar here before building.

It is F708's "Another Gun Mod", licensed All Rights Reserved, so it is not
redistributed with this source. Grab the same jar the pack uses (the jar name is
pinned by gunmod_jar in gradle.properties) and drop it in this folder.

build.gradle takes it as compileOnly - nothing from it is bundled into the built
mod - and copies it into run/mods so ./gradlew runClient / runServer can satisfy
the hard dependency declared in neoforge.mods.toml.
