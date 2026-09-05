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

import com.nfx.armedpillagers.domain.LoadoutRules.Weighted;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LoadoutRules}.
 *
 * <p>Partitions. Candidates: none / one / several, equal weights / several,
 * unequal weights. Armed chance: 0 / interior / 1 / below 0 / above 1 / NaN.
 * Weight: 1 / large / below 1. Value: present / null. Result: order kept;
 * chances sum to the armed chance; each chance is the weight's share; the
 * shipped 80/50/15 at 0.145 reproduces 8%, 5%, 1.5%; the entries feed
 * {@link WeightedChoice#of} as-is.
 */
final class LoadoutRulesTest {

    private static final double EPS = 1e-12;

    @Test
    void noCandidatesGiveNoSlices() {
        assertTrue(LoadoutRules.slices(List.<Weighted<String>>of(), 0.5).isEmpty());
    }

    @Test
    void oneCandidateTakesTheWholeArmedChance() {
        var slices = LoadoutRules.slices(List.of(new Weighted<>("revolver", 7)), 0.3);
        assertEquals(1, slices.size());
        assertEquals("revolver", slices.get(0).value());
        assertEquals(0.3, slices.get(0).chance(), EPS);
    }

    @Test
    void equalWeightsSplitEvenly() {
        var slices = LoadoutRules.slices(List.of(
                new Weighted<>("a", 5), new Weighted<>("b", 5), new Weighted<>("c", 5)), 0.6);
        for (var slice : slices) {
            assertEquals(0.2, slice.chance(), EPS, slice.value());
        }
    }

    @Test
    void shippedWeightsReproduceTheTunedChances() {
        var slices = LoadoutRules.slices(List.of(
                new Weighted<>("revolver", 80), new Weighted<>("rifle", 50), new Weighted<>("shotgun", 15)), 0.145);
        assertEquals(0.08, slices.get(0).chance(), EPS);
        assertEquals(0.05, slices.get(1).chance(), EPS);
        assertEquals(0.015, slices.get(2).chance(), EPS);
    }

    @Test
    void chancesSumToTheArmedChance() {
        var slices = LoadoutRules.slices(List.of(
                new Weighted<>("a", 3), new Weighted<>("b", 7), new Weighted<>("c", 11), new Weighted<>("d", 1)), 0.37);
        double sum = 0;
        for (var slice : slices) {
            sum += slice.chance();
        }
        assertEquals(0.37, sum, EPS);
    }

    @Test
    void orderIsTheCandidatesOrder() {
        var slices = LoadoutRules.slices(List.of(
                new Weighted<>("rare", 1), new Weighted<>("common", 100), new Weighted<>("middling", 10)), 1.0);
        assertEquals(List.of("rare", "common", "middling"),
                slices.stream().map(WeightedChoice.Entry::value).toList());
    }

    @Test
    void armedChanceZeroArmsNobody() {
        var slices = LoadoutRules.slices(List.of(new Weighted<>("a", 1), new Weighted<>("b", 2)), 0.0);
        for (var slice : slices) {
            assertEquals(0.0, slice.chance(), 0.0);
        }
    }

    @Test
    void armedChanceOneArmsEverybody() {
        var slices = LoadoutRules.slices(List.of(new Weighted<>("a", 1), new Weighted<>("b", 3)), 1.0);
        assertEquals(0.25, slices.get(0).chance(), EPS);
        assertEquals(0.75, slices.get(1).chance(), EPS);
    }

    @Test
    void armedChanceOutsideTheUnitIntervalIsRefused() {
        List<Weighted<String>> one = List.of(new Weighted<>("a", 1));
        assertThrows(IllegalArgumentException.class, () -> LoadoutRules.slices(one, -0.1));
        assertThrows(IllegalArgumentException.class, () -> LoadoutRules.slices(one, 1.1));
        assertThrows(IllegalArgumentException.class, () -> LoadoutRules.slices(one, Double.NaN));
    }

    @Test
    void weightBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("a", 0));
        assertThrows(IllegalArgumentException.class, () -> new Weighted<>("a", -5));
        new Weighted<>("a", 1);
        new Weighted<>("a", Integer.MAX_VALUE);
    }

    @Test
    void nullValueIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Weighted<String>(null, 1));
    }

    @Test
    void slicesFeedAWeightedChoiceDirectly() {
        WeightedChoice<String> choice = WeightedChoice.of(LoadoutRules.slices(List.of(
                new Weighted<>("revolver", 80), new Weighted<>("rifle", 50), new Weighted<>("shotgun", 15)), 0.145));
        assertEquals(0.145, choice.totalChance(), EPS);
        assertTrue(choice.select(0.5).isEmpty(), "a roll past the armed chance is a crossbow");
        assertTrue(choice.select(0.0).isPresent(), "a roll of zero is armed");
    }
}
