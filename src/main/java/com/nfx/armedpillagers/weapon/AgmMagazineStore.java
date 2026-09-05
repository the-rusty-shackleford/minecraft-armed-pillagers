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

import com.nfx.rangedweapons.api.AmmoStore;

import com.f708.anothergunmod.core.AmmoContainer;
import com.f708.anothergunmod.core.AmmoContainerRecord;
import com.f708.anothergunmod.registry.item.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * {@link AmmoStore} for Another Gun Mod's small magazine: a detachable
 * container of 32 small bullets, which is not a weapon and shares nothing
 * with one except the ammo-container data component.
 *
 * <p>Stateless; one instance serves every magazine.
 */
public final class AgmMagazineStore implements AmmoStore {

    /** The one instance. */
    public static final AgmMagazineStore INSTANCE = new AgmMagazineStore();

    /** Small magazines hold 32, and only ever small bullets. */
    static final int SMALL_CAPACITY = 32;

    private AgmMagazineStore() {}

    @Override
    public int capacity(ItemStack stack) {
        return SMALL_CAPACITY;
    }

    @Override
    public int rounds(ItemStack stack) {
        return AmmoContainerRecord.getContainerFromComponent(stack).size();
    }

    /** Same one-round-at-a-time build as {@link AgmWeapon#load}, for the same reason. */
    @Override
    public void load(ItemStack stack, int count) {
        if (count < 0 || count > SMALL_CAPACITY) {
            throw new IllegalArgumentException("count must be in [0, " + SMALL_CAPACITY + "], was " + count);
        }
        AmmoContainerRecord record = new AmmoContainerRecord(new AmmoContainer(SMALL_CAPACITY));
        ItemStack round = new ItemStack(ModItems.SMALLBULLET.get());
        for (int i = 0; i < count; i++) {
            record = record.addNewBullet(round);
        }
        AmmoContainerRecord.setContainerToComponent(stack, record);
    }
}
