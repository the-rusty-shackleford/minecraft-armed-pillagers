scoreboard players add #tick ap 1
execute if score #tick ap matches 40 run function aptest:spawn
execute if score #tick ap matches 45 if entity @e[type=pillager] run say APTEST_PILLAGER_EXISTS
execute if score #tick ap matches 45 if entity @e[type=iron_golem] run say APTEST_DUMMY_EXISTS
execute if score #tick ap matches 45 if entity @e[type=pillager,nbt={HandItems:[{id:"minecraft:crossbow"}]}] run say APTEST_HAS_CROSSBOW
execute if score #armed ap matches 0 if entity @e[type=pillager,nbt={HandItems:[{id:"anothergunmod:revolver"}]}] run function aptest:armed
execute if score #bullet ap matches 0 if entity @e[type=anothergunmod:bullet] run function aptest:bullet
execute if score #tick ap matches 60.. if score #dead ap matches 0 unless entity @e[type=iron_golem] run function aptest:dead
execute if score #tick ap matches 780 if entity @e[type=pillager] run say APTEST_PILLAGER_ALIVE_END
execute if score #tick ap matches 780 if entity @e[type=pillager,nbt={HandItems:[{id:"anothergunmod:revolver"}]}] run say APTEST_STILL_ARMED_END
execute if score #tick ap matches 780 if entity @e[type=iron_golem] run say APTEST_DUMMY_ALIVE_END
execute if score #tick ap matches 800 run function aptest:report
