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
package com.nfx.armedpillagers.weapon;

import com.f708.anothergunmod.core.AmmoContainer;
import com.f708.anothergunmod.core.AmmoContainerRecord;
import com.f708.anothergunmod.registry.entity.ModEntities;
import com.f708.anothergunmod.registry.entity.bullet.BulletBuilder;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.f708.anothergunmod.utils.GunUtils;
import com.nfx.armedpillagers.PillagerGun;
import com.nfx.armedpillagers.domain.WeaponStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * {@link RangedWeapon} for one of Another Gun Mod's guns.
 *
 * <p>The gun mod's own behaviour API -- {@code shoot}, {@code reload},
 * {@code canShoot} -- takes a {@code Player}, so a mob cannot go through it.
 * This class consumes the mod's <em>query</em> API, which takes an
 * {@code ItemStack}, and reimplements the behaviour half against the pieces
 * that are entity-agnostic: {@code BulletBuilder} accepts any
 * {@code LivingEntity} as shooter, and the ammo container is a public data
 * component. Everything goes through public classes; nothing is mixed into,
 * so a gun mod update can only break this at compile time.
 *
 * <p>Stateless: one instance serves every stack of its gun. All state is on
 * the stack.
 */
public final class AgmWeapon implements RangedWeapon {

    /**
     * Muzzle velocity and lifetime the gun mod itself uses in its player
     * firing path; it does not expose them, so they are pinned here. Public
     * because the catalog's profile defaults carry them too.
     */
    public static final float PROJECTILE_SPEED = 4.0f;
    public static final int PROJECTILE_LIFETIME_TICKS = 100;

    private final PillagerGun gun;

    /**
     * @param gun the catalog entry this weapon serves
     */
    public AgmWeapon(PillagerGun gun) {
        this.gun = gun;
    }

    @Override
    public WeaponProfile profile() {
        return gun.profile();
    }

    /**
     * effects: returns the stack's own numbers from the gun mod, clamped into
     * the protocol's ranges (the mod's config accepts values the protocol
     * rejects, and a fire rate of zero must not make a tick throw), with
     * spread and range from the catalog and the pinned projectile speed and
     * lifetime
     */
    @Override
    public WeaponStats stats(ItemStack stack) {
        AbstractGunItem item = gunItem(stack);
        return new WeaponStats(
                Math.max(1, item.maxAmmo(stack)),
                Math.max(0, item.reloadTime(stack)),
                Math.max(1, item.fireRate(stack)),
                Math.max(0, item.rangedDamage(stack)),
                Math.max(1, item.bulletAmountPerShot(stack)),
                gun.baseSpread(),
                (float) gun.range(),
                PROJECTILE_SPEED,
                PROJECTILE_LIFETIME_TICKS);
    }

    @Override
    public int capacity(ItemStack stack) {
        return Math.max(1, gunItem(stack).maxAmmo(stack));
    }

    @Override
    public int rounds(ItemStack stack) {
        return gunItem(stack).getAmmo(stack);
    }

    /** The gun mod's own notion, which accounts for its unlimited-ammo mode. */
    @Override
    public boolean isEmpty(ItemStack stack) {
        return gunItem(stack).isAmmoEmpty(stack);
    }

    /**
     * Rebuilds the magazine with {@code count} rounds of the gun's ammunition.
     *
     * <p>Built a round at a time rather than through
     * {@code addNewBullet(stack, n)}: that overload reuses one ItemStack
     * instance for every round and only capacity-checks once, so it can
     * overfill. The single-round call is the safe one. The record is
     * copy-on-write, so the write-back at the end is what makes it real.
     */
    @Override
    public void load(ItemStack stack, int count) {
        AbstractGunItem item = gunItem(stack);
        int capacity = capacity(stack);
        if (count < 0 || count > capacity) {
            throw new IllegalArgumentException("count must be in [0, " + capacity + "], was " + count);
        }
        AmmoContainerRecord record = new AmmoContainerRecord(new AmmoContainer(item.maxAmmo(stack)));
        ItemStack round = new ItemStack(gun.ammo());
        for (int i = 0; i < count; i++) {
            record = record.addNewBullet(round);
        }
        AmmoContainerRecord.setContainerToComponent(stack, record);
    }

    @Override
    public void consumeRound(ItemStack stack) {
        AbstractGunItem item = gunItem(stack);
        if (item.isAmmoEmpty(stack)) {
            throw new IllegalStateException("cannot consume a round from an empty " + gun.id());
        }
        item.descreaseAmmo(stack);
    }

    /**
     * Spawns the gun mod's own bullets, carrying whatever effect the next
     * round up the spout has (fire, ice, ender pearl, ...). Ammo is not
     * consumed here; the caller does that, per the contract.
     */
    @Override
    public void fire(ServerLevel level, LivingEntity shooter, ItemStack stack, Shot shot) {
        AbstractGunItem item = gunItem(stack);
        // getBullet(0) on an empty container silently returns a default round;
        // the contract has the caller fire only when not empty.
        ItemStack round = item.getAmmoContainer(stack).getBullet(0);
        var bulletTypes = GunUtils.getBulletType(round);
        int damage = Math.round(shot.damage());
        float spread = shot.spread();

        for (int i = 0; i < shot.count(); i++) {
            new BulletBuilder(ModEntities.BULLET.get(), level, shooter, stack, bulletTypes)
                    .position(shot.origin())
                    .direction(shot.direction())
                    .speed(shot.speed())
                    .lifetime(shot.lifetimeTicks())
                    .spread(spread, spread, spread)
                    .damage(damage)
                    .spawn();
        }
    }

    private AbstractGunItem gunItem(ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractGunItem item) || !stack.is(gun.gun())) {
            throw new IllegalArgumentException("not a " + gun.id() + ": " + stack);
        }
        return item;
    }
}
