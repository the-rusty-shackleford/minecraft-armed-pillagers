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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WeaponStats}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * constructor (RI), one field at a time with the rest valid:
 *   capacity:                 1 (accepted) / 0 -> IAE naming it
 *   reloadTicksPerRound:      0 (accepted) / -1 -> IAE
 *   fireRateTicks:            1 / 0 -> IAE
 *   damage:                   0 / -0.5 -> IAE / NaN -> IAE / infinite -> IAE
 *   projectilesPerShot:       1 / 0 -> IAE
 *   spread:                   0 / negative -> IAE / NaN -> IAE
 *   engagementRange:          tiny positive / 0 -> IAE / infinite -> IAE
 *   projectileSpeed:          tiny positive / 0 -> IAE
 *   projectileLifetimeTicks:  1 / 0 -> IAE
 * fullReloadTicks:
 *   capacity 1 / n; reload 0 / n; a product that overflows -> ArithmeticException
 * scaled:
 *   multipliers 0, 1, > 1 on damage; 1, > 1 on spread; other fields untouched
 *   invalid: negative or NaN damage multiplier / zero or negative spread multiplier -> IAE
 * withX:
 *   each returns a copy differing in exactly that field; each re-checks the RI
 * </pre>
 */
final class WeaponStatsTest {

    /** The revolver, roughly: 6 rounds, 6 damage, quick. */
    private static WeaponStats revolver() {
        return new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, 16.0f, 4.0f, 100);
    }

    private static void assertRejects(String field, Runnable construction) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, construction::run);
        assertTrue(e.getMessage().startsWith(field), "message should name " + field + ": " + e.getMessage());
    }

    // --- constructor ----------------------------------------------------------

    @Test
    void boundaryValuesAreAccepted() {
        WeaponStats edge = new WeaponStats(1, 0, 1, 0.0f, 1, 0.0f, Float.MIN_VALUE, Float.MIN_VALUE, 1);
        assertEquals(1, edge.capacity());
        assertEquals(0.0f, edge.damage());
    }

    @Test
    void capacityBelowOneIsRejected() {
        assertRejects("capacity", () -> new WeaponStats(0, 10, 15, 6.0f, 1, 0.045f, 16.0f, 4.0f, 100));
    }

    @Test
    void negativeReloadIsRejected() {
        assertRejects("reloadTicksPerRound", () -> new WeaponStats(6, -1, 15, 6.0f, 1, 0.045f, 16.0f, 4.0f, 100));
    }

    @Test
    void fireRateBelowOneIsRejected() {
        assertRejects("fireRateTicks", () -> new WeaponStats(6, 10, 0, 6.0f, 1, 0.045f, 16.0f, 4.0f, 100));
    }

    @Test
    void badDamageIsRejected() {
        assertRejects("damage", () -> new WeaponStats(6, 10, 15, -0.5f, 1, 0.045f, 16.0f, 4.0f, 100));
        assertRejects("damage", () -> new WeaponStats(6, 10, 15, Float.NaN, 1, 0.045f, 16.0f, 4.0f, 100));
        assertRejects("damage", () -> new WeaponStats(6, 10, 15, Float.POSITIVE_INFINITY, 1, 0.045f, 16.0f, 4.0f, 100));
    }

    @Test
    void projectilesBelowOneAreRejected() {
        assertRejects("projectilesPerShot", () -> new WeaponStats(6, 10, 15, 6.0f, 0, 0.045f, 16.0f, 4.0f, 100));
    }

    @Test
    void badSpreadIsRejected() {
        assertRejects("spread", () -> new WeaponStats(6, 10, 15, 6.0f, 1, -0.01f, 16.0f, 4.0f, 100));
        assertRejects("spread", () -> new WeaponStats(6, 10, 15, 6.0f, 1, Float.NaN, 16.0f, 4.0f, 100));
    }

    @Test
    void nonPositiveRangeIsRejected() {
        assertRejects("engagementRange", () -> new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, 0.0f, 4.0f, 100));
        assertRejects("engagementRange", () -> new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, Float.POSITIVE_INFINITY, 4.0f, 100));
    }

    @Test
    void nonPositiveSpeedIsRejected() {
        assertRejects("projectileSpeed", () -> new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, 16.0f, 0.0f, 100));
    }

    @Test
    void lifetimeBelowOneIsRejected() {
        assertRejects("projectileLifetimeTicks", () -> new WeaponStats(6, 10, 15, 6.0f, 1, 0.045f, 16.0f, 4.0f, 0));
    }

    // --- fullReloadTicks --------------------------------------------------------

    @Test
    void fullReloadIsPerRoundTimesCapacity() {
        assertEquals(60, revolver().fullReloadTicks());
        assertEquals(10, revolver().withCapacity(1).fullReloadTicks());
        assertEquals(0, revolver().withReloadTicksPerRound(0).fullReloadTicks());
    }

    @Test
    void fullReloadThatOverflowsThrows() {
        WeaponStats huge = revolver().withCapacity(Integer.MAX_VALUE).withReloadTicksPerRound(2);
        assertThrows(ArithmeticException.class, huge::fullReloadTicks);
    }

    // --- scaled -----------------------------------------------------------------

    @Test
    void scaledMultipliesDamageAndSpreadOnly() {
        WeaponStats s = revolver().scaled(2.0f, 3.0f);
        assertEquals(12.0f, s.damage());
        assertEquals(0.135f, s.spread(), 1e-6f);
        assertEquals(revolver().capacity(), s.capacity());
        assertEquals(revolver().fireRateTicks(), s.fireRateTicks());
        assertEquals(revolver().engagementRange(), s.engagementRange());
    }

    @Test
    void scaledByOneIsTheIdentity() {
        assertEquals(revolver(), revolver().scaled(1.0f, 1.0f));
    }

    @Test
    void damageMultiplierZeroIsAllowedAndZeroesDamage() {
        assertEquals(0.0f, revolver().scaled(0.0f, 1.0f).damage());
    }

    @Test
    void scaledRejectsBadMultipliers() {
        assertRejects("damageMultiplier", () -> revolver().scaled(-1.0f, 1.0f));
        assertRejects("damageMultiplier", () -> revolver().scaled(Float.NaN, 1.0f));
        assertRejects("spreadMultiplier", () -> revolver().scaled(1.0f, 0.0f));
        assertRejects("spreadMultiplier", () -> revolver().scaled(1.0f, -1.0f));
    }

    // --- withX ------------------------------------------------------------------

    @Test
    void withersChangeExactlyOneField() {
        WeaponStats base = revolver();
        assertEquals(base.withCapacity(9).capacity(), 9);
        assertEquals(base.withCapacity(9).withCapacity(6), base);
        assertEquals(base.withDamage(12.0f).damage(), 12.0f);
        assertEquals(base.withDamage(12.0f).withDamage(6.0f), base);
        assertEquals(base.withFireRateTicks(20).fireRateTicks(), 20);
        assertEquals(base.withReloadTicksPerRound(40).reloadTicksPerRound(), 40);
        assertEquals(base.withProjectilesPerShot(5).projectilesPerShot(), 5);
    }

    @Test
    void withersRecheckTheInvariant() {
        assertRejects("capacity", () -> revolver().withCapacity(0));
        assertRejects("damage", () -> revolver().withDamage(-1.0f));
        assertRejects("fireRateTicks", () -> revolver().withFireRateTicks(0));
        assertRejects("reloadTicksPerRound", () -> revolver().withReloadTicksPerRound(-1));
        assertRejects("projectilesPerShot", () -> revolver().withProjectilesPerShot(0));
    }
}
