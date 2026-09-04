/*
 * Armed Pillagers - pillagers that carry firearms and know how to use them.
 * Copyright (C) 2026 nfx and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nfx.armedpillagers;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Everything worth tuning without a rebuild.
 * Written to config/armedpillagers-common.toml on first run.
 */
public final class ApConfig {
    private ApConfig() {}

    public static final ModConfigSpec SPEC;

    // spawning
    public static final ModConfigSpec.DoubleValue REVOLVER_CHANCE;
    public static final ModConfigSpec.DoubleValue RIFLE_CHANCE;
    public static final ModConfigSpec.DoubleValue SHOTGUN_CHANCE;
    public static final ModConfigSpec.BooleanValue ARM_RAID_PILLAGERS;

    // combat
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SPREAD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue RELOAD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue REVOLVER_RANGE;
    public static final ModConfigSpec.DoubleValue RIFLE_RANGE;
    public static final ModConfigSpec.DoubleValue SHOTGUN_RANGE;

    // drops
    public static final ModConfigSpec.DoubleValue GUN_DROP_CHANCE;
    public static final ModConfigSpec.DoubleValue MAX_DROPPED_GUN_WEAR;
    public static final ModConfigSpec.DoubleValue AMMO_DROP_CHANCE;
    public static final ModConfigSpec.IntValue AMMO_DROP_MAX;
    public static final ModConfigSpec.DoubleValue MAGAZINE_DROP_CHANCE;
    public static final ModConfigSpec.IntValue MAGAZINE_MIN_ROUNDS;
    public static final ModConfigSpec.IntValue MAGAZINE_MAX_ROUNDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How often a newly spawned pillager carries a gun instead of a crossbow.",
                        "The three rolls are exclusive slices of one number, so the totals add up:",
                        "at the defaults roughly one pillager in seven is armed, and the shotgun is",
                        "the rare one. Existing pillagers already in the world are never re-armed.")
               .push("spawning");

        REVOLVER_CHANCE = builder
                .comment("Chance a pillager spawns with a revolver. Six shots, 6 damage, quick.")
                .defineInRange("revolverChance", 0.08D, 0.0D, 1.0D);

        RIFLE_CHANCE = builder
                .comment("Chance a pillager spawns with a rifle. Single shot, 12 damage, long reach.")
                .defineInRange("rifleChance", 0.05D, 0.0D, 1.0D);

        SHOTGUN_CHANCE = builder
                .comment("Chance a pillager spawns with a shotgun. Five pellets, devastating up close.",
                        "Deliberately the rarest of the three.")
                .defineInRange("shotgunChance", 0.015D, 0.0D, 1.0D);

        ARM_RAID_PILLAGERS = builder
                .comment("Whether pillagers spawned as part of a raid can be armed.",
                        "Set false to keep raids to crossbows and leave guns to patrols and outposts.")
                .define("armRaidPillagers", true);

        builder.pop();

        builder.comment("How a gun-armed pillager fights. Damage, fire rate, reload time and pellet",
                        "count all come from Another Gun Mod's own config - these are multipliers on",
                        "top of it, so retuning the guns there retunes the pillagers too.")
               .push("combat");

        DAMAGE_MULTIPLIER = builder
                .comment("Scales the gun's own damage when a pillager pulls the trigger.",
                        "1.0 means a pillager's rifle hits exactly as hard as yours (12).",
                        "Drop this if a patrol with two riflemen is more than you want to meet.")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 4.0D);

        SPREAD_MULTIPLIER = builder
                .comment("Scales shot spread. Higher is less accurate. 1.0 is already looser than a",
                        "standing player, so pillagers miss more than you do at the same range.")
                .defineInRange("spreadMultiplier", 1.0D, 0.1D, 8.0D);

        RELOAD_MULTIPLIER = builder
                .comment("Scales how long a pillager stands there reloading an empty gun.",
                        "That pause is the whole counterplay window - raise it to make guns fairer.")
                .defineInRange("reloadMultiplier", 1.0D, 0.25D, 8.0D);

        REVOLVER_RANGE = builder
                .comment("Blocks at which a revolver pillager stops closing and starts shooting.")
                .defineInRange("revolverRange", 16.0D, 4.0D, 64.0D);

        RIFLE_RANGE = builder
                .comment("Blocks at which a rifle pillager stops closing and starts shooting.")
                .defineInRange("rifleRange", 28.0D, 4.0D, 64.0D);

        SHOTGUN_RANGE = builder
                .comment("Blocks at which a shotgun pillager stops closing and starts shooting.",
                        "Short on purpose - it has to walk into your face to be dangerous.")
                .defineInRange("shotgunRange", 12.0D, 4.0D, 64.0D);

        builder.pop();

        builder.comment("What a dead armed pillager leaves behind. All of it needs the kill to be",
                        "credited to a player, exactly like vanilla mob equipment.")
               .push("drops");

        GUN_DROP_CHANCE = builder
                .comment("Chance the gun itself drops. Looting raises this the same way it raises any",
                        "mob's equipment drop. Vanilla equipment sits at 0.085 for comparison.")
                .defineInRange("gunDropChance", 0.10D, 0.0D, 1.0D);

        MAX_DROPPED_GUN_WEAR = builder
                .comment("Cap on how worn a dropped gun may be, as a fraction of its durability.",
                        "Vanilla batters dropped equipment down to near zero, which would make a rare",
                        "gun drop worthless; 0.6 guarantees at least 40% of the barrel left.",
                        "Set 1.0 for unmodified vanilla wear.")
                .defineInRange("maxDroppedGunWear", 0.6D, 0.0D, 1.0D);

        AMMO_DROP_CHANCE = builder
                .comment("Chance to also drop loose rounds for whichever gun it carried",
                        "(small bullets, big bullets or shells).")
                .defineInRange("ammoDropChance", 0.5D, 0.0D, 1.0D);

        AMMO_DROP_MAX = builder
                .comment("Upper bound on that ammo drop; the count is 1..this, uniformly.")
                .defineInRange("ammoDropMax", 3, 1, 64);

        MAGAZINE_DROP_CHANCE = builder
                .comment("Chance to drop a magazine, partly loaded with small bullets.",
                        "Magazines take small bullets only, so this is always a small magazine",
                        "regardless of which gun the pillager was carrying.")
                .defineInRange("magazineDropChance", 0.10D, 0.0D, 1.0D);

        MAGAZINE_MIN_ROUNDS = builder
                .comment("Fewest small bullets in that magazine.")
                .defineInRange("magazineMinRounds", 4, 0, 32);

        MAGAZINE_MAX_ROUNDS = builder
                .comment("Most small bullets in that magazine. A small magazine holds 32.")
                .defineInRange("magazineMaxRounds", 16, 0, 32);

        builder.pop();
        SPEC = builder.build();
    }
}
