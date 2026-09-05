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

import java.util.ArrayList;
import java.util.List;

/**
 * How loadout weights become spawn chances.
 *
 * <p>A pack says "revolver 80, rifle 50, shotgun 15": relative weights,
 * meaningful only against each other. Config says how often a pillager is
 * armed at all. This turns the two into the exclusive slices a
 * {@link WeightedChoice} rolls over: each weight's share of the total, scaled
 * by the armed chance, so the slices sum to exactly the armed chance and each
 * loadout's share of the armed pillagers is its share of the weight. The
 * shipped 80/50/15 at an armed chance of 0.145 reproduces the 8%, 5% and
 * 1.5% the mod was tuned with.
 */
public final class LoadoutRules {
    private LoadoutRules() {}

    /**
     * A candidate and its relative weight. Immutable.
     *
     * <p>RI: {@code value} is not null; {@code weight >= 1}.
     *
     * @param value  the candidate
     * @param weight its weight relative to the other candidates'
     * @param <T>    the candidate type
     */
    public record Weighted<T>(T value, int weight) {
        /**
         * @throws IllegalArgumentException if {@code value} is null or
         *                                  {@code weight < 1}
         */
        public Weighted {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("weight must be >= 1, was " + weight);
            }
        }
    }

    /**
     * The slices for a set of candidates.
     *
     * <p>requires: {@code 0 <= armedChance <= 1}; the candidates' values are
     * distinct and immutable ({@link WeightedChoice}'s own requirement)<br>
     * effects: returns one entry per candidate, in the given order, with
     * chance {@code armedChance * weight / (sum of all weights)}; the empty
     * list for no candidates<br>
     * throws: {@link IllegalArgumentException} if {@code armedChance} is
     * outside {@code [0, 1]} or not a number
     *
     * @param candidates the loadouts and their weights
     * @param armedChance how often any of them is issued at all
     * @param <T>        the candidate type
     * @return the entries, ready for {@link WeightedChoice#of}
     */
    public static <T> List<WeightedChoice.Entry<T>> slices(List<Weighted<T>> candidates, double armedChance) {
        // `!(a >= 0 && a <= 1)` rather than `a < 0 || a > 1` so that NaN fails too.
        if (!(armedChance >= 0.0 && armedChance <= 1.0)) {
            throw new IllegalArgumentException("armedChance must be in [0, 1], was " + armedChance);
        }
        long total = 0;
        for (Weighted<T> candidate : candidates) {
            total += candidate.weight();
        }
        List<WeightedChoice.Entry<T>> entries = new ArrayList<>(candidates.size());
        for (Weighted<T> candidate : candidates) {
            entries.add(new WeightedChoice.Entry<>(candidate.value(), armedChance * candidate.weight() / total));
        }
        return List.copyOf(entries);
    }
}
