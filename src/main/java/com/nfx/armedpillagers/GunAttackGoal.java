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

import com.nfx.armedpillagers.domain.Aim;
import com.nfx.armedpillagers.domain.CombatRules;
import com.nfx.armedpillagers.domain.FireControl;
import com.nfx.armedpillagers.domain.FireControl.Inputs;
import com.nfx.armedpillagers.domain.FireControl.Movement;
import com.nfx.armedpillagers.domain.FireControl.Outputs;
import com.nfx.armedpillagers.domain.Vec;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.armedpillagers.weapon.PillagerWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.WeaponProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
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
 * Ranged combat for a mob holding a {@link RangedWeapon}.
 *
 * <p>This class is the adapter: each tick it reads the mob into a
 * {@link FireControl.Inputs}, ticks the {@link FireControl} state machine, and
 * applies the {@link FireControl.Outputs} to the mob's navigation, look and
 * trigger. Every decision -- when to close, circle, fire or reload -- lives in
 * the machine, which is pure and tested; what lives here is the reading and
 * the doing, in the order the inline code did them: move, look, then trigger.
 *
 * <p>It knows no gun mod. Every number comes from the weapon's own
 * {@link WeaponStats} for the held stack, and every effect on the weapon --
 * spending a round, refilling, launching projectiles -- goes through the
 * {@link RangedWeapon} contract. Ammo is spent from the held stack, so a gun
 * looted off a pillager arrives with however many rounds it had left.
 *
 * <p>Cost, stated: one {@code stats(stack)} read per tick, shared by the
 * machine and the shot, is a handful of data-component lookups per armed mob
 * per tick -- beside a line-of-sight raycast this goal has always done.
 */
public class GunAttackGoal extends Goal {

    /**
     * Beyond the near radius a shot is heard as the muffled distant report
     * instead of the close one. 64 matches the gun mod's default sound range.
     */
    private static final double NEAR_SOUND_RANGE = 16.0;
    private static final double FAR_SOUND_RANGE = 64.0;

    private final Mob mob;
    private final double speedModifier;
    private final FireControl control;

    // Abstraction function:
    //   AF(mob, speedModifier, control) = the ranged-attack behaviour of `mob`
    //   with whatever RangedWeapon resolves from its main hand, moving at
    //   speedModifier when closing, in fire-control state `control`.
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
        return hasLivingTarget() && heldWeapon() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return hasLivingTarget() && heldWeapon() != null;
    }

    private boolean hasLivingTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    private RangedWeapon heldWeapon() {
        return PillagerWeapons.resolve(mob.getMainHandItem());
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
        ItemStack stack = mob.getMainHandItem();
        RangedWeapon weapon = PillagerWeapons.resolve(stack);
        if (target == null || weapon == null) {
            return;
        }

        WeaponStats stats = weapon.stats(stack);
        double range = stats.engagementRange();
        Inputs in = new Inputs(
                mob.getSensing().hasLineOfSight(target),
                mob.distanceToSqr(target),
                range * range,
                weapon.isEmpty(stack),
                CombatRules.reloadTicks(stats.reloadTicksPerRound(), stats.capacity(), ApConfig.RELOAD_MULTIPLIER.get()),
                CombatRules.fireCooldown(stats.fireRateTicks()));
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
            case RELOAD_FINISHED -> weapon.load(stack, weapon.capacity(stack));
            case RELOAD_STARTED -> ArmedPillagers.LOGGER.debug("pillager {} reloading its {} for {} ticks",
                    mob.getUUID(), ArmedPillagers.shortName(stack.getItem()), in.reloadDuration());
            case FIRE -> fire(target, weapon, stats, stack);
            case IDLE -> {
                // Cooling, reloading, or not in position.
            }
        }
    }

    private void fire(LivingEntity target, RangedWeapon weapon, WeaponStats stats, ItemStack stack) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        // Eye to centre mass, as an explicit vector: Aim says why the mob's own
        // look angle is not used.
        Aim aim = Aim.at(vec(mob.getEyePosition()), vec(target.position()), target.getBbHeight());
        if (aim.direction().equals(Vec.ZERO)) {
            // Target inside the shooter's own head: nowhere to aim. The old
            // code launched a bullet with no direction; refusing is the one
            // deliberate deviation.
            return;
        }

        // The consumer's multipliers, applied here so the weapon never sees
        // config: damage through CombatRules for its floor and rounding,
        // spread as a plain scale.
        int damage = CombatRules.damage(stats.damage(), ApConfig.DAMAGE_MULTIPLIER.get().floatValue());
        WeaponStats forShot = stats.scaled(1.0f, ApConfig.SPREAD_MULTIPLIER.get().floatValue()).withDamage(damage);
        Shot shot = Shot.of(forShot, vec3(aim.muzzle()), vec3(aim.direction()));

        weapon.fire(level, mob, stack, shot);
        weapon.consumeRound(stack);
        stack.hurtAndBreak(1, mob, EquipmentSlot.MAINHAND);
        playShot(level, weapon.profile(), shot.origin());
        ArmedPillagers.LOGGER.debug("pillager {} fires {} ({} x{} dmg), {} rounds left",
                mob.getUUID(), ArmedPillagers.shortName(stack.getItem()), shot.count(), damage, weapon.rounds(stack));
    }

    /**
     * The close report goes out through the level so everything nearby hears it;
     * players further out get the profile's muffled distant report instead -
     * the same two-layer treatment the gun mod gives player gunfire.
     */
    private void playShot(ServerLevel level, WeaponProfile profile, Vec3 muzzle) {
        float pitch = 0.95F + mob.getRandom().nextFloat() * 0.1F;
        SoundEvent near = sound(profile.shotSound());
        if (near != null) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), near, SoundSource.HOSTILE, 1.0F, pitch);
        }
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.01);

        SoundEvent farEvent = sound(profile.farShotSound());
        if (farEvent == null) {
            return;
        }
        Holder<SoundEvent> far = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(farEvent);
        for (ServerPlayer player : level.players()) {
            double distSqr = player.distanceToSqr(mob);
            if (CombatRules.hearsFarReport(distSqr, NEAR_SOUND_RANGE, FAR_SOUND_RANGE)) {
                player.connection.send(new ClientboundSoundPacket(far, SoundSource.HOSTILE,
                        mob.getX(), mob.getY(), mob.getZ(), 1.0F, 1.0F, mob.getRandom().nextLong()));
            }
        }
    }

    private static SoundEvent sound(java.util.Optional<ResourceLocation> id) {
        return id.map(BuiltInRegistries.SOUND_EVENT::get).orElse(null);
    }

    /** The boundary between the game's vector and the domain's. */
    private static Vec vec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    private static Vec3 vec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
