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
package com.nfx.armedpillagers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The {@code armedpillagers:pillager_loadouts} data map: which weapons a
 * newly spawned pillager may be issued, and how often relative to each
 * other.
 *
 * <p>Any datapack contributes entries at
 * {@code data/armedpillagers/data_maps/item/pillager_loadouts.json}. A value
 * is a bare weight ({@code "anothergunmod:revolver": 80}) or an object
 * ({@code {"weight": 80}}); the loader merges every pack's entries and
 * honours {@code "replace": true}. Weights are relative: how often a
 * pillager is armed at all is config ({@code armedChance}), and the
 * conversion is {@link com.nfx.armedpillagers.domain.LoadoutRules}.
 *
 * <p>A loadout is a request, not a guarantee. {@link LoadoutTable} admits an
 * entry only if the item resolves to a weapon -- it has a profile, or a gun
 * mod provides for it -- and its class is not denied by config. Not synced:
 * the AI that reads it is server-side.
 */
public final class PillagerLoadouts {
    private PillagerLoadouts() {}

    /**
     * One entry: a relative weight. Immutable.
     *
     * <p>RI: {@code weight >= 1}. A weight of zero is not "never": remove the
     * entry, or deny its class.
     *
     * @param weight how often this loadout is issued relative to the others
     */
    public record Loadout(int weight) {
        private static final Codec<Integer> WEIGHT = Codec.intRange(1, Integer.MAX_VALUE);

        /** Either {@code {"weight": n}} or the bare number {@code n}. Encodes as the object. */
        public static final Codec<Loadout> CODEC = Codec.withAlternative(
                RecordCodecBuilder.create(i -> i.group(
                        WEIGHT.fieldOf("weight").forGetter(Loadout::weight)
                ).apply(i, Loadout::new)),
                WEIGHT, Loadout::new);

        /**
         * @throws IllegalArgumentException if {@code weight < 1}
         */
        public Loadout {
            if (weight < 1) {
                throw new IllegalArgumentException("weight must be >= 1, was " + weight);
            }
        }
    }

    /** Item to {@link Loadout}, from datapacks. */
    public static final DataMapType<Item, Loadout> PILLAGER_LOADOUTS = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(ArmedPillagers.MOD_ID, "pillager_loadouts"),
            Registries.ITEM, Loadout.CODEC).build();
}
