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
package com.nfx.armedpillagers.domain;

import java.util.function.IntUnaryOperator;

/**
 * What a dead gunman leaves on the ground: how worn the gun is, how many loose
 * rounds fall with it, how full the magazine is.
 *
 * <p>Pure functions over plain numbers. The two that need randomness take it
 * as an {@link IntUnaryOperator} -- {@code bound -> a value in [0, bound)} --
 * rather than a random source, so a test can hand in a known roll and the
 * adapter can hand in {@code random::nextInt} and consume the entity's RNG
 * stream exactly as the inline code did.
 */
public final class DropRules {
    private DropRules() {}

    /**
     * The damage value a dropped gun should carry.
     *
     * <p>Vanilla batters dropped equipment down to a sliver of durability
     * ({@code Mob.dropCustomDeathLoot}), which would make a rare gun drop
     * worthless. The cap is a fraction of the gun's maximum damage; a cap of
     * one or more leaves vanilla's number alone.
     *
     * <p>requires: {@code damage >= 0}, {@code maxDamage >= 0}, {@code wearCap}
     * finite and {@code >= 0}<br>
     * effects: returns {@code damage} if {@code wearCap >= 1}, else
     * {@code min(damage, (int) (maxDamage * wearCap))}, the product truncated<br>
     * throws: {@link IllegalArgumentException} if any precondition fails
     *
     * @param damage    the damage value vanilla put on the drop
     * @param maxDamage the gun's maximum damage (its durability)
     * @param wearCap   the most of that durability the drop may have lost
     * @return the damage value to set
     */
    public static int clampedDamage(int damage, int maxDamage, double wearCap) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be >= 0, was " + damage);
        }
        if (maxDamage < 0) {
            throw new IllegalArgumentException("maxDamage must be >= 0, was " + maxDamage);
        }
        if (!(wearCap >= 0) || Double.isInfinite(wearCap)) {
            throw new IllegalArgumentException("wearCap must be finite and >= 0, was " + wearCap);
        }
        if (wearCap >= 1.0D) {
            return damage;
        }
        int maxWear = (int) (maxDamage * wearCap);
        return Math.min(damage, maxWear);
    }

    /**
     * Rounds in a dropped magazine, uniform between two configured bounds and
     * never more than the magazine holds.
     *
     * <p>The bounds may arrive in either order -- a config with minimum above
     * maximum is a mistake, not a crash -- so they are sorted first.
     *
     * <p>requires: {@code a >= 0}, {@code b >= 0}, {@code capacity >= 0};
     * {@code uniform(n)} returns a value in {@code [0, n)} for {@code n >= 1}<br>
     * effects: with {@code lo = min(a, b)} and {@code hi = max(a, b)}, calls
     * {@code uniform} exactly once with bound {@code hi - lo + 1} and returns
     * {@code min(lo + that, capacity)}<br>
     * throws: {@link IllegalArgumentException} if a bound or the capacity is
     * negative
     *
     * @param a        one configured bound on the round count
     * @param b        the other
     * @param capacity the most rounds the magazine can hold
     * @param uniform  the roll
     * @return rounds to load
     */
    public static int magazineRounds(int a, int b, int capacity, IntUnaryOperator uniform) {
        if (a < 0 || b < 0) {
            throw new IllegalArgumentException("round bounds must be >= 0, were " + a + " and " + b);
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0, was " + capacity);
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        int rounds = lo + uniform.applyAsInt(hi - lo + 1);
        return Math.min(rounds, capacity);
    }

    /**
     * Loose rounds dropped beside the gun: at least one, at most the configured
     * maximum, never more than a stack holds.
     *
     * <p>requires: {@code dropMax >= 1}, {@code maxStackSize >= 1};
     * {@code uniform(n)} returns a value in {@code [0, n)} for {@code n >= 1}<br>
     * effects: calls {@code uniform} exactly once with bound {@code dropMax}
     * and returns {@code min(1 + that, maxStackSize)}<br>
     * throws: {@link IllegalArgumentException} if {@code dropMax} or
     * {@code maxStackSize} is less than one
     *
     * @param dropMax      the configured upper bound on the count
     * @param maxStackSize the ammo item's stack limit
     * @param uniform      the roll
     * @return rounds to drop
     */
    public static int ammoCount(int dropMax, int maxStackSize, IntUnaryOperator uniform) {
        if (dropMax < 1) {
            throw new IllegalArgumentException("dropMax must be >= 1, was " + dropMax);
        }
        if (maxStackSize < 1) {
            throw new IllegalArgumentException("maxStackSize must be >= 1, was " + maxStackSize);
        }
        return Math.min(1 + uniform.applyAsInt(dropMax), maxStackSize);
    }
}
