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

/**
 * The decisions this mod makes, as pure functions over plain values.
 *
 * <p>Nothing in this package may reference Minecraft, NeoForge, or any other
 * mod. The rule is enforced by the build, not by review: this source set is
 * compiled against the JDK alone, so an offending import fails to compile.
 * Every class here is therefore testable with plain JUnit -- no game boot, no
 * mocks -- which is the point of the layer.
 *
 * <p>What lives here: which weapon a spawn roll selects, how long a reload
 * takes, how much a dropped gun is worn, where a shot is aimed from, and the
 * fire-control state machine that decides each tick whether to close, circle,
 * fire, or reload. What does not: anything that reads or writes an entity, an
 * item stack, a level, or a config value. The classes in {@code
 * com.nfx.armedpillagers} translate between the two.
 */
package com.nfx.armedpillagers.domain;
