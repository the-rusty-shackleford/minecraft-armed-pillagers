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
package com.nfx.armedpillagers.domain;

/**
 * An immutable 3-vector with exactly the four operations fire control needs.
 *
 * <p>This exists so that aiming can be a pure function tested without the game:
 * the adapter converts to and from {@code net.minecraft.world.phys.Vec3} at the
 * boundary. {@link #normalize()} copies that class's rule -- a vector shorter
 * than {@code 1e-4} normalises to {@link #ZERO} rather than to NaN -- so the
 * arithmetic here is the arithmetic the game does.
 *
 * <p>RI: every component is finite. Enforced in the constructor, so an
 * operation that would produce NaN or infinity fails there rather than
 * propagating a poisoned vector into a shot.
 *
 * @param x the x component
 * @param y the y component
 * @param z the z component
 */
public record Vec(double x, double y, double z) {

    /** The origin. Also what {@link #normalize()} returns for a vector too short to have a direction. */
    public static final Vec ZERO = new Vec(0, 0, 0);

    /**
     * Below this length a vector has no usable direction; the same threshold
     * {@code Vec3.normalize} uses.
     */
    public static final double NORMALIZE_EPSILON = 1.0E-4;

    /**
     * @throws IllegalArgumentException if any component is NaN or infinite
     */
    public Vec {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("components must be finite, were " + x + ", " + y + ", " + z);
        }
    }

    /**
     * effects: returns {@code this + other}, componentwise
     *
     * @param other the vector to add
     * @return the sum
     */
    public Vec add(Vec other) {
        return new Vec(x + other.x, y + other.y, z + other.z);
    }

    /**
     * effects: returns {@code this - other}, componentwise
     *
     * @param other the vector to subtract
     * @return the difference
     */
    public Vec subtract(Vec other) {
        return new Vec(x - other.x, y - other.y, z - other.z);
    }

    /**
     * requires: {@code factor} is finite<br>
     * effects: returns {@code this} scaled by {@code factor}<br>
     * throws: {@link IllegalArgumentException} if the result is not finite
     *
     * @param factor the scale
     * @return the scaled vector
     */
    public Vec scale(double factor) {
        return new Vec(x * factor, y * factor, z * factor);
    }

    /**
     * effects: returns the Euclidean length
     *
     * @return {@code sqrt(x² + y² + z²)}
     */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * effects: returns {@code this / length()} if {@code length() >= }
     * {@link #NORMALIZE_EPSILON}, else {@link #ZERO}
     *
     * @return a unit vector in this direction, or zero if there is no direction
     */
    public Vec normalize() {
        double length = length();
        if (length < NORMALIZE_EPSILON) {
            return ZERO;
        }
        return new Vec(x / length, y / length, z / length);
    }
}
