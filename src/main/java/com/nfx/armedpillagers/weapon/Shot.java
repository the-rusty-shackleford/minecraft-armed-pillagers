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
import net.minecraft.world.phys.Vec3;

/**
 * One trigger pull, fully specified: where the projectiles start, which way
 * they go, how many, how fast, how scattered, how hard, and for how long.
 *
 * <p>Built by the consumer <em>after</em> it has applied its own multipliers,
 * so a weapon implementation never sees or needs consumer config. Immutable.
 *
 * <p>RI: {@code direction} has length within {@code 1e-3} of one;
 * {@code count >= 1}; {@code speed > 0}; {@code spread >= 0};
 * {@code damage >= 0}; {@code lifetimeTicks >= 1}; all floats finite.
 *
 * @param origin        where every projectile spawns
 * @param direction     the aim, unit length; each projectile deviates from it by the spread
 * @param count         projectiles to launch
 * @param speed         blocks per tick
 * @param spread        per-axis uniform deviation, see {@link WeaponStats}
 * @param damage        damage each projectile deals
 * @param lifetimeTicks ticks before an unspent projectile is removed
 */
public record Shot(Vec3 origin, Vec3 direction, int count, float speed, float spread, float damage,
                   int lifetimeTicks) {

    private static final double UNIT_TOLERANCE = 1e-3;

    /**
     * @throws IllegalArgumentException if any component of the RI fails
     */
    public Shot {
        if (origin == null || direction == null) {
            throw new IllegalArgumentException("origin and direction must not be null");
        }
        if (Math.abs(direction.length() - 1.0) > UNIT_TOLERANCE) {
            throw new IllegalArgumentException("direction must be unit length, had length " + direction.length());
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, was " + count);
        }
        if (!(speed > 0) || Float.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be finite and > 0, was " + speed);
        }
        if (!(spread >= 0) || Float.isInfinite(spread)) {
            throw new IllegalArgumentException("spread must be finite and >= 0, was " + spread);
        }
        if (!(damage >= 0) || Float.isInfinite(damage)) {
            throw new IllegalArgumentException("damage must be finite and >= 0, was " + damage);
        }
        if (lifetimeTicks < 1) {
            throw new IllegalArgumentException("lifetimeTicks must be >= 1, was " + lifetimeTicks);
        }
    }

    /**
     * A shot with a weapon's numbers along a given line.
     *
     * <p>requires: {@code direction} is not the zero vector<br>
     * effects: returns the shot from {@code origin} along {@code direction}
     * normalised, carrying {@code stats}' projectile count, speed, spread,
     * damage and lifetime<br>
     * throws: {@link IllegalArgumentException} if {@code direction} has no
     * length to normalise
     *
     * @param stats     the weapon's numbers, already scaled by the consumer
     * @param origin    where the projectiles spawn
     * @param direction the aim; need not be unit length
     * @return the shot
     */
    public static Shot of(WeaponStats stats, Vec3 origin, Vec3 direction) {
        if (direction.lengthSqr() < 1e-8) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        return new Shot(origin, direction.normalize(), stats.projectilesPerShot(), stats.projectileSpeed(),
                stats.spread(), stats.damage(), stats.projectileLifetimeTicks());
    }
}
