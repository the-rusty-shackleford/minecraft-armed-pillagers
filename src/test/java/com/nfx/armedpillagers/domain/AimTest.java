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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Aim}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * target relative to eye:  level and ahead / above / below / behind
 * targetHeight:            0 (aim at the feet) / positive (aim at 0.6 of it)
 * degenerate:              eye coincides with the aim point -> direction ZERO, muzzle == eye
 * invariants:              |direction| == 1 / |muzzle - eye| == MUZZLE_OFFSET
 *                          / the aim point is exactly feet + 0.6 * height
 * invalid:                 negative / NaN / infinite targetHeight
 * </pre>
 */
final class AimTest {
    private static final double EPS = 1e-12;

    private static void assertVec(Vec expected, Vec actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x");
        assertEquals(expected.y(), actual.y(), EPS, "y");
        assertEquals(expected.z(), actual.z(), EPS, "z");
    }

    /** A standing shooter: eye 1.6 above its feet at the origin. */
    private static final Vec EYE = new Vec(0, 1.6, 0);

    @Test
    void levelTargetAheadIsAimedStraightAlongTheAxis() {
        // Feet at z=10, height 1.6 * ... choose height so 0.6 * h == 1.6: h = 8/3.
        Aim aim = Aim.at(EYE, new Vec(0, 0, 10), 1.6 / 0.6);
        assertVec(new Vec(0, 0, 1), aim.direction());
    }

    @Test
    void higherTargetAimsUpward() {
        Aim aim = Aim.at(EYE, new Vec(0, 10, 10), 2.0);
        assertEquals(true, aim.direction().y() > 0);
    }

    @Test
    void lowerTargetAimsDownward() {
        Aim aim = Aim.at(EYE, new Vec(0, -10, 10), 2.0);
        assertEquals(true, aim.direction().y() < 0);
    }

    @Test
    void targetBehindAimsBackward() {
        Aim aim = Aim.at(EYE, new Vec(0, 0, -10), 1.6 / 0.6);
        assertVec(new Vec(0, 0, -1), aim.direction());
    }

    @Test
    void zeroHeightAimsAtTheFeet() {
        Vec feet = new Vec(3, 0, 4);
        Aim aim = Aim.at(EYE, feet, 0);
        assertVec(feet.subtract(EYE).normalize(), aim.direction());
    }

    @Test
    void theAimPointIsSixTenthsOfTheWayUpTheTarget() {
        // Eye and feet on the same vertical line, so the direction is purely
        // vertical and its sign tells us where the aim point is.
        Vec feet = new Vec(0, 0, 0);
        double height = 2.0; // aim point at y = 1.2, below the 1.6 eye
        Aim aim = Aim.at(EYE, feet, height);
        assertVec(new Vec(0, -1, 0), aim.direction());
        Aim higher = Aim.at(EYE, feet, 4.0); // aim point at y = 2.4, above the eye
        assertVec(new Vec(0, 1, 0), higher.direction());
    }

    @Test
    void directionIsUnitLength() {
        Aim aim = Aim.at(EYE, new Vec(7, -3, 12), 1.95);
        assertEquals(1.0, aim.direction().length(), EPS);
    }

    @Test
    void muzzleSitsTheOffsetAlongTheAimLineFromTheEye() {
        Aim aim = Aim.at(EYE, new Vec(7, -3, 12), 1.95);
        assertEquals(Aim.MUZZLE_OFFSET, aim.muzzle().subtract(EYE).length(), EPS);
        assertVec(EYE.add(aim.direction().scale(Aim.MUZZLE_OFFSET)), aim.muzzle());
    }

    @Test
    void eyeOnTheAimPointHasNoDirectionAndMuzzleAtTheEye() {
        // Feet directly below the eye, height chosen so 0.6 * h == 1.6.
        Aim aim = Aim.at(EYE, new Vec(0, 0, 0), 1.6 / 0.6);
        assertVec(Vec.ZERO, aim.direction());
        assertVec(EYE, aim.muzzle());
    }

    @Test
    void rejectsANegativeHeight() {
        assertThrows(IllegalArgumentException.class, () -> Aim.at(EYE, Vec.ZERO, -1));
    }

    @Test
    void rejectsANaNHeight() {
        assertThrows(IllegalArgumentException.class, () -> Aim.at(EYE, Vec.ZERO, Double.NaN));
    }

    @Test
    void rejectsAnInfiniteHeight() {
        assertThrows(IllegalArgumentException.class, () -> Aim.at(EYE, Vec.ZERO, Double.POSITIVE_INFINITY));
    }
}
