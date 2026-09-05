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
package com.nfx.armedpillagers.weapon;

import com.f708.anothergunmod.registry.item.ModItems;
import com.nfx.armedpillagers.PillagerGun;
import net.minecraft.world.item.ItemStack;

/**
 * Finds the {@link RangedWeapon} or {@link AmmoStore} behind an item stack.
 *
 * <p>This is the one place the consumer asks "is this a weapon, and who
 * operates it?". Today it answers from the {@link PillagerGun} catalog; the
 * capability and data-map tiers plug in here, in precedence order, without
 * the callers changing.
 */
public final class RangedWeapons {
    private RangedWeapons() {}

    /**
     * effects: returns the weapon behind {@code stack}, or null if the stack
     * is empty or not a weapon any provider knows. Cheap enough to call every
     * tick.
     *
     * @param stack the stack in question
     * @return its weapon, or null
     */
    public static RangedWeapon resolve(ItemStack stack) {
        PillagerGun gun = PillagerGun.of(stack);
        return gun == null ? null : gun.weapon();
    }

    /**
     * effects: returns whether {@link #resolve} would find a weapon
     *
     * @param stack the stack in question
     * @return whether it is a weapon
     */
    public static boolean isWeapon(ItemStack stack) {
        return resolve(stack) != null;
    }

    /**
     * effects: returns the ammo store behind {@code stack} -- a detachable
     * magazine's own store, or the weapon itself since every weapon is one --
     * or null if it holds no rounds
     *
     * @param stack the stack in question
     * @return its store, or null
     */
    public static AmmoStore ammoStore(ItemStack stack) {
        if (!stack.isEmpty() && stack.is(ModItems.SMALL_MAGAZINE.get())) {
            return AgmMagazineStore.INSTANCE;
        }
        return resolve(stack);
    }
}
