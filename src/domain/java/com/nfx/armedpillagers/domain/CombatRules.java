/*
 * Armed Pillagers - pillagers that carry firearms and know how to use them.
 * Copyright (C) 2026 nfx and Rusty Shackleford
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
 * The arithmetic of a gunfight: how long a reload takes, how long the trigger
 * waits between shots, how hard a shot lands, and who hears it from afar.
 *
 * <p>Every method is a pure function of plain numbers. The expressions are
 * exactly the ones that used to sit inline in the attack goal, including their
 * types -- an {@code int} product scaled by a {@code double} and truncated, a
 * {@code float} product rounded -- because a mob that reloads one tick faster
 * than it did yesterday is a behaviour change, and the tests here pin the
 * numbers to keep it from being an accidental one.
 *
 * <p>Preconditions are checked and reported as {@link IllegalArgumentException}
 * rather than left to produce a nonsensical number: a negative reload or a NaN
 * multiplier is a broken config or a broken weapon, and the sooner it is named
 * the better.
 */
public final class CombatRules {
    /**
     * A reload never takes fewer ticks than this, however fast the gun says it
     * is: the pause is the whole counterplay window.
     */
    public static final int MIN_RELOAD_TICKS = 10;
    /** A reload never takes more ticks than this, however slow the gun says it is. */
    public static final int MAX_RELOAD_TICKS = 400;
    /** Shots are never closer together than this, whatever the gun's fire rate. */
    public static final int MIN_FIRE_COOLDOWN = 2;

    private CombatRules() {}

    /**
     * Ticks a mob spends reloading an empty weapon: the gun's per-round time for
     * a whole magazine, scaled, clamped.
     *
     * <p>A mob pays for the full magazine at once rather than per round as a
     * player does, so standing exposed through it costs what it should.
     *
     * <p>requires: {@code reloadTime >= 0}, {@code capacity >= 0},
     * {@code reloadTime * capacity} fits in an {@code int}, {@code multiplier}
     * finite and {@code >= 0}<br>
     * effects: returns {@code (int) ((reloadTime * capacity) * multiplier)},
     * with the product taken in {@code int} first and the scale applied in
     * {@code double} and truncated toward zero, clamped to
     * [{@link #MIN_RELOAD_TICKS}, {@link #MAX_RELOAD_TICKS}]<br>
     * throws: {@link IllegalArgumentException} if any precondition fails
     *
     * @param reloadTime the gun's own ticks to load one round
     * @param capacity   rounds in a full magazine
     * @param multiplier the config scale on top
     * @return ticks the reload takes
     */
    public static int reloadTicks(int reloadTime, int capacity, double multiplier) {
        if (reloadTime < 0) {
            throw new IllegalArgumentException("reloadTime must be >= 0, was " + reloadTime);
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0, was " + capacity);
        }
        requireFiniteNonNegative("multiplier", multiplier);

        final int product;
        try {
            product = Math.multiplyExact(reloadTime, capacity);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "reloadTime * capacity overflows an int: " + reloadTime + " * " + capacity, overflow);
        }

        int scaled = (int) (product * multiplier);
        return Math.max(MIN_RELOAD_TICKS, Math.min(MAX_RELOAD_TICKS, scaled));
    }

    /**
     * Ticks the trigger waits after a shot before it can fire again.
     *
     * <p>effects: returns {@code max(}{@link #MIN_FIRE_COOLDOWN}{@code , fireRate)}
     *
     * @param fireRate the gun's own minimum ticks between shots; any value is
     *                 accepted and a low or negative one clamps to the floor
     * @return ticks until the next shot may fire
     */
    public static int fireCooldown(int fireRate) {
        return Math.max(MIN_FIRE_COOLDOWN, fireRate);
    }

    /**
     * Damage one projectile deals when a mob pulls the trigger.
     *
     * <p>requires: {@code multiplier} finite and {@code >= 0}<br>
     * effects: returns {@code max(1, round(baseDamage * multiplier))}, the
     * product taken in {@code float} and rounded half-up<br>
     * throws: {@link IllegalArgumentException} if {@code multiplier} is NaN,
     * infinite or negative
     *
     * @param baseDamage the gun's own per-projectile damage
     * @param multiplier the config scale on top
     * @return damage per projectile, never less than one
     */
    public static int damage(int baseDamage, float multiplier) {
        requireFiniteNonNegative("multiplier", multiplier);
        return Math.max(1, Math.round(baseDamage * multiplier));
    }

    /**
     * Damage one projectile deals, from a weapon profile's float damage.
     *
     * <p>For a whole-number {@code baseDamage} this is identical to
     * {@link #damage(int, float)}: an int converts to float exactly, so the
     * product and the rounding are the same. Fractional damage from a
     * datapack profile takes the same path.
     *
     * <p>requires: {@code baseDamage >= 0} and finite; {@code multiplier}
     * finite and {@code >= 0}<br>
     * effects: returns {@code max(1, round(baseDamage * multiplier))}, the
     * product taken in {@code float} and rounded half-up<br>
     * throws: {@link IllegalArgumentException} if either argument is out of range
     *
     * @param baseDamage the weapon's own per-projectile damage
     * @param multiplier the config scale on top
     * @return damage per projectile, never less than one
     */
    public static int damage(float baseDamage, float multiplier) {
        requireFiniteNonNegative("baseDamage", baseDamage);
        requireFiniteNonNegative("multiplier", multiplier);
        return Math.max(1, Math.round(baseDamage * multiplier));
    }


    private static void requireFiniteNonNegative(String name, double value) {
        // `!(value >= 0)` rather than `value < 0` so that NaN fails too.
        if (!(value >= 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, was " + value);
        }
    }

}
