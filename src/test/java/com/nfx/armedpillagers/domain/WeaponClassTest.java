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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WeaponClass}.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * get(name):
 *   interning:  the same name twice is the same instance (==) / different names are different
 *   well-known: each constant is get() of its own name
 *   valid:      letters only / digits / underscores / a single character
 *   invalid:    empty / uppercase / a space / a hyphen / a colon / null -> IAE
 *   open set:   a name no constant declares is minted, not rejected
 * isValidName: mirrors the valid/invalid partitions without throwing
 * name / toString: the name given
 * </pre>
 */
final class WeaponClassTest {

    @Test
    void theSameNameIsTheSameInstance() {
        assertSame(WeaponClass.get("plasma"), WeaponClass.get("plasma"));
    }

    @Test
    void differentNamesAreDifferentInstances() {
        assertNotSame(WeaponClass.get("alpha_one"), WeaponClass.get("alpha_two"));
    }

    @Test
    void theWellKnownConstantsAreTheirOwnNames() {
        assertSame(WeaponClass.SIDEARM, WeaponClass.get("sidearm"));
        assertSame(WeaponClass.RIFLE, WeaponClass.get("rifle"));
        assertSame(WeaponClass.SHOTGUN, WeaponClass.get("shotgun"));
        assertSame(WeaponClass.AUTOMATIC, WeaponClass.get("automatic"));
        assertSame(WeaponClass.LAUNCHER, WeaponClass.get("launcher"));
        assertSame(WeaponClass.FLAME, WeaponClass.get("flame"));
        assertSame(WeaponClass.UNCLASSIFIED, WeaponClass.get("unclassified"));
    }

    @Test
    void lettersDigitsAndUnderscoresAreValid() {
        assertEquals("mk_2_carbine", WeaponClass.get("mk_2_carbine").name());
        assertEquals("x", WeaponClass.get("x").name());
        assertEquals("42", WeaponClass.get("42").name());
    }

    @Test
    void anUnknownNameIsMintedNotRejected() {
        WeaponClass minted = WeaponClass.get("gauss_cannon");
        assertEquals("gauss_cannon", minted.name());
        assertSame(minted, WeaponClass.get("gauss_cannon"));
    }

    @Test
    void emptyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get(""));
    }

    @Test
    void uppercaseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get("Rifle"));
    }

    @Test
    void spacesAndPunctuationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get("side arm"));
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get("side-arm"));
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get("mod:rifle"));
    }

    @Test
    void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeaponClass.get(null));
    }

    @Test
    void isValidNameMirrorsGetWithoutThrowing() {
        assertTrue(WeaponClass.isValidName("sidearm"));
        assertTrue(WeaponClass.isValidName("mk_2"));
        assertFalse(WeaponClass.isValidName(""));
        assertFalse(WeaponClass.isValidName("Rifle"));
        assertFalse(WeaponClass.isValidName("a b"));
        assertFalse(WeaponClass.isValidName(null));
    }

    @Test
    void toStringIsTheName() {
        assertEquals("shotgun", WeaponClass.SHOTGUN.toString());
    }
}
