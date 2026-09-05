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

import com.nfx.armedpillagers.domain.WeaponClass;
import com.nfx.armedpillagers.domain.WeaponStats;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * What is true of every stack of one weapon item: its class, its default
 * stats, what it eats, and what it sounds like. Immutable.
 *
 * <p>Items and sounds are referenced by id and resolved at use rather than
 * held as registry objects, so a profile can be built -- and, later, decoded
 * from a datapack -- without a bootstrapped registry. An id that resolves to
 * nothing degrades: no ammo drops, no sound plays.
 *
 * <p>RI: no field is null. Enforced in the constructor; the {@code Optional}s
 * express absence, a null {@code Optional} is a bug.
 *
 * @param weaponClass  the coarse category, for consumer policy
 * @param defaults     the stats for a stack with no overrides of its own
 * @param ammoItem     the loose-round item this weapon eats, if any; what a killed carrier drops
 * @param magazineItem a detachable container for its rounds, if any; also loot
 * @param shotSound    the report heard near the shooter, if any
 * @param farShotSound the muffled report heard at a distance, if any
 */
public record WeaponProfile(WeaponClass weaponClass, WeaponStats defaults,
                            Optional<ResourceLocation> ammoItem, Optional<ResourceLocation> magazineItem,
                            Optional<ResourceLocation> shotSound, Optional<ResourceLocation> farShotSound) {

    /**
     * @throws IllegalArgumentException if any field is null
     */
    public WeaponProfile {
        if (weaponClass == null || defaults == null || ammoItem == null || magazineItem == null
                || shotSound == null || farShotSound == null) {
            throw new IllegalArgumentException("no field of a WeaponProfile may be null");
        }
    }
}
