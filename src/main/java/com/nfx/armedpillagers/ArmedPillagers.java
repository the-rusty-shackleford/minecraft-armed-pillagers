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
