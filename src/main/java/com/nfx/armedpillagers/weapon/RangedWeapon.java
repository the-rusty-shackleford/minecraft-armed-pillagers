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

import com.nfx.armedpillagers.domain.WeaponStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A ranged weapon in the hands of any living entity.
 *
 * <p>This is the contract a mob-AI consumer codes against and a gun mod (or
 * a bridge on its behalf) implements. Nothing here takes a {@code Player}:
 * every gun mod's own API is player-bound, which is exactly why a mob cannot
 * use one directly. And nothing here reads the shooter's look angle -- aim is
 * always the explicit vector in the {@link Shot}, because on a mob the look
 * angle is body yaw and lags the head while strafing.
 *
 * <p>An implementation is stateless and shared: all per-stack state lives in
 * the stack's data components, read and written through {@link AmmoStore}.
 */
public interface RangedWeapon extends AmmoStore {

    /**
     * effects: returns the per-item description this weapon honours; never
     * null
     *
     * @return the profile
     */
    WeaponProfile profile();

    /**
     * The numbers governing this specific stack right now.
     *
     * <p>Per stack because gun mods keep stat overrides on the stack's
     * components. Invariant: {@code stats(stack).capacity() == capacity(stack)}.
     *
     * <p>effects: returns the stats for {@code stack}; by default the
     * profile's defaults
     *
     * @param stack a stack of this weapon's item
     * @return its stats
     */
    default WeaponStats stats(ItemStack stack) {
        return profile().defaults();
    }

    /**
     * Spends one round: one trigger pull, however many projectiles it launched.
     *
     * <p>requires: {@code rounds(stack) >= 1}<br>
     * effects: {@code rounds(stack)} decreases by exactly one<br>
     * throws: {@link IllegalStateException} if the weapon is empty
     *
     * @param stack a stack of this weapon's item
     */
    void consumeRound(ItemStack stack);

    /**
     * Launches a shot's projectiles.
     *
     * <p>requires: {@code shooter.level() == level}; {@code shooter.isAlive()}<br>
     * effects: spawns {@code shot.count()} projectiles in {@code level}, owned
     * by {@code shooter}, from {@code shot.origin()}, each along
     * {@code shot.direction()} perturbed per the spread definition in
     * {@link WeaponStats}, moving {@code shot.speed()} blocks per tick,
     * dealing {@code shot.damage()} on a hit, gone after
     * {@code shot.lifetimeTicks()}. <strong>Consumes no ammo, plays no sound,
     * damages no durability</strong> -- those are the caller's, in that order,
     * after this returns.
     *
     * @param level   the server level to spawn into
     * @param shooter who fired; the projectiles' owner
     * @param stack   the weapon stack being fired
     * @param shot    what to launch
     */
    void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot);
}
