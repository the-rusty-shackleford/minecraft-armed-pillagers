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

import com.f708.anothergunmod.registry.entity.ModEntities;
import com.f708.anothergunmod.registry.entity.bullet.BulletBuilder;
import com.f708.anothergunmod.registry.item.custom.AbstractGunItem;
import com.f708.anothergunmod.sounds.ModSounds;
import com.f708.anothergunmod.utils.GunUtils;
import com.nfx.armedpillagers.domain.Aim;
import com.nfx.armedpillagers.domain.CombatRules;
import com.nfx.armedpillagers.domain.FireControl;
import com.nfx.armedpillagers.domain.FireControl.Inputs;
import com.nfx.armedpillagers.domain.FireControl.Movement;
import com.nfx.armedpillagers.domain.FireControl.Outputs;
import com.nfx.armedpillagers.domain.Vec;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * <p>This class is the adapter: each tick it reads the mob into a
 * {@link FireControl.Inputs}, ticks the {@link FireControl} state machine, and
 * applies the {@link FireControl.Outputs} to the mob's navigation, look and
 * trigger. Every decision -- when to close, circle, fire or reload -- lives in
 * the machine, which is pure and tested; what lives here is the reading and
 * the doing, in the same order the inline code did them: move, look, then
 * trigger.
 *
 * <p>Every number comes out of the gun itself, through Another Gun Mod's
 * AbstractGunItem API. Fire rate, damage, pellet count, magazine size and
 * reload time are the item's own, so retuning the guns in that mod's config
 * retunes armed pillagers with them. Ammo is spent out of the held stack's
 * real ammo container, so a gun looted off a pillager arrives with however
 * many rounds it had left.
 *
 * <p>Reading the reload length and fire cooldown into the inputs costs up to
 * three data-component lookups per tick that the inline code only paid on a
 * reload or a shot. That is the price of a machine that takes plain values,
 * and it is noise beside the line-of-sight raycast this goal has always done
 * every tick.
 */
public class GunAttackGoal extends Goal {

    /**
     * Beyond the near radius a shot is heard as the gun mod's muffled distant
     * report instead of the close one. 64 matches its own default sound range.
     */
    private static final double NEAR_SOUND_RANGE = 16.0;
    private static final double FAR_SOUND_RANGE = 64.0;

    private final Mob mob;
    private final double speedModifier;
    private final FireControl control;

    // Abstraction function:
    //   AF(mob, speedModifier, control) = the ranged-attack behaviour of `mob`
    //   with whatever PillagerGun is in its main hand, moving at speedModifier
    //   when closing, in fire-control state `control`.
    // Rep invariant:
    //   control's dice are bound to mob.getRandom(), so the mob's random stream
    //   is consumed exactly where the inline code consumed it.
    // Safety from rep exposure:
    //   all fields are private and final; nothing is returned.

    public GunAttackGoal(Mob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.control = new FireControl(new FireControl.Dice() {
            @Override
            public float nextFloat() {
                return mob.getRandom().nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return mob.getRandom().nextInt(bound);
            }
        });
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
        control.reset();
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        control.reset();
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

        double range = gun.range();
        Inputs in = new Inputs(
                mob.getSensing().hasLineOfSight(target),
                mob.distanceToSqr(target),
                range * range,
                item.isAmmoEmpty(stack),
                CombatRules.reloadTicks(item.reloadTime(stack), item.maxAmmo(stack), ApConfig.RELOAD_MULTIPLIER.get()),
                CombatRules.fireCooldown(item.fireRate(stack)));
        Outputs out = control.tick(in);

        switch (out.movement()) {
            case Movement.Repath() -> mob.getNavigation().moveTo(target, speedModifier);
            case Movement.KeepCourse() -> {
                // The last path stands.
            }
            case Movement.Strafe(boolean backwards, boolean clockwise) -> {
                mob.getNavigation().stop();
                mob.getMoveControl().strafe(backwards ? -0.5F : 0.5F, clockwise ? 0.5F : -0.5F);
            }
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        switch (out.action()) {
            case RELOAD_FINISHED -> gun.refill(item, stack);
            case RELOAD_STARTED -> ArmedPillagers.LOGGER.debug("pillager {} reloading its {} for {} ticks",
                    mob.getUUID(), gun.id(), in.reloadDuration());
            case FIRE -> fire(target, gun, item, stack);
            case IDLE -> {
                // Cooling, reloading, or not in position.
            }
        }
    }

    private void fire(LivingEntity target, PillagerGun gun, AbstractGunItem item, ItemStack stack) {
        // Eye to centre mass, as an explicit vector: Aim says why the mob's own
        // look angle is not used.
        Aim aim = Aim.at(vec(mob.getEyePosition()), vec(target.position()), target.getBbHeight());
        Vec3 direction = vec3(aim.direction());
        Vec3 muzzle = vec3(aim.muzzle());

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

    /** The boundary between the game's vector and the domain's. */
    private static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    private static Vec3 vec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
