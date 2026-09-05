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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.nfx.armedpillagers.domain.FireControl.Action;
import com.nfx.armedpillagers.domain.FireControl.Dice;
import com.nfx.armedpillagers.domain.FireControl.Inputs;
import com.nfx.armedpillagers.domain.FireControl.Movement;
import com.nfx.armedpillagers.domain.FireControl.Outputs;

/**
 * Tests for {@link FireControl}.
 *
 * <p>The machine's rep is private, so every partition is observed through its
 * outputs: line of sight is observable as when the mob starts circling (it
 * needs five consecutive visible ticks), the trigger as FIRE / RELOAD_* /
 * IDLE, and the path timer as Repath vs KeepCourse.
 *
 * <p>The scripted dice throw on any draw that was not queued, so every test
 * that uses them also asserts that the machine draws exactly when the contract
 * says and never otherwise. A fresh machine draws once on its first
 * out-of-position tick (the path delay), which is why {@link #settled} needs
 * one queued int.
 *
 * <p>Testing strategy -- input-space partitions:
 *
 * <pre>
 * visibility streak (via in-position):
 *   visible for 4 ticks (not yet) / 5 (in position) / 6 (still)
 *   visible -> hidden (streak resets; out of position at once)
 *   hidden -> visible (streak restarts from one; five more ticks needed)
 *   after reset() the streak restarts
 * position:
 *   in range with streak < 5 / in range with streak >= 5 / out of range with streak >= 5
 *   distSqr exactly equal to rangeSqr (in position)
 * out-of-position movement:
 *   fresh machine -> Repath on the first tick, delay := 20 + nextInt(21)
 *   delay unexpired -> KeepCourse / delay expires -> Repath
 *   leaving position (delay was zeroed) -> Repath at once
 * in-position movement:
 *   first tick strafes / ticks 2..19 draw no dice / 20th tick draws, flips or keeps, counter restarts
 * hysteresis on squared distance:
 *   > 0.75 rangeSqr -> forward from either prior state
 *   < 0.25 rangeSqr -> backward from either prior state
 *   dead band [0.25, 0.75]: prior forward stays forward, prior backward stays backward
 *   exactly 0.75 and exactly 0.25 are inside the dead band
 * trigger:
 *   ready + in position -> FIRE, then fireCooldown ticks of IDLE, then FIRE again
 *   ready + out of position -> IDLE
 *   ready + ammo empty -> RELOAD_STARTED, and it beats position
 *   cooling + ammo empty -> the cooldown finishes first
 *   reloading -> IDLE for reloadDuration - 1 ticks, then RELOAD_FINISHED
 *   reloading still moves (a Strafe or KeepCourse is emitted)
 * reset():
 *   mid-reload clears the reload / mid-cooldown clears the cooldown
 *   strafe directions survive
 * inputs:
 *   negative distSqr / negative rangeSqr / reloadDuration 0 / fireCooldown 0 -> IAE
 *   null dice -> IAE
 * invariants:
 *   a 1000-tick seeded random walk never trips checkRep (-ea is on), never
 *   fires inside a cooldown, and never fires between a reload's start and end
 * dice consumption:
 *   strafe ticks 2..19 draw nothing; out-of-position ticks with an unexpired
 *   delay draw nothing
 * </pre>
 */
final class FireControlTest {

    /** Defaults chosen so tests can name only what they vary. */
    private static final double RANGE_SQR = 100.0;
    private static final double IN_RANGE = 50.0;
    private static final double OUT_OF_RANGE = 400.0;
    private static final int RELOAD = 40;
    private static final int COOLDOWN = 3;

    private static Inputs in(boolean canSee, double distSqr, boolean ammoEmpty) {
        return new Inputs(canSee, distSqr, RANGE_SQR, ammoEmpty, RELOAD, COOLDOWN);
    }

    private static Inputs visible(double distSqr) {
        return in(true, distSqr, false);
    }

    /** Dice that hand out scripted values and fail the test if asked for more. */
    private static final class ScriptedDice implements Dice {
        private final Deque<Float> floats = new ArrayDeque<>();
        private final Deque<Integer> ints = new ArrayDeque<>();
        int floatCalls;
        int intCalls;
        int lastIntBound;

        ScriptedDice floats(float... values) {
            for (float v : values) {
                floats.add(v);
            }
            return this;
        }

        ScriptedDice ints(int... values) {
            for (int v : values) {
                ints.add(v);
            }
            return this;
        }

        @Override
        public float nextFloat() {
            floatCalls++;
            if (floats.isEmpty()) {
                throw new AssertionError("unexpected nextFloat()");
            }
            return floats.pop();
        }

        @Override
        public int nextInt(int bound) {
            intCalls++;
            lastIntBound = bound;
            if (ints.isEmpty()) {
                throw new AssertionError("unexpected nextInt(" + bound + ")");
            }
            return ints.pop();
        }
    }

    /** Dice that always give the same answers, for tests that do not care. */
    private static Dice steady() {
        return new Dice() {
            @Override
            public float nextFloat() {
                return 0.9f; // never flips the strafe direction
            }

            @Override
            public int nextInt(int bound) {
                return 0; // shortest path delay
            }
        };
    }

    /**
     * Tick the machine until it is in position: five visible ticks in range.
     * Draws one int on tick 1 (the path delay) and fires on tick 5.
     */
    private static FireControl settled(Dice dice) {
        FireControl control = new FireControl(dice);
        for (int i = 0; i < 5; i++) {
            control.tick(visible(IN_RANGE));
        }
        return control;
    }

    private static boolean strafing(Outputs out) {
        return out.movement() instanceof Movement.Strafe;
    }

    // --- visibility streak ---------------------------------------------------

    @Test
    void fourVisibleTicksAreNotEnoughToHoldPosition() {
        FireControl control = new FireControl(steady());
        Outputs out = null;
        for (int i = 0; i < 4; i++) {
            out = control.tick(visible(IN_RANGE));
        }
        assertFalse(strafing(out));
    }

    @Test
    void fifthVisibleTickHoldsPosition() {
        FireControl control = new FireControl(steady());
        Outputs out = null;
        for (int i = 0; i < 5; i++) {
            out = control.tick(visible(IN_RANGE));
        }
        assertTrue(strafing(out));
    }

    @Test
    void sixthVisibleTickStillHoldsPosition() {
        FireControl control = settled(steady());
        assertTrue(strafing(control.tick(visible(IN_RANGE))));
    }

    @Test
    void losingSightDropsPositionAtOnce() {
        FireControl control = settled(steady());
        assertFalse(strafing(control.tick(in(false, IN_RANGE, false))));
    }

    @Test
    void regainingSightNeedsFiveMoreTicks() {
        FireControl control = settled(steady());
        control.tick(in(false, IN_RANGE, false));
        Outputs out = null;
        for (int i = 0; i < 4; i++) {
            out = control.tick(visible(IN_RANGE));
        }
        assertFalse(strafing(out));
        assertTrue(strafing(control.tick(visible(IN_RANGE))));
    }

    @Test
    void resetRestartsTheStreak() {
        FireControl control = settled(steady());
        control.reset();
        assertFalse(strafing(control.tick(visible(IN_RANGE))));
    }

    // --- position ------------------------------------------------------------

    @Test
    void inRangeWithAShortStreakIsOutOfPosition() {
        FireControl control = new FireControl(steady());
        assertFalse(strafing(control.tick(visible(IN_RANGE))));
    }

    @Test
    void outOfRangeWithALongStreakIsOutOfPosition() {
        FireControl control = settled(steady());
        assertFalse(strafing(control.tick(visible(OUT_OF_RANGE))));
    }

    @Test
    void exactlyAtRangeIsInPosition() {
        FireControl control = new FireControl(steady());
        Outputs out = null;
        for (int i = 0; i < 5; i++) {
            out = control.tick(visible(RANGE_SQR));
        }
        assertTrue(strafing(out));
    }

    // --- out-of-position movement --------------------------------------------

    @Test
    void freshMachineRepathsOnItsFirstTickAndDrawsTheDelay() {
        ScriptedDice dice = new ScriptedDice().ints(5);
        FireControl control = new FireControl(dice);
        Outputs out = control.tick(visible(OUT_OF_RANGE));
        assertInstanceOf(Movement.Repath.class, out.movement());
        assertEquals(1, dice.intCalls);
        assertEquals(21, dice.lastIntBound);
    }

    @Test
    void keepsCourseUntilTheDelayExpiresThenRepaths() {
        // Delay becomes 20 + 0 = 20 on the first tick; it is decremented before
        // being tested, so ticks 2..20 keep course and tick 21 repaths.
        ScriptedDice dice = new ScriptedDice().ints(0, 0);
        FireControl control = new FireControl(dice);
        control.tick(visible(OUT_OF_RANGE));
        for (int t = 2; t <= 20; t++) {
            assertInstanceOf(Movement.KeepCourse.class, control.tick(visible(OUT_OF_RANGE)).movement(),
                    "tick " + t);
        }
        assertInstanceOf(Movement.Repath.class, control.tick(visible(OUT_OF_RANGE)).movement());
        assertEquals(2, dice.intCalls);
    }

    @Test
    void leavingPositionRepathsAtOnce() {
        // Being in position zeroes the delay, so the first out-of-position tick
        // decrements it to -1 and repaths immediately -- a second draw.
        ScriptedDice dice = new ScriptedDice().ints(0, 0);
        FireControl control = settled(dice);
        assertInstanceOf(Movement.Repath.class, control.tick(visible(OUT_OF_RANGE)).movement());
        assertEquals(2, dice.intCalls);
    }

    // --- in-position movement ------------------------------------------------

    @Test
    void firstInPositionTickStrafes() {
        FireControl control = new FireControl(steady());
        Outputs out = null;
        for (int i = 0; i < 5; i++) {
            out = control.tick(visible(IN_RANGE));
        }
        assertInstanceOf(Movement.Strafe.class, out.movement());
    }

    @Test
    void strafingDrawsNoDiceUntilTheTwentiethTick() {
        ScriptedDice dice = new ScriptedDice().ints(0);
        FireControl control = settled(dice); // tick 5 is strafe tick 1
        for (int strafeTick = 2; strafeTick <= 19; strafeTick++) {
            control.tick(visible(IN_RANGE));
        }
        assertEquals(0, dice.floatCalls);
        assertEquals(1, dice.intCalls);
    }

    @Test
    void twentiethStrafeTickDrawsAndKeepsDirectionOnAHighRoll() {
        ScriptedDice dice = new ScriptedDice().ints(0).floats(0.9f);
        FireControl control = settled(dice);
        Movement.Strafe before = (Movement.Strafe) control.tick(visible(IN_RANGE)).movement();
        for (int strafeTick = 3; strafeTick <= 19; strafeTick++) {
            control.tick(visible(IN_RANGE));
        }
        Movement.Strafe at20 = (Movement.Strafe) control.tick(visible(IN_RANGE)).movement();
        assertEquals(1, dice.floatCalls);
        assertEquals(before.clockwise(), at20.clockwise());
    }

    @Test
    void twentiethStrafeTickFlipsDirectionOnALowRoll() {
        ScriptedDice dice = new ScriptedDice().ints(0).floats(0.1f);
        FireControl control = settled(dice);
        Movement.Strafe before = (Movement.Strafe) control.tick(visible(IN_RANGE)).movement();
        for (int strafeTick = 3; strafeTick <= 19; strafeTick++) {
            control.tick(visible(IN_RANGE));
        }
        Movement.Strafe at20 = (Movement.Strafe) control.tick(visible(IN_RANGE)).movement();
        assertEquals(!before.clockwise(), at20.clockwise());
    }

    @Test
    void theStrafeCounterRestartsAfterTheTwentiethTick() {
        // After the draw at tick 20 the next draw must be 20 ticks later, not 1.
        ScriptedDice dice = new ScriptedDice().ints(0).floats(0.9f, 0.9f);
        FireControl control = settled(dice);
        for (int strafeTick = 2; strafeTick <= 20; strafeTick++) {
            control.tick(visible(IN_RANGE));
        }
        assertEquals(1, dice.floatCalls);
        for (int strafeTick = 1; strafeTick <= 19; strafeTick++) {
            control.tick(visible(IN_RANGE));
        }
        assertEquals(1, dice.floatCalls);
        control.tick(visible(IN_RANGE));
        assertEquals(2, dice.floatCalls);
    }

    // --- hysteresis -----------------------------------------------------------

    private static boolean backwards(FireControl control, double distSqr) {
        return ((Movement.Strafe) control.tick(visible(distSqr)).movement()).backwards();
    }

    @Test
    void farOutInTheBandPushesForwardFromEitherPrior() {
        FireControl control = settled(steady());
        assertFalse(backwards(control, 0.9 * RANGE_SQR)); // prior: forward (the default)
        backwards(control, 0.1 * RANGE_SQR); // now backward
        assertFalse(backwards(control, 0.9 * RANGE_SQR)); // prior: backward
    }

    @Test
    void closeInPushesBackwardFromEitherPrior() {
        FireControl control = settled(steady());
        assertTrue(backwards(control, 0.1 * RANGE_SQR)); // prior: forward
        backwards(control, 0.9 * RANGE_SQR); // now forward
        assertTrue(backwards(control, 0.1 * RANGE_SQR)); // prior: forward again
    }

    @Test
    void deadBandKeepsForward() {
        FireControl control = settled(steady());
        backwards(control, 0.9 * RANGE_SQR); // forward
        assertFalse(backwards(control, 0.5 * RANGE_SQR));
    }

    @Test
    void deadBandKeepsBackward() {
        FireControl control = settled(steady());
        backwards(control, 0.1 * RANGE_SQR); // backward
        assertTrue(backwards(control, 0.5 * RANGE_SQR));
    }

    @Test
    void theBandEdgesAreInsideTheDeadBand() {
        FireControl control = settled(steady());
        backwards(control, 0.1 * RANGE_SQR); // backward
        assertTrue(backwards(control, 0.75 * RANGE_SQR)); // not > 0.75, so unchanged
        backwards(control, 0.9 * RANGE_SQR); // forward
        assertFalse(backwards(control, 0.25 * RANGE_SQR)); // not < 0.25, so unchanged
    }

    // --- trigger --------------------------------------------------------------

    @Test
    void readyAndInPositionFires() {
        FireControl control = new FireControl(steady());
        Outputs out = null;
        for (int i = 0; i < 5; i++) {
            out = control.tick(visible(IN_RANGE));
        }
        assertEquals(Action.FIRE, out.action());
    }

    @Test
    void afterFiringItCoolsForTheCooldownThenFiresAgain() {
        FireControl control = settled(steady()); // fired on tick 5
        for (int i = 0; i < COOLDOWN; i++) {
            assertEquals(Action.IDLE, control.tick(visible(IN_RANGE)).action(), "cooling tick " + (i + 1));
        }
        assertEquals(Action.FIRE, control.tick(visible(IN_RANGE)).action());
    }

    @Test
    void readyButOutOfPositionIdles() {
        FireControl control = new FireControl(steady());
        assertEquals(Action.IDLE, control.tick(visible(OUT_OF_RANGE)).action());
    }

    @Test
    void emptyGunStartsAReloadEvenOutOfPosition() {
        FireControl control = new FireControl(steady());
        assertEquals(Action.RELOAD_STARTED, control.tick(in(true, OUT_OF_RANGE, true)).action());
    }

    @Test
    void emptyGunWhileCoolingWaitsForTheCooldownFirst() {
        FireControl control = settled(steady()); // fired, cooling for 3
        assertEquals(Action.IDLE, control.tick(in(true, IN_RANGE, true)).action());
        assertEquals(Action.IDLE, control.tick(in(true, IN_RANGE, true)).action());
        assertEquals(Action.IDLE, control.tick(in(true, IN_RANGE, true)).action());
        assertEquals(Action.RELOAD_STARTED, control.tick(in(true, IN_RANGE, true)).action());
    }

    @Test
    void reloadIdlesThenFinishesOnTheLastTick() {
        FireControl control = new FireControl(steady());
        control.tick(in(true, IN_RANGE, true)); // RELOAD_STARTED
        for (int i = 1; i < RELOAD; i++) {
            assertEquals(Action.IDLE, control.tick(in(true, IN_RANGE, true)).action(), "reload tick " + i);
        }
        assertEquals(Action.RELOAD_FINISHED, control.tick(in(true, IN_RANGE, false)).action());
    }

    @Test
    void reloadingStillMoves() {
        FireControl closing = new FireControl(steady());
        closing.tick(in(true, OUT_OF_RANGE, true)); // reload starts, repaths
        Outputs out = closing.tick(in(true, OUT_OF_RANGE, true));
        assertEquals(Action.IDLE, out.action());
        assertInstanceOf(Movement.KeepCourse.class, out.movement());

        FireControl circling = settled(steady()); // fired, cooling for 3
        for (int i = 0; i < COOLDOWN; i++) {
            circling.tick(in(true, IN_RANGE, true));
        }
        assertEquals(Action.RELOAD_STARTED, circling.tick(in(true, IN_RANGE, true)).action());
        assertTrue(strafing(circling.tick(in(true, IN_RANGE, true))));
    }

    // --- reset ----------------------------------------------------------------

    @Test
    void resetMidReloadClearsIt() {
        FireControl control = new FireControl(steady());
        control.tick(in(true, IN_RANGE, true)); // reloading
        control.reset();
        // With the gun still empty, a fresh RELOAD_STARTED proves the old one is gone.
        assertEquals(Action.RELOAD_STARTED, control.tick(in(true, IN_RANGE, true)).action());
    }

    @Test
    void resetMidCooldownClearsIt() {
        FireControl control = settled(steady()); // fired, cooling
        control.reset();
        for (int i = 0; i < 4; i++) {
            control.tick(visible(IN_RANGE));
        }
        // Five ticks after reset it is back in position with no cooldown left.
        assertEquals(Action.FIRE, control.tick(visible(IN_RANGE)).action());
    }

    @Test
    void resetKeepsTheStrafeDirections() {
        // ints: tick 1's path delay, then the first tick after reset. The float
        // is the 20th strafe tick's roll, low enough to flip clockwise.
        ScriptedDice dice = new ScriptedDice().ints(0, 0).floats(0.1f);
        FireControl control = settled(dice);
        backwards(control, 0.1 * RANGE_SQR); // strafe tick 2: now backward
        for (int strafeTick = 3; strafeTick <= 20; strafeTick++) {
            control.tick(visible(0.5 * RANGE_SQR)); // tick 20 flips clockwise
        }
        Movement.Strafe before = (Movement.Strafe) control.tick(visible(0.5 * RANGE_SQR)).movement();
        assertTrue(before.backwards());
        assertTrue(before.clockwise());

        control.reset();
        for (int i = 0; i < 5; i++) {
            control.tick(visible(0.5 * RANGE_SQR));
        }
        Movement.Strafe after = (Movement.Strafe) control.tick(visible(0.5 * RANGE_SQR)).movement();
        assertEquals(before.backwards(), after.backwards());
        assertEquals(before.clockwise(), after.clockwise());
    }

    // --- inputs ---------------------------------------------------------------

    @Test
    void inputsRejectNegativeDistances() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, -1, RANGE_SQR, false, RELOAD, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, IN_RANGE, -1, false, RELOAD, COOLDOWN));
    }

    @Test
    void inputsRejectAZeroReloadDuration() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, IN_RANGE, RANGE_SQR, false, 0, COOLDOWN));
    }

    @Test
    void inputsRejectAZeroFireCooldown() {
        assertThrows(IllegalArgumentException.class, () -> new Inputs(true, IN_RANGE, RANGE_SQR, false, RELOAD, 0));
    }

    @Test
    void nullDiceAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FireControl(null));
    }

    // --- invariants -----------------------------------------------------------

    @Test
    void aThousandRandomTicksNeverBreakTheRepOrTheTriggerRules() {
        Random random = new Random(20260904L);
        Dice dice = new Dice() {
            @Override
            public float nextFloat() {
                return random.nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }
        };
        FireControl control = new FireControl(dice);
        int ticksSinceFire = Integer.MAX_VALUE;
        boolean reloading = false;
        for (int t = 0; t < 1000; t++) {
            Inputs in = new Inputs(random.nextBoolean(), random.nextDouble() * 300, RANGE_SQR,
                    random.nextInt(8) == 0, RELOAD, COOLDOWN);
            Outputs out = control.tick(in); // checkRep runs inside, under -ea
            if (ticksSinceFire != Integer.MAX_VALUE) {
                ticksSinceFire++;
            }
            switch (out.action()) {
                case FIRE -> {
                    assertTrue(ticksSinceFire > COOLDOWN, "fired inside a cooldown at tick " + t);
                    assertFalse(reloading, "fired mid-reload at tick " + t);
                    ticksSinceFire = 0;
                }
                case RELOAD_STARTED -> {
                    assertFalse(reloading, "reload started twice at tick " + t);
                    reloading = true;
                }
                case RELOAD_FINISHED -> {
                    assertTrue(reloading, "reload finished without starting at tick " + t);
                    reloading = false;
                }
                case IDLE -> {
                    // nothing to check
                }
            }
        }
    }

    // --- dice consumption -----------------------------------------------------

    @Test
    void quietTicksDrawNoDice() {
        // In position, strafe ticks 2..19 draw nothing: the only draw is tick
        // 1's path delay. An unqueued draw would throw.
        ScriptedDice counting = new ScriptedDice().ints(0);
        FireControl circling = settled(counting);
        for (int strafeTick = 2; strafeTick <= 19; strafeTick++) {
            circling.tick(visible(IN_RANGE));
        }
        assertEquals(1, counting.intCalls);
        assertEquals(0, counting.floatCalls);

        // Out of position with an unexpired delay, nothing is drawn either.
        ScriptedDice oneRepath = new ScriptedDice().ints(20);
        FireControl coursing = new FireControl(oneRepath);
        coursing.tick(visible(OUT_OF_RANGE)); // draws once, delay := 40
        for (int t = 0; t < 39; t++) {
            coursing.tick(visible(OUT_OF_RANGE)); // decrements to 1, never draws
        }
        assertEquals(1, oneRepath.intCalls);
        assertEquals(0, oneRepath.floatCalls);
    }
}
