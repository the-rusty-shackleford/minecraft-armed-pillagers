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

import com.f708.anothergunmod.core.enums.WeaponType;
import com.f708.anothergunmod.registry.item.ModItems;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.nfx.rangedweapons.api.AmmoStore;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the {@link RangedWeapon} or {@link AmmoStore} behind an item stack.
 *
 * <p>This is the one place the consumer asks "is this a weapon, and who
 * operates it?". Another Gun Mod's guns are answered first, natively, through
 * the gun mod's own bullets and ammunition -- provided the
 * {@code rangedweapons:weapons} data map describes the gun, since spread,
 * range and sounds come from there -- and everything else is the protocol's
 * to answer, whose fallback tier operates any item with a profile. The
 * precedence is the protocol's own: code that knows the item beats data
 * describing it.
 *
 * <p>The flamethrower is left to the protocol on purpose: its jet is not a
 * bullet, so the gun mod's bullet builder is the wrong tool for it.
 */
public final class PillagerWeapons {
    private PillagerWeapons() {}

    // One AgmWeapon per gun item, made on first sight. Bounded by the gun
    // mod's gun count; concurrent because a client thread may ask isWeapon()
    // for a tooltip while the server thread fires.
    private static final Map<AbstractGunItem, AgmWeapon> AGM_WEAPONS = new ConcurrentHashMap<>();

    /**
     * effects: returns the weapon behind {@code stack}, or null if the stack
     * is empty or no tier claims it. A few lookups, no allocation on the
     * steady state: cheap enough to call every tick.
     *
     * @param stack the stack in question
     * @return its weapon, or null
     */
    public static RangedWeapon resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof AbstractGunItem gun
                && gun.weaponType() != WeaponType.FLAMETHROWER
                && RangedWeapons.profileOf(stack).isPresent()) {
            return AGM_WEAPONS.computeIfAbsent(gun, AgmWeapon::new);
        }
        return RangedWeapons.resolve(stack);
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
