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

/**
 * Where a shot starts and which way it goes.
 *
 * <p>Aim is computed from the shooter's eye to the target's centre mass and
 * handed to the projectile as an explicit vector. It is never read off the
 * shooter's look angle: on a mob that is <em>body</em> yaw, which lags the head
 * badly while strafing and would throw every shot wide. The muzzle sits a
 * short way along the aim line from the eye so the projectile spawns in front
 * of the shooter's face rather than inside its hitbox.
 *
 * <p>Immutable. RI: {@code direction} is either a unit vector or
 * {@link Vec#ZERO} (the degenerate case where eye and aim point coincide).
 *
 * @param muzzle    where the projectile spawns
 * @param direction which way it travels; unit length, or zero if there is no direction
 */
public record Aim(Vec muzzle, Vec direction) {

    /** The fraction of the target's height at which the aim point sits: centre mass, not feet. */
    public static final double AIM_HEIGHT_FRACTION = 0.6;

    /** How far along the aim line, from the eye, the projectile spawns. */
    public static final double MUZZLE_OFFSET = 0.6;

    /**
     * Aims from an eye at a target.
     *
     * <p>requires: {@code targetHeight >= 0} and finite<br>
     * effects: returns the aim whose {@code direction} is
     * {@code normalize(targetFeet + (0, AIM_HEIGHT_FRACTION * targetHeight, 0) - eye)}
     * and whose {@code muzzle} is {@code eye + MUZZLE_OFFSET * direction}; when
     * eye and aim point coincide the direction is {@link Vec#ZERO} and the
     * muzzle is the eye<br>
     * throws: {@link IllegalArgumentException} if {@code targetHeight} is
     * negative, NaN or infinite
     *
     * @param eye          the shooter's eye position
     * @param targetFeet   the target's position, which Minecraft reports at its feet
     * @param targetHeight the target's bounding-box height
     * @return the aim
     */
    public static Aim at(Vec eye, Vec targetFeet, double targetHeight) {
        if (!(targetHeight >= 0) || Double.isInfinite(targetHeight)) {
            throw new IllegalArgumentException("targetHeight must be finite and >= 0, was " + targetHeight);
        }
        Vec aimPoint = targetFeet.add(new Vec(0, targetHeight * AIM_HEIGHT_FRACTION, 0));
        Vec direction = aimPoint.subtract(eye).normalize();
        Vec muzzle = eye.add(direction.scale(MUZZLE_OFFSET));
        return new Aim(muzzle, direction);
    }
}
