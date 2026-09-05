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
import com.f708.anothergunmod.sounds.ModSounds;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.armedpillagers.domain.WeightedChoice;
import com.nfx.armedpillagers.weapon.AgmWeapon;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The catalog: the only three guns a pillager is ever allowed to hold, what
 * each is, and the spawn roll across them.
 *
 * The auto-gun, machine gun and flamethrower are absent on purpose: sustained
 * automatic fire from a mob that never has to reload mid-burst is not a fight,
 * and the flamethrower sets the world alight. Each entry here is a weapon a
 * player can reasonably duck behind cover from.
 *
 * Base spreads are looser than {@code GunUtils#getSpread} gives a standing
 * player, so an armed pillager is a worse shot than you are at the same range.
 *
 * How a gun is operated lives in {@link AgmWeapon}, behind the
 * {@link RangedWeapon} contract; this enum only says which guns and what
 * their profiles are.
 */
public enum PillagerGun {
    REVOLVER("revolver", WeaponClass.SIDEARM,
            ModItems.REVOLVER, ModItems.SMALLBULLET, ModSounds.REVOLVER_SHOOT,
            0.045F, ApConfig.REVOLVER_RANGE),

    RIFLE("rifle", WeaponClass.RIFLE,
            ModItems.RIFLE, ModItems.BIGBULLET, ModSounds.RIFLE_SHOT,
            0.030F, ApConfig.RIFLE_RANGE),

    SHOTGUN("shotgun", WeaponClass.SHOTGUN,
            ModItems.SHOTGUN, ModItems.SHELL, ModSounds.SHOTGUN_SHOT,
            0.140F, ApConfig.SHOTGUN_RANGE);

    private final String id;
    private final WeaponClass weaponClass;
    private final Supplier<Item> gunItem;
    private final Supplier<Item> ammoItem;
    private final Supplier<SoundEvent> shotSound;
    private final float baseSpread;
    private final ModConfigSpec.DoubleValue range;

    /** Built on first use, once the registries are frozen and the config loaded. */
    private WeaponProfile profile;
    private final AgmWeapon weapon = new AgmWeapon(this);

    PillagerGun(String id, WeaponClass weaponClass, Supplier<Item> gunItem, Supplier<Item> ammoItem,
                Supplier<SoundEvent> shotSound, float baseSpread, ModConfigSpec.DoubleValue range) {
        this.id = id;
        this.weaponClass = weaponClass;
        this.gunItem = gunItem;
        this.ammoItem = ammoItem;
        this.shotSound = shotSound;
        this.baseSpread = baseSpread;
        this.range = range;
    }

    public String id() {
        return id;
    }

    public Item gun() {
        return gunItem.get();
    }

    public Item ammo() {
        return ammoItem.get();
    }

    /** The catalog's spread, before the consumer's multiplier. */
    public float baseSpread() {
        return baseSpread;
    }

    /** The configured engagement range, read live. */
    public double range() {
        return range.get();
    }

    /** The one {@link RangedWeapon} that operates every stack of this gun. */
    public RangedWeapon weapon() {
        return weapon;
    }

    /**
     * This gun's profile, built once from the catalog entry.
     *
     * <p>Requires the item and sound registries to be frozen, which they are
     * by the time a pillager can spawn. The default stats carry the range as
     * configured at first call; the live per-stack numbers come from
     * {@link RangedWeapon#stats}, which reads the config each time.
     */
    public WeaponProfile profile() {
        if (profile == null) {
            profile = new WeaponProfile(
                    weaponClass,
                    new WeaponStats(1, 0, 1, 0.0f, 1, baseSpread, (float) range(),
                            AgmWeapon.PROJECTILE_SPEED, AgmWeapon.PROJECTILE_LIFETIME_TICKS),
                    Optional.of(BuiltInRegistries.ITEM.getKey(ammo())),
                    Optional.of(BuiltInRegistries.ITEM.getKey(ModItems.SMALL_MAGAZINE.get())),
                    Optional.ofNullable(BuiltInRegistries.SOUND_EVENT.getKey(shotSound.get())),
                    Optional.ofNullable(BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.FAR_GUN_SHOT.get())));
        }
        return profile;
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
