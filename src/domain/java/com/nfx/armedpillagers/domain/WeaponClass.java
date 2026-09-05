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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A coarse category of ranged weapon: sidearm, rifle, shotgun, automatic.
 *
 * <p>Consumers use it for <em>policy</em> -- "never issue automatics to a mob"
 * -- and the protocol itself never reads it. It is an open set: any mod may
 * mint a class by name, instances are interned so identity comparison is
 * valid, and a consumer that meets a name it does not know treats it as
 * opaque rather than as an error. The same shape as NeoForge's own
 * {@code ItemAbility}, for the same reason: a Java enum cannot be extended
 * and a registry would freeze the set at load.
 *
 * <p>Immutable. RI: {@code name} matches {@code [a-z0-9_]+}, and at most one
 * instance exists per name (interned through {@code BY_NAME}).
 */
public final class WeaponClass {
    private static final Map<String, WeaponClass> BY_NAME = new ConcurrentHashMap<>();

    /** A pistol, revolver: quick, short. */
    public static final WeaponClass SIDEARM = get("sidearm");
    /** A rifle: single shots, long reach. */
    public static final WeaponClass RIFLE = get("rifle");
    /** A shotgun: several projectiles per pull, close. */
    public static final WeaponClass SHOTGUN = get("shotgun");
    /** Anything that fires sustained bursts without reloading between them. */
    public static final WeaponClass AUTOMATIC = get("automatic");
    /** Grenade and rocket launchers. */
    public static final WeaponClass LAUNCHER = get("launcher");
    /** Flamethrowers and other stream weapons; not projectiles at all. */
    public static final WeaponClass FLAME = get("flame");
    /** What a profile gets when it names no class. */
    public static final WeaponClass UNCLASSIFIED = get("unclassified");

    private final String name;

    private WeaponClass(String name) {
        this.name = name;
    }

    /**
     * The unique class with this name, minted on first use.
     *
     * <p>requires: {@code name} is non-empty and every character is a lowercase
     * ASCII letter, a digit or an underscore<br>
     * effects: returns the one instance for {@code name}, creating it if this
     * is the first request<br>
     * throws: {@link IllegalArgumentException} if the name is malformed
     *
     * @param name the class name
     * @return its unique instance
     */
    public static WeaponClass get(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                    "weapon class names are [a-z0-9_]+, was \"" + name + "\"");
        }
        return BY_NAME.computeIfAbsent(name, WeaponClass::new);
    }

    /**
     * effects: returns whether {@code name} is a legal class name -- non-empty,
     * lowercase ASCII letters, digits and underscores only
     *
     * @param name the candidate
     * @return whether {@link #get} would accept it
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * effects: returns the name this class was minted under
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // equals and hashCode are identity: instances are interned, so two
    // references to the same name are the same object.
}
