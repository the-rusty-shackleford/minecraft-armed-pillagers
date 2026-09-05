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

import com.f708.anothergunmod.registry.item.ModItems;
import com.nfx.armedpillagers.domain.WeightedChoice;
import com.nfx.armedpillagers.weapon.AgmWeapon;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The catalog: the only three guns a pillager is ever allowed to hold, and
 * the spawn roll across them.
 *
 * The auto-gun, machine gun and flamethrower are absent on purpose: sustained
 * automatic fire from a mob that never has to reload mid-burst is not a fight,
 * and the flamethrower sets the world alight. Each entry here is a weapon a
 * player can reasonably duck behind cover from.
 *
 * What each gun <em>is</em> -- its class, spread, range, sounds, ammunition --
 * lives in the {@code rangedweapons:weapons} data map, shipped by this mod at
 * {@code data/rangedweapons/data_maps/item/weapons.json} and editable by any
 * pack. This enum names the guns and which item is which; how a gun is
 * operated lives in {@link AgmWeapon}, behind the {@link RangedWeapon}
 * contract.
 */
public enum PillagerGun {
    REVOLVER("revolver", ModItems.REVOLVER, ModItems.SMALLBULLET),
    RIFLE("rifle", ModItems.RIFLE, ModItems.BIGBULLET),
    SHOTGUN("shotgun", ModItems.SHOTGUN, ModItems.SHELL);

    private final String id;
    private final Supplier<Item> gunItem;
    private final Supplier<Item> ammoItem;
    private final AgmWeapon weapon = new AgmWeapon(this);

    PillagerGun(String id, Supplier<Item> gunItem, Supplier<Item> ammoItem) {
        this.id = id;
        this.gunItem = gunItem;
        this.ammoItem = ammoItem;
    }

    public String id() {
        return id;
    }

    public Item gun() {
        return gunItem.get();
    }

    /** The round this gun is refilled with. The profile's ammo id says what drops. */
    public Item ammo() {
        return ammoItem.get();
    }

    /** The one {@link RangedWeapon} that operates every stack of this gun. */
    public RangedWeapon weapon() {
        return weapon;
    }

    /**
     * This gun's profile from the data map, read live so a pack's changes
     * take effect on reload. Empty if no pack describes the item, in which
     * case the gun is not usable by mobs and the resolver says so.
     */
    public Optional<WeaponProfile> profile() {
        return RangedWeapons.profileOf(gun());
    }

    public static PillagerGun byId(String id) {
        for (PillagerGun gun : values()) {
            if (gun.id.equals(id)) {
                return gun;
            }
        }
        return null;
    }

    public static PillagerGun of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (PillagerGun gun : values()) {
            if (stack.is(gun.gun())) {
                return gun;
            }
        }
        return null;
    }

    /**
     * One roll across all three weapons, or null for a crossbow. The chances
     * are exclusive slices laid out rarest first, so each config number means
     * what it says; {@link WeightedChoice} owns that rule and its tests.
     */
    public static PillagerGun roll(RandomSource random) {
        WeightedChoice<PillagerGun> choice = WeightedChoice.of(List.of(
                new WeightedChoice.Entry<>(SHOTGUN, ApConfig.SHOTGUN_CHANCE.get()),
                new WeightedChoice.Entry<>(RIFLE, ApConfig.RIFLE_CHANCE.get()),
                new WeightedChoice.Entry<>(REVOLVER, ApConfig.REVOLVER_CHANCE.get())));
        return choice.select(random.nextFloat()).orElse(null);
    }

    /** The registry path of an item, for log lines: "revolver", not "anothergunmod:revolver". */
    static String shortName(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? item.toString() : key.getPath();
    }
}
