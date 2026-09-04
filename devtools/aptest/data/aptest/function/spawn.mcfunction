# A wide platform: the pillager circles while it shoots, and a small arena would
# have it strafe off the edge, lose line of sight and drop its target - which
# looks like a mod bug and is not one.
fill -20 100 -20 44 100 44 minecraft:stone
fill -20 101 -20 44 105 44 minecraft:air
# No NBT on the pillager: /summon only runs finalizeSpawn when the command has
# no NBT argument, and finalizeSpawn is what both the vanilla crossbow and this
# mod's gun roll hang off.
summon minecraft:pillager 2 101 12
# A stationary 100 HP dummy - more hit points than any of the three guns can
# empty into it in one magazine, so a kill proves reloading works. NoAI so it
# neither flees nor hits back.
summon minecraft:iron_golem 18 101 12 {PersistenceRequired:1b,NoAI:1b}
say APTEST_SPAWN
