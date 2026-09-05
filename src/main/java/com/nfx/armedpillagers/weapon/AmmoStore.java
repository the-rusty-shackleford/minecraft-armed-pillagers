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

import net.minecraft.world.item.ItemStack;

/**
 * Something that holds a whole number of rounds: a gun's internal magazine,
 * a detachable magazine, a quiver.
 *
 * <p>All state lives in the stack's own data components; an implementation is
 * stateless and may be shared across every stack of its item. Separate from
 * {@link RangedWeapon} because a loot magazine is one of these and is not a
 * weapon, and because a weapon's ammunition is only one of the things a
 * consumer may want to fill.
 */
public interface AmmoStore {

    /**
     * effects: returns the most rounds {@code stack} can hold; at least one
     *
     * @param stack a stack of this store's item
     * @return the capacity
     */
    int capacity(ItemStack stack);

    /**
     * effects: returns the rounds loaded right now, in
     * {@code [0, capacity(stack)]}
     *
     * @param stack a stack of this store's item
     * @return the current round count
     */
    int rounds(ItemStack stack);

    /**
     * Whether the store must be reloaded before it can supply a round.
     *
     * <p>effects: returns whether no round is available; by default
     * {@code rounds(stack) == 0}. An implementation whose backing mod has a
     * notion of unlimited ammunition overrides this rather than lying about
     * the count.
     *
     * @param stack a stack of this store's item
     * @return whether it is empty
     */
    default boolean isEmpty(ItemStack stack) {
        return rounds(stack) == 0;
    }

    /**
     * Sets the loaded round count.
     *
     * <p>requires: {@code 0 <= count <= capacity(stack)}<br>
     * effects: {@code rounds(stack) == count} afterwards. The stack's
     * components are written <em>in place</em>, so the caller must pass the
     * stack that is actually held or dropped, never a copy -- components are
     * copy-on-write, and a forgotten write-back is a silent no-op.<br>
     * throws: {@link IllegalArgumentException} if {@code count} is out of range
     *
     * @param stack a stack of this store's item
     * @param count the rounds it should hold afterwards
     */
    void load(ItemStack stack, int count);
}
