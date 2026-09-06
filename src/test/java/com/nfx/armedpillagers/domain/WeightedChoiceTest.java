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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nfx.armedpillagers.domain.WeightedChoice.Entry;

/**
 * Tests for {@link WeightedChoice}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * of(entries):
 *   entries:   none / one / three
 *   chances:   all zero / one zero among positives / sum < 1 / sum == 1
 *              / sum > 1 (the last slice is truncated, the first is intact)
 *   ordering:  given rarest-first / given in reverse (sorted on construction)
 *              / equal chances (given order kept)
 *   invalid:   negative chance / NaN chance / infinite chance / duplicate value
 *
 * select(roll):
 *   roll:      0 / inside the first slice / exactly on a boundary L_i (belongs
 *              to slice i) / just below a boundary (belongs to slice i-1)
 *              / inside the last slice / at or past the total (empty)
 *              / the largest double below 1
 *   invalid:   negative / exactly 1 / above 1 / NaN
 *
 * totalChance():
 *   empty / sum < 1 / sum > 1
 * </pre>
 */
final class WeightedChoiceTest {

    /** Rarest first: the default Armed Pillagers config, 0.015 / 0.05 / 0.08. */
    private static WeightedChoice<String> defaults() {
        return WeightedChoice.of(List.of(
                new Entry<>("shotgun", 0.015),
                new Entry<>("rifle", 0.05),
                new Entry<>("revolver", 0.08)));
    }

    // --- of: entries and chances --------------------------------------------

    @Test
    void noEntriesNeverChooses() {
        WeightedChoice<String> none = WeightedChoice.of(List.of());
        assertEquals(Optional.empty(), none.select(0.0));
        assertEquals(Optional.empty(), none.select(0.5));
    }

    @Test
    void oneEntryIsChosenBelowItsChanceAndNotAbove() {
        WeightedChoice<String> one = WeightedChoice.of(List.of(new Entry<>("only", 0.25)));
        assertEquals(Optional.of("only"), one.select(0.2));
        assertEquals(Optional.empty(), one.select(0.25));
    }

    @Test
    void allZeroChancesNeverChoose() {
        WeightedChoice<String> zeros = WeightedChoice.of(List.of(
                new Entry<>("a", 0.0), new Entry<>("b", 0.0)));
        assertEquals(Optional.empty(), zeros.select(0.0));
    }

    @Test
    void aZeroChanceEntryIsSkippedOver() {
        // "never" has an empty slice at the front; the roll 0 must fall through
        // to "sometimes", whose slice starts where "never"'s empty one does.
        WeightedChoice<String> choice = WeightedChoice.of(List.of(
                new Entry<>("never", 0.0), new Entry<>("sometimes", 0.5)));
        assertEquals(Optional.of("sometimes"), choice.select(0.0));
    }

    @Test
    void chancesSummingBelowOneLeaveARemainderThatChoosesNothing() {
        // The slices end at 0.015 + 0.05 + 0.08 accumulated in double, which
        // is 0.14500000000000000388. The literal 0.145 is not that number: it
        // sits 4e-18 *below* it, so it lands inside the last slice. Rolls
        // clearly past the total avoid asserting on a representation
        // artefact; the exact boundary is rollAtTheTotalChoosesNothing, which
        // builds the sum the way select does.
        assertEquals(Optional.empty(), defaults().select(0.2));
        assertEquals(Optional.empty(), defaults().select(0.9));
    }

    @Test
    void chancesSummingToExactlyOneAlwaysChoose() {
        WeightedChoice<String> full = WeightedChoice.of(List.of(
                new Entry<>("a", 0.25), new Entry<>("b", 0.75)));
        assertTrue(full.select(0.0).isPresent());
        assertTrue(full.select(Math.nextDown(1.0)).isPresent());
    }

    @Test
    void chancesSummingPastOneTruncateTheCommonEntryNotTheRareOne() {
        // rare keeps its full 0.3; common is cut from 0.9 to the remaining 0.7.
        WeightedChoice<String> over = WeightedChoice.of(List.of(
                new Entry<>("common", 0.9), new Entry<>("rare", 0.3)));
        assertEquals(Optional.of("rare"), over.select(0.0));
        assertEquals(Optional.of("rare"), over.select(Math.nextDown(0.3)));
        assertEquals(Optional.of("common"), over.select(0.3));
        assertEquals(Optional.of("common"), over.select(Math.nextDown(1.0)));
    }

    // --- of: ordering -------------------------------------------------------

    @Test
    void entriesGivenRarestFirstKeepThatOrder() {
        assertEquals(Optional.of("shotgun"), defaults().select(0.0));
        assertEquals(Optional.of("rifle"), defaults().select(0.015));
        assertEquals(Optional.of("revolver"), defaults().select(0.065));
    }

    @Test
    void entriesGivenInReverseAreSortedRarestFirst() {
        WeightedChoice<String> reversed = WeightedChoice.of(List.of(
                new Entry<>("revolver", 0.08),
                new Entry<>("rifle", 0.05),
                new Entry<>("shotgun", 0.015)));
        assertEquals(Optional.of("shotgun"), reversed.select(0.0));
        assertEquals(Optional.of("rifle"), reversed.select(0.015));
        assertEquals(Optional.of("revolver"), reversed.select(0.065));
    }

    @Test
    void equalChancesKeepTheirGivenOrder() {
        WeightedChoice<String> tied = WeightedChoice.of(List.of(
                new Entry<>("first", 0.1), new Entry<>("second", 0.1)));
        assertEquals(Optional.of("first"), tied.select(0.05));
        assertEquals(Optional.of("second"), tied.select(0.15));
    }

    // --- of: invalid --------------------------------------------------------

    @Test
    void rejectsANegativeChance() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.of(List.of(new Entry<>("a", -0.1))));
    }

    @Test
    void rejectsANaNChance() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.of(List.of(new Entry<>("a", Double.NaN))));
    }

    @Test
    void rejectsAnInfiniteChance() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.of(List.of(new Entry<>("a", Double.POSITIVE_INFINITY))));
    }

    @Test
    void rejectsDuplicateValues() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChoice.of(List.of(new Entry<>("a", 0.1), new Entry<>("a", 0.2))));
    }

    // --- select: rolls ------------------------------------------------------

    @Test
    void rollOfZeroLandsInTheFirstSlice() {
        assertEquals(Optional.of("shotgun"), defaults().select(0.0));
    }

    @Test
    void rollInsideTheFirstSlice() {
        assertEquals(Optional.of("shotgun"), defaults().select(0.01));
    }

    @Test
    void rollExactlyOnABoundaryBelongsToTheSliceThatStartsThere() {
        // L_1 = 0.015 is where rifle begins.
        assertEquals(Optional.of("rifle"), defaults().select(0.015));
    }

    @Test
    void rollJustBelowABoundaryBelongsToTheSliceBeforeIt() {
        assertEquals(Optional.of("shotgun"), defaults().select(Math.nextDown(0.015)));
    }

    @Test
    void rollInsideTheLastSlice() {
        assertEquals(Optional.of("revolver"), defaults().select(0.1));
    }

    @Test
    void rollAtTheTotalChoosesNothing() {
        assertEquals(Optional.empty(), defaults().select(0.015 + 0.05 + 0.08));
    }

    @Test
    void largestRollBelowOneIsAccepted() {
        assertEquals(Optional.empty(), defaults().select(Math.nextDown(1.0)));
    }

    // --- select: invalid ----------------------------------------------------

    @Test
    void rejectsANegativeRoll() {
        assertThrows(IllegalArgumentException.class, () -> defaults().select(-0.1));
    }

    @Test
    void rejectsARollOfExactlyOne() {
        assertThrows(IllegalArgumentException.class, () -> defaults().select(1.0));
    }

    @Test
    void rejectsARollAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> defaults().select(1.5));
    }

    @Test
    void rejectsANaNRoll() {
        assertThrows(IllegalArgumentException.class, () -> defaults().select(Double.NaN));
    }

    // --- totalChance --------------------------------------------------------

    @Test
    void totalOfNoEntriesIsZero() {
        assertEquals(0.0, WeightedChoice.of(List.<Entry<String>>of()).totalChance());
    }

    @Test
    void totalBelowOneIsTheSum() {
        assertEquals(0.145, defaults().totalChance(), 1e-12);
    }

    @Test
    void totalMayExceedOne() {
        WeightedChoice<String> over = WeightedChoice.of(List.of(
                new Entry<>("a", 0.9), new Entry<>("b", 0.3)));
        assertEquals(1.2, over.totalChance(), 1e-12);
    }
}
