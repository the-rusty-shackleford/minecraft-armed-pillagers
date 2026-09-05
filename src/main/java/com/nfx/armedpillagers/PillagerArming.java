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

import com.nfx.armedpillagers.domain.DropRules;
import com.nfx.rangedweapons.api.AmmoStore;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.armedpillagers.weapon.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Optional;

/**
 * Hands guns out, teaches every pillager how to use one, and decides what a
 * dead gunman leaves on the ground.
 *
 * Arming is two-stage on purpose. Pillager#finalizeSpawn hands itself a crossbow
 * and runs AFTER FinalizeSpawnEvent, so equipping there would just be
 * overwritten; instead the spawn event marks the mob and the join event - which
 * fires once the entity actually enters the level, after finalizeSpawn and after
 * a raid's applyRaidBuffs - does the swap. Marking at spawn rather than at join
 * is what keeps the roll to genuinely new pillagers: ones already saved in the
 * world keep the crossbows they were spawned with.
 *
 * Everything done to a weapon here -- filling it at spawn, filling a dropped
 * magazine -- goes through the {@link RangedWeapon} and {@link AmmoStore}
 * contracts; this class knows no gun mod.
 */
@EventBusSubscriber(modid = ArmedPillagers.MOD_ID)
public final class PillagerArming {
    private PillagerArming() {}

    /** Set at spawn, consumed at join. Its presence means "roll this one". */
    private static final String TAG_PENDING = "armedpillagers_pending";
    /** Which gun it got, so drops don't depend on the main hand still holding it. */
    private static final String TAG_GUN = "armedpillagers_gun";

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager)) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.EVENT && !ApConfig.ARM_RAID_PILLAGERS.get()) {
            return;
        }
        ArmedPillagers.LOGGER.debug("marking pillager {} for a gun roll (spawn type {})",
                pillager.getUUID(), event.getSpawnType());
        pillager.getPersistentData().putBoolean(TAG_PENDING, true);
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Pillager pillager)) {
            return;
        }

        CompoundTag data = pillager.getPersistentData();
        if (data.getBoolean(TAG_PENDING)) {
            data.remove(TAG_PENDING);
            arm(pillager, data);
        }

        // Given to every pillager, not just armed ones: the goal is inert without
        // a gun in hand, and this way a pillager handed one by a command or
        // another mod can still shoot it.
        addGoal(pillager);
    }

    private static void arm(Pillager pillager, CompoundTag data) {
        PillagerGun gun = PillagerGun.roll(pillager.getRandom());
        if (gun == null) {
            return;
        }
        ItemStack stack = new ItemStack(gun.gun());
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            ArmedPillagers.LOGGER.warn("catalog entry {} resolves to no weapon; pillager {} keeps its crossbow",
                    gun.id(), pillager.getUUID());
            return;
        }
        weapon.load(stack, weapon.capacity(stack));

        ArmedPillagers.LOGGER.debug("arming pillager {} with a {}", pillager.getUUID(), gun.id());
        pillager.setItemSlot(EquipmentSlot.MAINHAND, stack);
        pillager.setDropChance(EquipmentSlot.MAINHAND, ApConfig.GUN_DROP_CHANCE.get().floatValue());
        data.putString(TAG_GUN, gun.id());
    }

    private static void addGoal(Pillager pillager) {
        for (WrappedGoal wrapped : pillager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GunAttackGoal) {
                return;
            }
        }
        // Priority 3 is the slot vanilla gives RangedCrossbowAttackGoal, which is
        // exactly what this stands in for.
        pillager.goalSelector.addGoal(3, new GunAttackGoal(pillager, 1.0));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager) || !event.isRecentlyHit()) {
            return;
        }
        PillagerGun gun = PillagerGun.byId(pillager.getPersistentData().getString(TAG_GUN));
        if (gun == null) {
            return;
        }
        WeaponProfile profile = gun.profile();

        RandomSource random = pillager.getRandom();
        Level level = pillager.level();

        unbatterDroppedGun(event);

        Item ammoItem = item(profile.ammoItem());
        if (ammoItem != null && random.nextFloat() < ApConfig.AMMO_DROP_CHANCE.get()) {
            ItemStack ammo = new ItemStack(ammoItem);
            ammo.setCount(DropRules.ammoCount(ApConfig.AMMO_DROP_MAX.get(), ammo.getMaxStackSize(), random::nextInt));
            event.getDrops().add(drop(level, pillager, ammo));
        }

        Item magazineItem = item(profile.magazineItem());
        if (magazineItem != null && random.nextFloat() < ApConfig.MAGAZINE_DROP_CHANCE.get()) {
            ItemStack magazine = loadedMagazine(magazineItem, random);
            if (magazine != null) {
                event.getDrops().add(drop(level, pillager, magazine));
            }
        }
    }

    /**
     * Vanilla batters dropped equipment down to a sliver of durability, which
     * would make a rare gun drop worthless. Clamp the wear instead; the rule is
     * DropRules.clampedDamage, this just finds the weapons among the drops.
     */
    private static void unbatterDroppedGun(LivingDropsEvent event) {
        double cap = ApConfig.MAX_DROPPED_GUN_WEAR.get();
        if (cap >= 1.0D) {
            return;
        }
        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (!RangedWeapons.isWeapon(stack) || !stack.isDamageableItem()) {
                continue;
            }
            int clamped = DropRules.clampedDamage(stack.getDamageValue(), stack.getMaxDamage(), cap);
            if (clamped != stack.getDamageValue()) {
                stack.setDamageValue(clamped);
            }
        }
    }

    /**
     * A magazine partly filled with its own rounds, or null if no store knows
     * how to fill this item -- a magazine nobody can read is inert loot.
     */
    private static ItemStack loadedMagazine(Item magazineItem, RandomSource random) {
        ItemStack magazine = new ItemStack(magazineItem);
        AmmoStore store = RangedWeapons.ammoStore(magazine);
        if (store == null) {
            return null;
        }
        int rounds = DropRules.magazineRounds(
                ApConfig.MAGAZINE_MIN_ROUNDS.get(), ApConfig.MAGAZINE_MAX_ROUNDS.get(),
                store.capacity(magazine), random::nextInt);
        store.load(magazine, rounds);
        return magazine;
    }

    private static Item item(Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.ITEM::get).orElse(null);
    }

    private static ItemEntity drop(Level level, Pillager pillager, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, pillager.getX(), pillager.getY() + 0.5D, pillager.getZ(), stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
