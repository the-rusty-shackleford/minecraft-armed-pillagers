/*
 * Armed Pillagers - pillagers that carry Another Gun Mod firearms.
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Armed Pillagers - NeoForge 1.21.1, addon to F708's Another Gun Mod.
 *
 * A slice of newly spawned pillagers carry a revolver or a rifle instead of a
 * crossbow, and a much thinner slice carry a shotgun. Auto-guns, machine guns
 * and flamethrowers are never handed out - see {@link PillagerGun}.
 *
 * Vanilla pillager AI can only operate a crossbow ({@code RangedCrossbowAttackGoal}
 * tests {@code isHolding(CrossbowItem)}), so a gun in the main hand would leave
 * the mob with no ranged attack at all. {@link GunAttackGoal} replaces that:
 * it drives the gun through Another Gun Mod's own item API - its damage, fire
 * rate, reload time, ammo container and BulletEntity - so an armed pillager
 * shoots with exactly the weapon the player would pick up off its corpse.
 *
 * Everything the gun mod is asked for goes through its public classes; nothing
 * here is mixed into, so a gun mod update can only ever break this at compile
 * time, never silently at runtime.
 */
@Mod(ArmedPillagers.MOD_ID)
public class ArmedPillagers {
    public static final String MOD_ID = "armedpillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArmedPillagers(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ApConfig.SPEC);
    }
}
