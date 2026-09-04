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

import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DropRules}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * clampedDamage(damage, maxDamage, wearCap):
 *   wearCap:      >= 1 (exactly 1, above 1) leaves damage alone / 0 / in (0, 1)
 *   damage vs cap (wearCap in (0,1)):  below the cap / exactly at / above (clamped)
 *   truncation:   maxDamage * wearCap fractional truncates toward zero
 *   maxDamage:    0
 *   invalid:      negative damage / negative maxDamage / negative, NaN, infinite wearCap
 *
 * magazineRounds(a, b, capacity, uniform):
 *   bound order:  a < b / a == b / a > b (swapped config gives the same result)
 *   result vs capacity:  below / exactly at / above (clamped)
 *   degenerate:   a == b == 0 -> 0
 *   roll:         uniform is called exactly once, with bound |a - b| + 1
 *   invalid:      negative a / negative b / negative capacity
 *
 * ammoCount(dropMax, maxStackSize, uniform):
 *   roll:         0 -> 1 / dropMax - 1 -> dropMax
 *   dropMax:      1 (only ever one round) / > maxStackSize (clamped)
 *   roll:         uniform is called exactly once, with bound dropMax
 *   invalid:      dropMax 0 / maxStackSize 0
 * </pre>
 */
final class DropRulesTest {

    /** A roll that always lands on {@code value} and records how it was asked. */
    private static final class FixedRoll implements IntUnaryOperator {
        private final int value;
        int calls;
        int lastBound;

        FixedRoll(int value) {
            this.value = value;
        }

        @Override
        public int applyAsInt(int bound) {
            calls++;
            lastBound = bound;
            return value;
        }
    }

    // --- clampedDamage ------------------------------------------------------

    @Test
    void capOfOneLeavesVanillaDamageAlone() {
        assertEquals(95, DropRules.clampedDamage(95, 100, 1.0));
    }

    @Test
    void capAboveOneLeavesVanillaDamageAlone() {
        assertEquals(95, DropRules.clampedDamage(95, 100, 1.5));
    }

    @Test
    void capOfZeroMeansAPristineGun() {
        assertEquals(0, DropRules.clampedDamage(95, 100, 0.0));
    }

    @Test
    void damageBelowTheCapIsUnchanged() {
        // 100 * 0.6 = 60; 40 is under it.
        assertEquals(40, DropRules.clampedDamage(40, 100, 0.6));
    }

    @Test
    void damageExactlyAtTheCapIsUnchanged() {
        assertEquals(60, DropRules.clampedDamage(60, 100, 0.6));
    }

    @Test
    void damageAboveTheCapIsClampedToIt() {
        assertEquals(60, DropRules.clampedDamage(95, 100, 0.6));
    }

    @Test
    void theCapTruncatesTowardZero() {
        // 7 * 0.6 = 4.2 -> 4.
        assertEquals(4, DropRules.clampedDamage(7, 7, 0.6));
    }

    @Test
    void zeroMaxDamageClampsEverythingToZero() {
        assertEquals(0, DropRules.clampedDamage(5, 0, 0.6));
    }

    @Test
    void clampRejectsNegativeDamage() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.clampedDamage(-1, 100, 0.6));
    }

    @Test
    void clampRejectsNegativeMaxDamage() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.clampedDamage(5, -1, 0.6));
    }

    @Test
    void clampRejectsBadCaps() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.clampedDamage(5, 100, -0.1));
        assertThrows(IllegalArgumentException.class, () -> DropRules.clampedDamage(5, 100, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> DropRules.clampedDamage(5, 100, Double.POSITIVE_INFINITY));
    }

    // --- magazineRounds -----------------------------------------------------

    @Test
    void roundsWithOrderedBoundsAreLowPlusTheRoll() {
        assertEquals(4 + 7, DropRules.magazineRounds(4, 16, 32, new FixedRoll(7)));
    }

    @Test
    void roundsWithEqualBoundsAreThatBound() {
        FixedRoll roll = new FixedRoll(0);
        assertEquals(10, DropRules.magazineRounds(10, 10, 32, roll));
        assertEquals(1, roll.lastBound);
    }

    @Test
    void roundsWithSwappedBoundsMatchTheOrderedResult() {
        assertEquals(DropRules.magazineRounds(4, 16, 32, new FixedRoll(7)),
                DropRules.magazineRounds(16, 4, 32, new FixedRoll(7)));
    }

    @Test
    void roundsBelowCapacityAreUnchanged() {
        assertEquals(11, DropRules.magazineRounds(4, 16, 32, new FixedRoll(7)));
    }

    @Test
    void roundsExactlyAtCapacityAreUnchanged() {
        assertEquals(32, DropRules.magazineRounds(20, 40, 32, new FixedRoll(12)));
    }

    @Test
    void roundsAboveCapacityAreClampedToIt() {
        assertEquals(32, DropRules.magazineRounds(20, 40, 32, new FixedRoll(20)));
    }

    @Test
    void zeroBoundsGiveAnEmptyMagazine() {
        assertEquals(0, DropRules.magazineRounds(0, 0, 32, new FixedRoll(0)));
    }

    @Test
    void roundsRollExactlyOnceWithTheRangeWidth() {
        FixedRoll roll = new FixedRoll(0);
        DropRules.magazineRounds(4, 16, 32, roll);
        assertEquals(1, roll.calls);
        assertEquals(16 - 4 + 1, roll.lastBound);
    }

    @Test
    void roundsRejectNegativeBounds() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.magazineRounds(-1, 16, 32, new FixedRoll(0)));
        assertThrows(IllegalArgumentException.class, () -> DropRules.magazineRounds(4, -1, 32, new FixedRoll(0)));
    }

    @Test
    void roundsRejectNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.magazineRounds(4, 16, -1, new FixedRoll(0)));
    }

    // --- ammoCount ----------------------------------------------------------

    @Test
    void lowestRollDropsOneRound() {
        assertEquals(1, DropRules.ammoCount(3, 64, new FixedRoll(0)));
    }

    @Test
    void highestRollDropsTheMaximum() {
        assertEquals(3, DropRules.ammoCount(3, 64, new FixedRoll(2)));
    }

    @Test
    void dropMaxOfOneAlwaysDropsOne() {
        assertEquals(1, DropRules.ammoCount(1, 64, new FixedRoll(0)));
    }

    @Test
    void countNeverExceedsTheStackSize() {
        assertEquals(16, DropRules.ammoCount(64, 16, new FixedRoll(63)));
    }

    @Test
    void countRollsExactlyOnceWithDropMax() {
        FixedRoll roll = new FixedRoll(0);
        DropRules.ammoCount(3, 64, roll);
        assertEquals(1, roll.calls);
        assertEquals(3, roll.lastBound);
    }

    @Test
    void countRejectsAZeroMaximum() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.ammoCount(0, 64, new FixedRoll(0)));
    }

    @Test
    void countRejectsAZeroStackSize() {
        assertThrows(IllegalArgumentException.class, () -> DropRules.ammoCount(3, 0, new FixedRoll(0)));
    }
}
