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
package com.nfx.armedpillagers.bridge.agm;

import com.f708.anothergunmod.core.enums.WeaponType;
import com.f708.anothergunmod.registry.item.ModItems;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.mojang.logging.LogUtils;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

/**
 * Binds Another Gun Mod's guns to the Ranged Weapons protocol natively.
 *
 * <p>Every gun item the gun mod registers -- found by walking the item
 * registry, so a gun added in a later version of the mod is bridged without
 * a change here -- gets the protocol's {@code WEAPON} capability, answered
 * by an {@link AgmWeapon}. The flamethrower is left out: its jet is not a
 * bullet, and the gun mod's bullet builder is the wrong tool for it. The
 * small magazine gets {@code AMMO_STORE}.
 *
 * <p>The provider declines a gun no pack has described in the
 * {@code rangedweapons:weapons} data map. Spread, engagement range and
 * sounds come from that profile, and inventing them would arm a mob with
 * numbers nobody chose; declined, the protocol's own fallback tier may still
 * answer if a profile appears later, and a consumer's loadout check reports
 * the gap at reload.
 */
@Mod(AgmBridge.MOD_ID)
public final class AgmBridge {
    public static final String MOD_ID = "armedpillagers_agm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AgmBridge(IEventBus modBus) {
        modBus.addListener(AgmBridge::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        int bridged = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof AbstractGunItem gun) || gun.weaponType() == WeaponType.FLAMETHROWER) {
                continue;
            }
            AgmWeapon weapon = new AgmWeapon(gun);
            event.registerItem(RangedWeapons.WEAPON,
                    (stack, context) -> RangedWeapons.profileOf(stack).isPresent() ? weapon : null,
                    gun);
            bridged++;
        }
        event.registerItem(RangedWeapons.AMMO_STORE,
                (stack, context) -> AgmMagazineStore.INSTANCE,
                ModItems.SMALL_MAGAZINE.get());
        LOGGER.info("bridged {} Another Gun Mod guns to the Ranged Weapons protocol", bridged);
    }
}
