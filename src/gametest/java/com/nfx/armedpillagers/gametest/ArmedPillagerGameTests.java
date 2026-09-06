/*
 * Armed Pillagers - pillagers that carry firearms and know how to use them.
 * Copyright (C) 2026 nfx and Rusty Shackleford
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
package com.nfx.armedpillagers.gametest;

import com.nfx.armedpillagers.ApConfig;
import com.nfx.armedpillagers.ArmedPillagers;
import com.nfx.armedpillagers.LoadoutTable;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The one end-to-end assertion: a pillager that spawns through the normal
 * path comes out holding a gun and fires it.
 *
 * <p>This is the gate every later change is measured against. It runs on a
 * real headless server ({@code ./gradlew runGameTestServer}) and its exit code
 * is the number of failed tests, so a red test fails the Gradle task. It is
 * deliberately protocol-agnostic: it asserts that the crossbow is gone and
 * that a dummy takes damage, not which bullet entity did it, so the weapon
 * backend can be swapped underneath it without touching the test.
 *
 * <p>The class has a public no-argument constructor and instance test
 * methods because the gametest registry instantiates the holder class
 * reflectively before invoking each test.
 */
@GameTestHolder(ArmedPillagers.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmedPillagerGameTests {
    /** Relative to the template origin. Layer 0 is the floor laid below. */
    private static final BlockPos PILLAGER = new BlockPos(2, 1, 4);
    private static final BlockPos DUMMY = new BlockPos(6, 1, 4);
    private static final int ARENA_SIZE = 9;

    public ArmedPillagerGameTests() {}

    /**
     * Spawn path: FinalizeSpawnEvent marks the pillager, EntityJoinLevelEvent
     * swaps its crossbow for a gun, the goal acquires the dummy and fires.
     *
     * @param helper the arena this test was given
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public void spawnedPillagerIsArmedAndFires(GameTestHelper helper) {
        // Force the roll the way devtools/test-config.toml does, but in
        // process: the config is loaded by the time any gametest runs, so
        // set() is legal here.
        ApConfig.ARMED_CHANCE.set(1.0D);

        // The template is an empty box. clearSpaceForStructure leaves its
        // layer 0 as air, so lay the floor the entities will stand on.
        layFloor(helper);

        // NOT helper.spawn(): that skips finalizeSpawn, and the whole arming
        // path hangs off it (the crossbow is handed out there, and so is our
        // mark). EntityType.spawn with a spawn type runs the full sequence.
        Pillager pillager = EntityType.PILLAGER.spawn(
                helper.getLevel(), helper.absolutePos(PILLAGER), MobSpawnType.COMMAND);
        if (pillager == null) {
            helper.fail("pillager did not spawn");
            return;
        }
        // EntityJoinLevelEvent fired synchronously inside spawn(), so the swap
        // has already happened by the time we get the entity back.
        if (pillager.getMainHandItem().is(Items.CROSSBOW)) {
            helper.fail("pillager kept its crossbow: arming did not run at join", pillager);
            return;
        }

        // A stationary target that neither flees nor hits back, the same
        // stand-in devtools/aptest/spawn.mcfunction uses.
        IronGolem dummy = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, DUMMY);
        float startingHealth = dummy.getHealth();

        helper.succeedWhen(() -> {
            if (!(dummy.getHealth() < startingHealth)) {
                helper.fail("dummy has not been hit", dummy);
            }
        });
    }

    /**
     * The fallback tier through this mod's own goal: a pillager handed a
     * profiled vanilla item -- no gun mod involved -- shoots it. Whatever
     * the spawn roll issued is overwritten; the goal reads the main hand
     * every tick, so the stick is what fires.
     *
     * @param helper the arena this test was given
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public void pillagerHandedAProfiledItemFiresIt(GameTestHelper helper) {
        layFloor(helper);
        Pillager pillager = EntityType.PILLAGER.spawn(
                helper.getLevel(), helper.absolutePos(PILLAGER), MobSpawnType.COMMAND);
        if (pillager == null) {
            helper.fail("pillager did not spawn");
            return;
        }

        ItemStack stick = new ItemStack(Items.STICK);
        RangedWeapon weapon = RangedWeapons.resolve(stick);
        if (weapon == null) {
            helper.fail("the gametest datapack's stick profile did not load");
            return;
        }
        int capacity = weapon.capacity(stick);
        weapon.load(stick, capacity);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, stick);

        IronGolem dummy = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, DUMMY);
        float startingHealth = dummy.getHealth();

        helper.succeedWhen(() -> {
            ItemStack held = pillager.getMainHandItem();
            helper.assertTrue(held.is(Items.STICK), "the pillager still holds the stick");
            helper.assertTrue(weapon.rounds(held) < capacity, "a round has been spent from the stick");
            helper.assertTrue(dummy.getHealth() < startingHealth, "the dummy has been hit");
        });
    }

    /**
     * Admission at reload: the gametest datapack lists a sidearm, an
     * automatic and an item with no profile. Only the first is a loadout.
     *
     * @param helper the arena this test was given
     */
    @GameTest(template = "arena")
    public void loadoutTableAdmitsOnlyWeaponsOfAllowedClasses(GameTestHelper helper) {
        List<Item> admitted = LoadoutTable.current().admitted();
        helper.assertTrue(admitted.contains(Items.STICK), "a profiled sidearm is admitted");
        helper.assertFalse(admitted.contains(Items.BONE), "an automatic is refused by the default deniedClasses");
        helper.assertFalse(admitted.contains(Items.DIAMOND), "an item with no profile is not a weapon");
        helper.succeed();
    }

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }
}
