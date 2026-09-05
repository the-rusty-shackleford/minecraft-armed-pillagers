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

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;

/**
 * Armed Pillagers - NeoForge 1.21.1. Pillagers that carry firearms and know
 * how to use them.
 *
 * <p>A slice of newly spawned pillagers carry a gun instead of a crossbow.
 * Which guns, and how often relative to each other, is the
 * {@code armedpillagers:pillager_loadouts} data map ({@link PillagerLoadouts});
 * how often at all is config. A loadout is issued only if the item is a
 * weapon by the Ranged Weapons protocol -- a gun mod provides for it, or a
 * datapack profile describes it -- and its class is not one config denies
 * ({@link LoadoutTable}). This mod names no gun mod.
 *
 * <p>Vanilla pillager AI can only operate a crossbow
 * ({@code RangedCrossbowAttackGoal} tests {@code isHolding(CrossbowItem)}), so
 * a gun in the main hand would leave the mob with no ranged attack at all.
 * {@link GunAttackGoal} replaces that, driving whatever {@code RangedWeapon}
 * resolves from the mob's hand through the protocol's contract.
 */
@Mod(ArmedPillagers.MOD_ID)
public class ArmedPillagers {
    public static final String MOD_ID = "armedpillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArmedPillagers(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ApConfig.SPEC);
        modBus.addListener(ArmedPillagers::registerDataMaps);
        // A game-bus event, not a mod-bus one.
        NeoForge.EVENT_BUS.addListener(ArmedPillagers::onDataMapsUpdated);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(PillagerLoadouts.PILLAGER_LOADOUTS);
    }

    /**
     * Fires once per registry after every data map on it has been applied,
     * so the loadouts and the weapon profiles they are checked against are
     * both current. The client-sync cause is ignored: the table serves the
     * server-side AI, and neither data map is synced anyway.
     */
    private static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getCause() != DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            return;
        }
        event.ifRegistry(Registries.ITEM, LoadoutTable::rebuild);
    }

    /**
     * effects: returns the registry path of {@code item} for log lines --
     * "revolver", not "anothergunmod:revolver"
     *
     * @param item the item
     * @return its short name
     */
    public static String shortName(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? item.toString() : key.getPath();
    }
}
