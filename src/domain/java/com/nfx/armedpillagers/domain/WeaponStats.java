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
package com.nfx.armedpillagers.domain;

/**
 * Everything a ranged AI needs to fight with one specific weapon stack right
 * now. Immutable.
 *
 * <p>Units: ticks, blocks, blocks per tick, and damage in the game's own
 * half-heart units per projectile. Every value is per <em>stack</em> and per
 * <em>moment</em>: a gun mod may keep stat overrides on the stack's components,
 * so a provider answers for the stack it is handed, not for the item type.
 *
 * <p>Spread is defined here, not by any gun mod: each projectile's direction
 * is the aim direction plus an independent uniform offset in {@code [-spread,
 * +spread]} on each axis, then renormalised. Hand-tuned spreads are tuned
 * against that definition.
 *
 * <p>RI: {@code capacity >= 1}; {@code reloadTicksPerRound >= 0};
 * {@code fireRateTicks >= 1}; {@code damage >= 0} and finite;
 * {@code projectilesPerShot >= 1}; {@code spread >= 0} and finite;
 * {@code engagementRange > 0} and finite; {@code projectileSpeed > 0} and
 * finite; {@code projectileLifetimeTicks >= 1}. Enforced in the constructor
 * so a profile that fails it is refused where it is built, with the field
 * named, rather than discovered as a mob that never fires.
 *
 * @param capacity                rounds in a full magazine
 * @param reloadTicksPerRound     ticks to load one round; a full reload is this times the capacity
 * @param fireRateTicks           minimum ticks between trigger pulls
 * @param damage                  damage per projectile
 * @param projectilesPerShot      projectiles launched per trigger pull
 * @param spread                  angular scatter per projectile, per the definition above
 * @param engagementRange         blocks at which a mob stops closing and starts shooting
 * @param projectileSpeed         blocks per tick
 * @param projectileLifetimeTicks ticks before a projectile that hits nothing is gone
 */
public record WeaponStats(int capacity, int reloadTicksPerRound, int fireRateTicks, float damage,
                          int projectilesPerShot, float spread, float engagementRange,
                          float projectileSpeed, int projectileLifetimeTicks) {

    /**
     * @throws IllegalArgumentException naming the first field that violates the RI
     */
    public WeaponStats {
        atLeast("capacity", capacity, 1);
        atLeast("reloadTicksPerRound", reloadTicksPerRound, 0);
        atLeast("fireRateTicks", fireRateTicks, 1);
        finiteAtLeast("damage", damage, 0);
        atLeast("projectilesPerShot", projectilesPerShot, 1);
        finiteAtLeast("spread", spread, 0);
        finiteAbove("engagementRange", engagementRange, 0);
        finiteAbove("projectileSpeed", projectileSpeed, 0);
        atLeast("projectileLifetimeTicks", projectileLifetimeTicks, 1);
    }

    /**
     * Ticks to reload from empty: a mob pays for the whole magazine at once.
     *
     * <p>effects: returns {@code reloadTicksPerRound * capacity}<br>
     * throws: {@link ArithmeticException} if the product overflows an int
     *
     * @return the full-magazine reload time
     */
    public int fullReloadTicks() {
        return Math.multiplyExact(reloadTicksPerRound, capacity);
    }

    /**
     * A copy with damage and spread scaled, for a consumer applying its own
     * multipliers before building a shot.
     *
     * <p>requires: {@code damageMultiplier >= 0} and finite;
     * {@code spreadMultiplier > 0} and finite<br>
     * effects: returns a copy with {@code damage * damageMultiplier} and
     * {@code spread * spreadMultiplier}, everything else unchanged<br>
     * throws: {@link IllegalArgumentException} if either multiplier is out of range
     *
     * @param damageMultiplier scale on damage
     * @param spreadMultiplier scale on spread
     * @return the scaled copy
     */
    public WeaponStats scaled(float damageMultiplier, float spreadMultiplier) {
        finiteAtLeast("damageMultiplier", damageMultiplier, 0);
        finiteAbove("spreadMultiplier", spreadMultiplier, 0);
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage * damageMultiplier,
                projectilesPerShot, spread * spreadMultiplier, engagementRange, projectileSpeed,
                projectileLifetimeTicks);
    }

    /** effects: returns a copy with the given capacity; throws IAE if it is below one. */
    public WeaponStats withCapacity(int capacity) {
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage, projectilesPerShot,
                spread, engagementRange, projectileSpeed, projectileLifetimeTicks);
    }

    /** effects: returns a copy with the given damage; throws IAE if it is negative or not finite. */
    public WeaponStats withDamage(float damage) {
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage, projectilesPerShot,
                spread, engagementRange, projectileSpeed, projectileLifetimeTicks);
    }

    /** effects: returns a copy with the given fire rate; throws IAE if it is below one. */
    public WeaponStats withFireRateTicks(int fireRateTicks) {
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage, projectilesPerShot,
                spread, engagementRange, projectileSpeed, projectileLifetimeTicks);
    }

    /** effects: returns a copy with the given per-round reload; throws IAE if it is negative. */
    public WeaponStats withReloadTicksPerRound(int reloadTicksPerRound) {
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage, projectilesPerShot,
                spread, engagementRange, projectileSpeed, projectileLifetimeTicks);
    }

    /** effects: returns a copy with the given projectile count; throws IAE if it is below one. */
    public WeaponStats withProjectilesPerShot(int projectilesPerShot) {
        return new WeaponStats(capacity, reloadTicksPerRound, fireRateTicks, damage, projectilesPerShot,
                spread, engagementRange, projectileSpeed, projectileLifetimeTicks);
    }

    private static void atLeast(String field, int value, int min) {
        if (value < min) {
            throw new IllegalArgumentException(field + " must be >= " + min + ", was " + value);
        }
    }

    private static void finiteAtLeast(String field, float value, float min) {
        // `!(value >= min)` rather than `value < min` so that NaN fails too.
        if (!(value >= min) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and >= " + min + ", was " + value);
        }
    }

    private static void finiteAbove(String field, float value, float exclusiveMin) {
        if (!(value > exclusiveMin) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and > " + exclusiveMin + ", was " + value);
        }
    }
}
