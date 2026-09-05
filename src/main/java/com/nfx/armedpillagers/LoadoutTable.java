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
package com.nfx.armedpillagers;

import com.nfx.armedpillagers.PillagerLoadouts.Loadout;
import com.nfx.armedpillagers.domain.LoadoutRules;
import com.nfx.armedpillagers.domain.LoadoutRules.Weighted;
import com.nfx.armedpillagers.domain.WeightedChoice;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The loadouts a newly spawned pillager can actually be issued: the
 * {@link PillagerLoadouts} entries that resolve to a weapon and whose class
 * is not denied, with their relative shares. Immutable; rebuilt whole on
 * every datapack reload and swapped in as one reference.
 *
 * <p>The armed chance is read from config at roll time rather than baked in
 * here, so changing it needs no reload; the denied classes are applied at
 * build time, so changing those takes effect on the next {@code /reload}.
 *
 * <p>AF: the set of admissible loadouts and, for each, its share of the
 * armed pillagers.<br>
 * RI: {@code shares} holds one entry per element of {@code candidates}, in
 * the same order, and their chances sum to one unless there are none.
 */
public final class LoadoutTable {

    private static final LoadoutTable EMPTY = new LoadoutTable(List.of());
    private static volatile LoadoutTable current = EMPTY;

    private final List<Weighted<Item>> candidates;
    private final List<WeightedChoice.Entry<Item>> shares;
    private final WeightedChoice<Item> choice;

    private LoadoutTable(List<Weighted<Item>> candidates) {
        this.candidates = List.copyOf(candidates);
        this.shares = LoadoutRules.slices(this.candidates, 1.0);
        this.choice = WeightedChoice.of(this.shares);
    }

    /** effects: returns the live table; empty until the first reload builds one */
    public static LoadoutTable current() {
        return current;
    }

    /**
     * One spawn roll: a crossbow, or one of the admitted loadouts in its
     * share.
     *
     * <p>effects: with probability {@code armedChance} from config returns a
     * loadout chosen by share, otherwise empty; draws exactly one number from
     * {@code random}
     *
     * @param random the pillager's own random
     * @return the gun to issue, or empty for a crossbow
     */
    public Optional<Item> roll(RandomSource random) {
        double roll = random.nextDouble();
        double armedChance = ApConfig.ARMED_CHANCE.get();
        if (candidates.isEmpty() || roll >= armedChance) {
            return Optional.empty();
        }
        // roll < armedChance, so the quotient is below one -- except when
        // rounding carries it to exactly one, which select() refuses.
        return choice.select(Math.min(roll / armedChance, Math.nextDown(1.0)));
    }

    /** effects: returns the admitted items in id order; never null */
    public List<Item> admitted() {
        return candidates.stream().map(Weighted::value).toList();
    }

    /**
     * Rebuilds the live table from the registry's current data maps. Called
     * once per reload, after every data map on the item registry has been
     * applied, so both the loadouts and the profiles they depend on are
     * current.
     *
     * <p>effects: replaces {@link #current()}; logs one line per entry it
     * refuses, saying why, and one summary
     *
     * @param items the item registry carrying the reloaded data maps
     */
    static void rebuild(Registry<Item> items) {
        current = build(items, Set.copyOf(ApConfig.DENIED_CLASSES.get()));
    }

    static LoadoutTable build(Registry<Item> items, Set<String> deniedClasses) {
        Map<ResourceKey<Item>, Loadout> loadouts = items.getDataMap(PillagerLoadouts.PILLAGER_LOADOUTS);
        List<Map.Entry<ResourceKey<Item>, Loadout>> ordered = new ArrayList<>(loadouts.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().location()));

        List<Weighted<Item>> candidates = new ArrayList<>(ordered.size());
        for (Map.Entry<ResourceKey<Item>, Loadout> entry : ordered) {
            ResourceLocation id = entry.getKey().location();
            Item item = items.get(entry.getKey());
            if (item == null) {
                continue;
            }
            RangedWeapon weapon = RangedWeapons.resolve(new ItemStack(item));
            if (weapon == null) {
                ArmedPillagers.LOGGER.warn("pillager loadout {} skipped: no gun mod provides for it and no "
                        + "rangedweapons:weapons profile describes it, so it is not a weapon", id);
                continue;
            }
            String weaponClass = weapon.profile().weaponClass().name();
            if (deniedClasses.contains(weaponClass)) {
                ArmedPillagers.LOGGER.info("pillager loadout {} refused: class '{}' is denied by config", id, weaponClass);
                continue;
            }
            candidates.add(new Weighted<>(item, entry.getValue().weight()));
        }

        LoadoutTable table = new LoadoutTable(candidates);
        if (candidates.isEmpty()) {
            ArmedPillagers.LOGGER.warn("no pillager loadout admitted; pillagers keep their crossbows");
        } else {
            ArmedPillagers.LOGGER.info("pillager loadouts admitted, as shares of armed pillagers (armed chance {}): {}",
                    ApConfig.ARMED_CHANCE.get(), table.describeShares());
        }
        return table;
    }

    private String describeShares() {
        return shares.stream()
                .map(share -> ArmedPillagers.shortName(share.value()) + " " + String.format("%.1f%%", share.chance() * 100.0))
                .collect(Collectors.joining(", "));
    }
}
