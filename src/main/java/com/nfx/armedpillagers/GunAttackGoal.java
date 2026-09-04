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

import com.f708.anothergunmod.registry.entity.ModEntities;
import com.f708.anothergunmod.registry.entity.bullet.BulletBuilder;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.f708.anothergunmod.sounds.ModSounds;
import com.f708.anothergunmod.utils.GunUtils;
import com.nfx.armedpillagers.domain.CombatRules;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ranged combat for a mob holding one of {@link PillagerGun}'s weapons.
 *
 * Shaped after vanilla's RangedCrossbowAttackGoal - close until the weapon's
 * range and line of sight are both good, then hold position and fire - but
 * every number comes out of the gun itself, through Another Gun Mod's
 * AbstractGunItem API. Fire rate, damage, pellet count, magazine size and
 * reload time are the item's own, so retuning the guns in that mod's config
 * retunes armed pillagers with them.
 *
 * Ammo is spent out of the held stack's real ammo container, so a gun looted
 * off a pillager arrives with however many rounds it had left.
 */
public class GunAttackGoal extends Goal {
    private static final UniformInt PATHFINDING_DELAY = TimeUtil.rangeOfSeconds(1, 2);

    /**
     * Beyond the near radius a shot is heard as the gun mod's muffled distant
     * report instead of the close one. 64 matches its own default sound range.
     */
    private static final double NEAR_SOUND_RANGE = 16.0;
    private static final double FAR_SOUND_RANGE = 64.0;

    private final Mob mob;
    private final double speedModifier;

    private int seeTime;
    private int cooldown;
    private int reloadTicks;
    private int updatePathDelay;
    private int strafeTicks = -1;
    private boolean strafeClockwise;
    private boolean strafeBackwards;

    public GunAttackGoal(Mob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return hasLivingTarget() && heldGun() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return hasLivingTarget() && heldGun() != null;
    }

    private boolean hasLivingTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    private PillagerGun heldGun() {
        return PillagerGun.of(mob.getMainHandItem());
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        seeTime = 0;
        cooldown = 0;
        reloadTicks = 0;
        strafeTicks = -1;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        seeTime = 0;
        reloadTicks = 0;
        strafeTicks = -1;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        PillagerGun gun = heldGun();
        if (target == null || gun == null) {
            return;
        }
        ItemStack stack = mob.getMainHandItem();
        if (!(stack.getItem() instanceof AbstractGunItem item)) {
            return;
        }

        boolean canSee = mob.getSensing().hasLineOfSight(target);
        if (canSee != seeTime > 0) {
            seeTime = 0;
        }
        seeTime += canSee ? 1 : -1;

        double range = gun.range();
        double distSqr = mob.distanceToSqr(target);
        boolean inPosition = distSqr <= range * range && seeTime >= 5;

        move(target, inPosition, distSqr, range * range);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (reloadTicks > 0) {
            if (--reloadTicks == 0) {
                gun.refill(item, stack);
            }
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (item.isAmmoEmpty(stack)) {
            // A full magazine's worth of the gun's reload time, not the per-round
            // figure a player pays; CombatRules.reloadTicks says why.
            reloadTicks = CombatRules.reloadTicks(
                    item.reloadTime(stack), item.maxAmmo(stack), ApConfig.RELOAD_MULTIPLIER.get());
            ArmedPillagers.LOGGER.debug("pillager {} reloading its {} for {} ticks",
                    mob.getUUID(), gun.id(), reloadTicks);
            return;
        }
        if (inPosition && canSee) {
            fire(target, gun, item, stack);
            cooldown = CombatRules.fireCooldown(item.fireRate(stack));
        }
    }

    /**
     * Close the distance while out of position; circle once in it, the way
     * vanilla's bow goal does - sideways, plus a forward or backward push that
     * holds the mob inside the middle of its weapon's range band rather than
     * letting it drift out to the edge.
     */
    private void move(LivingEntity target, boolean inPosition, double distSqr, double rangeSqr) {
        if (!inPosition) {
            strafeTicks = -1;
            if (--updatePathDelay <= 0) {
                mob.getNavigation().moveTo(target, speedModifier);
                updatePathDelay = PATHFINDING_DELAY.sample(mob.getRandom());
            }
            return;
        }

        updatePathDelay = 0;
        mob.getNavigation().stop();
        if (strafeTicks < 0) {
            strafeTicks = 0;
        }
        if (++strafeTicks >= 20) {
            if (mob.getRandom().nextFloat() < 0.3F) {
                strafeClockwise = !strafeClockwise;
            }
            strafeTicks = 0;
        }
        if (distSqr > rangeSqr * 0.75D) {
            strafeBackwards = false;
        } else if (distSqr < rangeSqr * 0.25D) {
            strafeBackwards = true;
        }
        mob.getMoveControl().strafe(strafeBackwards ? -0.5F : 0.5F, strafeClockwise ? 0.5F : -0.5F);
    }

    private void fire(LivingEntity target, PillagerGun gun, AbstractGunItem item, ItemStack stack) {
        // Aim from the eyes at centre mass rather than letting BulletEntity read
        // getLookAngle(): on a mob that is body yaw, which lags the head badly
        // while strafing and would throw every shot wide.
        Vec3 eye = mob.getEyePosition();
        Vec3 aimPoint = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 direction = aimPoint.subtract(eye).normalize();
        Vec3 muzzle = eye.add(direction.scale(0.6));

        float spread = gun.spread();
        int damage = CombatRules.damage(item.rangedDamage(stack), ApConfig.DAMAGE_MULTIPLIER.get().floatValue());
        ItemStack round = item.getAmmoContainer(stack).getBullet(0);

        for (int i = 0; i < item.bulletAmountPerShot(stack); i++) {
            new BulletBuilder(ModEntities.BULLET.get(), mob.level(), mob, stack, GunUtils.getBulletType(round))
                    .position(muzzle)
                    .direction(direction)
                    .speed(4.0F)
                    .lifetime(100)
                    .spread(spread, spread, spread)
                    .damage(damage)
                    .spawn();
        }

        item.descreaseAmmo(stack);
        stack.hurtAndBreak(1, mob, EquipmentSlot.MAINHAND);
        playShot(gun, muzzle);
        ArmedPillagers.LOGGER.debug("pillager {} fires {} ({} x{} dmg), {} rounds left",
                mob.getUUID(), gun.id(), item.bulletAmountPerShot(stack), damage, item.getAmmo(stack));
    }

    /**
     * The close report goes out through the level so everything nearby hears it;
     * players further out get the gun mod's muffled distant crack instead - the
     * same two-layer treatment it gives player gunfire.
     */
    private void playShot(PillagerGun gun, Vec3 muzzle) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        float pitch = 0.95F + mob.getRandom().nextFloat() * 0.1F;
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                gun.shotSound(), SoundSource.HOSTILE, 1.0F, pitch);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.01);

        Holder<SoundEvent> far = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.FAR_GUN_SHOT.get());
        for (ServerPlayer player : level.players()) {
            double distSqr = player.distanceToSqr(mob);
            if (CombatRules.hearsFarReport(distSqr, NEAR_SOUND_RANGE, FAR_SOUND_RANGE)) {
                player.connection.send(new ClientboundSoundPacket(far, SoundSource.HOSTILE,
                        mob.getX(), mob.getY(), mob.getZ(), 1.0F, 1.0F, mob.getRandom().nextLong()));
            }
        }
    }
}
