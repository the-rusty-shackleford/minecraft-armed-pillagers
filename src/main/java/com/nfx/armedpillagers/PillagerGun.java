/*
 * Armed Pillagers - pillagers that carry Another Gun Mod firearms.
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

import com.f708.anothergunmod.core.AmmoContainer;
import com.f708.anothergunmod.core.AmmoContainerRecord;
import com.f708.anothergunmod.registry.item.ModItems;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.f708.anothergunmod.sounds.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/**
 * The only three guns a pillager is ever allowed to hold.
 *
 * The auto-gun, machine gun and flamethrower are absent on purpose: sustained
 * automatic fire from a mob that never has to reload mid-burst is not a fight,
 * and the flamethrower sets the world alight. Each entry here is a weapon a
 * player can reasonably duck behind cover from.
 *
 * Base spreads are looser than {@code GunUtils#getSpread} gives a standing
 * player, so an armed pillager is a worse shot than you are at the same range.
 */
public enum PillagerGun {
    REVOLVER("revolver",
            ModItems.REVOLVER, ModItems.SMALLBULLET, ModSounds.REVOLVER_SHOOT,
            0.045F, ApConfig.REVOLVER_RANGE),

    RIFLE("rifle",
            ModItems.RIFLE, ModItems.BIGBULLET, ModSounds.RIFLE_SHOT,
            0.030F, ApConfig.RIFLE_RANGE),

    SHOTGUN("shotgun",
            ModItems.SHOTGUN, ModItems.SHELL, ModSounds.SHOTGUN_SHOT,
            0.140F, ApConfig.SHOTGUN_RANGE);

    private final String id;
    private final Supplier<Item> gunItem;
    private final Supplier<Item> ammoItem;
    private final Supplier<SoundEvent> shotSound;
    private final float baseSpread;
    private final ModConfigSpec.DoubleValue range;

    PillagerGun(String id, Supplier<Item> gunItem, Supplier<Item> ammoItem,
                Supplier<SoundEvent> shotSound, float baseSpread, ModConfigSpec.DoubleValue range) {
        this.id = id;
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

    public SoundEvent shotSound() {
        return shotSound.get();
    }

    public float spread() {
        return baseSpread * ApConfig.SPREAD_MULTIPLIER.get().floatValue();
    }

    public double range() {
        return range.get();
    }

    /** A gun loaded to its own maximum: six rounds for a revolver, one for a rifle, five shells. */
    public ItemStack loadedStack() {
        ItemStack stack = new ItemStack(gun());
        if (stack.getItem() instanceof AbstractGunItem item) {
            refill(item, stack);
        }
        return stack;
    }

    /**
     * Fills the gun's ammo container back to capacity.
     *
     * Built a round at a time rather than through {@code addNewBullet(stack, n)}:
     * that overload reuses one ItemStack instance for every round and only
     * capacity-checks once, so it can overfill. The single-round call is the
     * safe one.
     */
    public void refill(AbstractGunItem item, ItemStack stack) {
        int capacity = item.maxAmmo(stack);
        AmmoContainerRecord record = new AmmoContainerRecord(new AmmoContainer(capacity));
        ItemStack round = new ItemStack(ammo());
        for (int i = 0; i < capacity; i++) {
            record = record.addNewBullet(round);
        }
        AmmoContainerRecord.setContainerToComponent(stack, record);
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
     * One roll across all three weapons. Rarest first so the slices stay
     * exclusive and each config number means what it says.
     */
    public static PillagerGun roll(RandomSource random) {
        float roll = random.nextFloat();
        float shotgun = ApConfig.SHOTGUN_CHANCE.get().floatValue();
        float rifle = ApConfig.RIFLE_CHANCE.get().floatValue();
        float revolver = ApConfig.REVOLVER_CHANCE.get().floatValue();

        if (roll < shotgun) {
            return SHOTGUN;
        }
        if (roll < shotgun + rifle) {
            return RIFLE;
        }
        if (roll < shotgun + rifle + revolver) {
            return REVOLVER;
        }
        return null;
    }
}
