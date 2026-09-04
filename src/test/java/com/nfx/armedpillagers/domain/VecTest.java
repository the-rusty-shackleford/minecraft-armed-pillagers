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

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Vec}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * constructor:   all finite / a NaN component / an infinite component
 * add, subtract: positive, negative and zero components; the identity
 * scale:         by 1 / by 0 / by a negative / by a factor that overflows to infinity
 * length:        zero / an axis vector / a 3-4-5 triangle in the plane
 * normalize:     already unit / longer than unit / shorter than unit but above
 *                the epsilon / exactly at the epsilon (has a direction)
 *                / just below the epsilon (ZERO) / the zero vector (ZERO)
 * </pre>
 */
final class VecTest {
    private static final double EPS = 1e-12;

    private static void assertVec(Vec expected, Vec actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x");
        assertEquals(expected.y(), actual.y(), EPS, "y");
        assertEquals(expected.z(), actual.z(), EPS, "z");
    }

    // --- constructor --------------------------------------------------------

    @Test
    void finiteComponentsAreAccepted() {
        assertVec(new Vec(1, -2, 0.5), new Vec(1, -2, 0.5));
    }

    @Test
    void aNaNComponentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Vec(Double.NaN, 0, 0));
    }

    @Test
    void anInfiniteComponentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Vec(0, Double.NEGATIVE_INFINITY, 0));
    }

    // --- add / subtract -----------------------------------------------------

    @Test
    void addIsComponentwise() {
        assertVec(new Vec(1, -1, 3), new Vec(2, -3, 0).add(new Vec(-1, 2, 3)));
    }

    @Test
    void addingZeroIsTheIdentity() {
        assertVec(new Vec(2, -3, 0), new Vec(2, -3, 0).add(Vec.ZERO));
    }

    @Test
    void subtractIsComponentwise() {
        assertVec(new Vec(3, -5, -3), new Vec(2, -3, 0).subtract(new Vec(-1, 2, 3)));
    }

    @Test
    void subtractingItselfIsZero() {
        assertVec(Vec.ZERO, new Vec(2, -3, 0).subtract(new Vec(2, -3, 0)));
    }

    // --- scale --------------------------------------------------------------

    @Test
    void scaleByOneIsTheIdentity() {
        assertVec(new Vec(2, -3, 0.5), new Vec(2, -3, 0.5).scale(1));
    }

    @Test
    void scaleByZeroIsZero() {
        assertVec(Vec.ZERO, new Vec(2, -3, 0.5).scale(0));
    }

    @Test
    void scaleByANegativeFlips() {
        assertVec(new Vec(-4, 6, -1), new Vec(2, -3, 0.5).scale(-2));
    }

    @Test
    void scaleThatOverflowsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Vec(Double.MAX_VALUE, 0, 0).scale(2));
    }

    // --- length -------------------------------------------------------------

    @Test
    void lengthOfZeroIsZero() {
        assertEquals(0.0, Vec.ZERO.length(), EPS);
    }

    @Test
    void lengthOfAnAxisVectorIsItsComponent() {
        assertEquals(7.0, new Vec(0, -7, 0).length(), EPS);
    }

    @Test
    void lengthOfAThreeFourIsFive() {
        assertEquals(5.0, new Vec(3, 0, 4).length(), EPS);
    }

    // --- normalize ----------------------------------------------------------

    @Test
    void normalizingAUnitVectorIsTheIdentity() {
        assertVec(new Vec(0, 0, 1), new Vec(0, 0, 1).normalize());
    }

    @Test
    void normalizingALongVectorGivesUnitLength() {
        Vec unit = new Vec(3, 0, 4).normalize();
        assertVec(new Vec(0.6, 0, 0.8), unit);
        assertEquals(1.0, unit.length(), EPS);
    }

    @Test
    void normalizingAShortVectorAboveTheEpsilonGivesUnitLength() {
        assertEquals(1.0, new Vec(0.001, 0, 0).normalize().length(), EPS);
    }

    @Test
    void aVectorExactlyAtTheEpsilonStillHasADirection() {
        assertVec(new Vec(1, 0, 0), new Vec(Vec.NORMALIZE_EPSILON, 0, 0).normalize());
    }

    @Test
    void aVectorJustBelowTheEpsilonNormalizesToZero() {
        assertVec(Vec.ZERO, new Vec(Math.nextDown(Vec.NORMALIZE_EPSILON), 0, 0).normalize());
    }

    @Test
    void theZeroVectorNormalizesToZero() {
        assertVec(Vec.ZERO, Vec.ZERO.normalize());
    }
}
