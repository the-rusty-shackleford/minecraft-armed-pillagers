forceload add -32 -32 63 63
scoreboard objectives add ap dummy
scoreboard players set #tick ap 0
scoreboard players set #armed ap 0
scoreboard players set #bullet ap 0
scoreboard players set #dead ap 0
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule randomTickSpeed 0
gamerule doPatrolSpawning false
gamerule doTraderSpawning false
say APTEST_LOADED
