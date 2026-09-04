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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CombatRules}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * reloadTicks(reloadTime, capacity, multiplier):
 *   product*multiplier:  below the floor / exactly the floor / in band
 *                        / exactly the ceiling / above the ceiling
 *   multiplier:          0 / the config minimum 0.25 / 1 / the config maximum 8
 *   truncation:          a fractional result truncates toward zero, not rounds
 *   capacity:            0 (a weapon that holds nothing still pays the floor)
 *   invalid:             negative reloadTime / negative capacity
 *                        / negative, NaN, infinite multiplier / int overflow
 *
 * fireCooldown(fireRate):
 *   fireRate:            below the floor (negative, 0, 1) / exactly the floor / above
 *
 * damage(baseDamage, multiplier):
 *   product:             below 1 (base 0; multiplier 0) / exactly 1 / above
 *   rounding:            a half rounds up / a whole number is exact
 *   multiplier:          the config maximum 4
 *   invalid:             negative / NaN / infinite multiplier
 *
 * hearsFarReport(distSqr, near, far):
 *   distSqr vs near²:    below / exactly at (not far) / just above
 *   distSqr vs far²:     exactly at (still far) / above (silent)
 *   invalid:             any negative argument / NaN
 * </pre>
 */
final class CombatRulesTest {

    // --- reloadTicks --------------------------------------------------------

    @Test
    void reloadBelowTheFloorClampsUpToIt() {
        // 2 * 3 = 6 ticks; the floor is 10.
        assertEquals(CombatRules.MIN_RELOAD_TICKS, CombatRules.reloadTicks(2, 3, 1.0));
    }

    @Test
    void reloadExactlyAtTheFloorIsTheFloor() {
        assertEquals(10, CombatRules.reloadTicks(5, 2, 1.0));
    }

    @Test
    void reloadInBandIsTheScaledProduct() {
        // 10 per round * 6 rounds = 60.
        assertEquals(60, CombatRules.reloadTicks(10, 6, 1.0));
    }

    @Test
    void reloadExactlyAtTheCeilingIsTheCeiling() {
        assertEquals(400, CombatRules.reloadTicks(40, 10, 1.0));
    }

    @Test
    void reloadAboveTheCeilingClampsDownToIt() {
        assertEquals(CombatRules.MAX_RELOAD_TICKS, CombatRules.reloadTicks(100, 100, 1.0));
    }

    @Test
    void reloadMultiplierZeroYieldsTheFloor() {
        assertEquals(CombatRules.MIN_RELOAD_TICKS, CombatRules.reloadTicks(40, 10, 0.0));
    }

    @Test
    void reloadAtTheConfigMinimumMultiplier() {
        // 40 * 10 = 400, * 0.25 = 100.
        assertEquals(100, CombatRules.reloadTicks(40, 10, 0.25));
    }

    @Test
    void reloadAtTheConfigMaximumMultiplierStillClamps() {
        assertEquals(CombatRules.MAX_RELOAD_TICKS, CombatRules.reloadTicks(10, 6, 8.0));
    }

    @Test
    void reloadTruncatesTowardZeroRatherThanRounding() {
        // 3 * 3 = 9, * 1.5 = 13.5 -> 13, not 14.
        assertEquals(13, CombatRules.reloadTicks(3, 3, 1.5));
    }

    @Test
    void reloadWithZeroCapacityStillPaysTheFloor() {
        assertEquals(CombatRules.MIN_RELOAD_TICKS, CombatRules.reloadTicks(40, 0, 1.0));
    }

    @Test
    void reloadRejectsNegativeReloadTime() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.reloadTicks(-1, 6, 1.0));
    }

    @Test
    void reloadRejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.reloadTicks(10, -1, 1.0));
    }

    @Test
    void reloadRejectsNegativeMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.reloadTicks(10, 6, -0.5));
    }

    @Test
    void reloadRejectsNaNMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.reloadTicks(10, 6, Double.NaN));
    }

    @Test
    void reloadRejectsInfiniteMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatRules.reloadTicks(10, 6, Double.POSITIVE_INFINITY));
    }

    @Test
    void reloadRejectsAProductThatOverflowsAnInt() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatRules.reloadTicks(Integer.MAX_VALUE, 2, 1.0));
    }

    // --- fireCooldown -------------------------------------------------------

    @Test
    void cooldownBelowTheFloorClampsUpToIt() {
        assertEquals(CombatRules.MIN_FIRE_COOLDOWN, CombatRules.fireCooldown(-5));
        assertEquals(CombatRules.MIN_FIRE_COOLDOWN, CombatRules.fireCooldown(0));
        assertEquals(CombatRules.MIN_FIRE_COOLDOWN, CombatRules.fireCooldown(1));
    }

    @Test
    void cooldownExactlyAtTheFloorIsTheFloor() {
        assertEquals(2, CombatRules.fireCooldown(2));
    }

    @Test
    void cooldownAboveTheFloorIsUnchanged() {
        assertEquals(15, CombatRules.fireCooldown(15));
    }

    // --- damage -------------------------------------------------------------

    @Test
    void damageNeverDropsBelowOne() {
        assertEquals(1, CombatRules.damage(0, 1.0f));
        assertEquals(1, CombatRules.damage(6, 0.0f));
    }

    @Test
    void damageExactlyOneStaysOne() {
        assertEquals(1, CombatRules.damage(1, 1.0f));
    }

    @Test
    void damageWholeProductIsExact() {
        assertEquals(12, CombatRules.damage(12, 1.0f));
        assertEquals(6, CombatRules.damage(12, 0.5f));
    }

    @Test
    void damageHalfRoundsUp() {
        // 5 * 0.5 = 2.5 -> 3.
        assertEquals(3, CombatRules.damage(5, 0.5f));
    }

    @Test
    void damageAtTheConfigMaximumMultiplier() {
        assertEquals(48, CombatRules.damage(12, 4.0f));
    }

    @Test
    void damageRejectsNegativeMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damage(6, -1.0f));
    }

    @Test
    void damageRejectsNaNMultiplier() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damage(6, Float.NaN));
    }

    @Test
    void damageRejectsInfiniteMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatRules.damage(6, Float.POSITIVE_INFINITY));
    }

    // --- hearsFarReport -----------------------------------------------------

    @Test
    void insideTheNearRadiusHearsTheCloseReportNotTheFarOne() {
        assertFalse(CombatRules.hearsFarReport(100.0, 16.0, 64.0));
    }

    @Test
    void exactlyOnTheNearRadiusIsStillTheCloseReport() {
        assertFalse(CombatRules.hearsFarReport(16.0 * 16.0, 16.0, 64.0));
    }

    @Test
    void justPastTheNearRadiusHearsTheFarReport() {
        assertTrue(CombatRules.hearsFarReport(16.0 * 16.0 + 1.0, 16.0, 64.0));
    }

    @Test
    void exactlyOnTheFarRadiusStillHearsTheFarReport() {
        assertTrue(CombatRules.hearsFarReport(64.0 * 64.0, 16.0, 64.0));
    }

    @Test
    void beyondTheFarRadiusHearsNothing() {
        assertFalse(CombatRules.hearsFarReport(64.0 * 64.0 + 1.0, 16.0, 64.0));
    }

    @Test
    void farReportRejectsNegativeArguments() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.hearsFarReport(-1.0, 16.0, 64.0));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.hearsFarReport(100.0, -16.0, 64.0));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.hearsFarReport(100.0, 16.0, -64.0));
    }

    @Test
    void farReportRejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.hearsFarReport(Double.NaN, 16.0, 64.0));
    }
}
