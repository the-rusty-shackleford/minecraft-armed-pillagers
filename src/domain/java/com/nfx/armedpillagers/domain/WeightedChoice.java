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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One roll across a set of candidates whose chances are exclusive slices of
 * {@code [0, 1)}, laid out rarest first.
 *
 * <p>Each candidate's chance is the width of its slice, and the slices are laid
 * end to end starting from zero. A roll lands in at most one slice; if it lands
 * past the last, nothing is chosen. Because the slices are laid out rarest
 * first, a set of chances that adds up past one truncates the <em>common</em>
 * candidates, never the rare ones -- each rare chance keeps meaning exactly
 * what its config number says, which is the promise that makes the numbers
 * tunable.
 *
 * <p>Immutable. Construction copies and sorts the entries; nothing is retained
 * that a caller can change afterwards, except the candidate values themselves,
 * which the spec requires to be immutable.
 *
 * @param <T> the candidate type
 */
public final class WeightedChoice<T> {

    /**
     * A candidate and its chance.
     *
     * @param value  the candidate; must be immutable and distinct from every
     *               other value in the same choice
     * @param chance the width of its slice; finite and {@code >= 0}
     * @param <T>    the candidate type
     */
    public record Entry<T>(T value, double chance) {}

    private final List<Entry<T>> slices;

    // Abstraction function:
    //   AF(slices) = the distribution over Optional<T> in which, with
    //     L_i = sum of slices[j].chance for j < i,
    //     slices[i].value has probability max(0, min(slices[i].chance, 1 - L_i))
    //     and "no choice" has whatever remains of [0, 1).
    // Rep invariant:
    //   every chance is finite and >= 0;
    //   slices is sorted by chance ascending, stably;
    //   no two entries have equal values;
    //   slices is unmodifiable.
    // Safety from rep exposure:
    //   slices is an unmodifiable copy made in `of`, never returned; Entry is a
    //   record of a double and the client's T, which the spec requires to be
    //   immutable, as for any map key.

    private WeightedChoice(List<Entry<T>> sortedSlices) {
        this.slices = sortedSlices;
        checkRep();
    }

    /**
     * Builds a choice over {@code entries}, ordered rarest first.
     *
     * <p>requires: every value is immutable, and no two values are equal<br>
     * effects: returns the choice whose slices are {@code entries} sorted by
     * chance ascending (stable, so equal chances keep their given order)<br>
     * throws: {@link IllegalArgumentException} if any chance is negative, NaN
     * or infinite, or if two entries have equal values
     *
     * @param entries the candidates and their chances, in any order
     * @param <T>     the candidate type
     * @return the choice
     */
    public static <T> WeightedChoice<T> of(List<Entry<T>> entries) {
        Set<T> seen = new HashSet<>();
        for (Entry<T> entry : entries) {
            double chance = entry.chance();
            // `!(chance >= 0)` rather than `chance < 0` so that NaN fails too.
            if (!(chance >= 0) || Double.isInfinite(chance)) {
                throw new IllegalArgumentException(
                        "chance must be finite and >= 0, was " + chance + " for " + entry.value());
            }
            if (!seen.add(entry.value())) {
                throw new IllegalArgumentException("duplicate candidate " + entry.value());
            }
        }
        List<Entry<T>> sorted = new ArrayList<>(entries);
        // List.sort is stable, which is what keeps equal chances in given order.
        sorted.sort(Comparator.comparingDouble(Entry::chance));
        return new WeightedChoice<>(List.copyOf(sorted));
    }

    /**
     * Selects the candidate whose slice contains {@code roll}.
     *
     * <p>requires: {@code 0 <= roll < 1}<br>
     * effects: returns the value of the first slice {@code i} for which
     * {@code roll < L_i + chance_i}, where {@code L_i} is the sum of the chances
     * before it -- so a roll exactly on a boundary belongs to the slice that
     * starts there -- or empty if no slice contains it<br>
     * throws: {@link IllegalArgumentException} if {@code roll} is outside
     * {@code [0, 1)} or NaN
     *
     * @param roll a uniform draw from {@code [0, 1)}
     * @return the chosen candidate, if any
     */
    public Optional<T> select(double roll) {
        if (!(roll >= 0) || roll >= 1) {
            throw new IllegalArgumentException("roll must be in [0, 1), was " + roll);
        }
        double cumulative = 0;
        for (Entry<T> slice : slices) {
            cumulative += slice.chance();
            if (roll < cumulative) {
                return Optional.of(slice.value());
            }
        }
        return Optional.empty();
    }

    /**
     * The sum of every chance, which may exceed one.
     *
     * <p>effects: returns the sum of the chances of all entries
     *
     * @return the total
     */
    public double totalChance() {
        double total = 0;
        for (Entry<T> slice : slices) {
            total += slice.chance();
        }
        return total;
    }

    private void checkRep() {
        double previous = -1;
        Set<T> seen = new HashSet<>();
        for (Entry<T> slice : slices) {
            assert slice.chance() >= 0 && !Double.isInfinite(slice.chance()) : "chance out of range";
            assert slice.chance() >= previous : "slices not sorted ascending";
            assert seen.add(slice.value()) : "duplicate value";
            previous = slice.chance();
        }
    }
}
