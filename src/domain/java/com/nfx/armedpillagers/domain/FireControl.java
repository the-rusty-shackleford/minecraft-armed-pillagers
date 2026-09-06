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
 * The fire-control state machine: each tick, given what the mob can see and
 * how far away its target is, decide whether to close, circle, fire, or reload.
 *
 * <p>Shaped after vanilla's ranged-attack goals -- close until the weapon's
 * range and line of sight are both good, then hold position and circle while
 * shooting -- but expressed over plain numbers and booleans so the whole
 * machine is testable without a game. The adapter reads the mob into an
 * {@link Inputs}, ticks, and applies the {@link Outputs}; nothing here touches
 * an entity.
 *
 * <p>Two things about the ordering are easy to get wrong and are part of the
 * contract. Movement is decided <em>before</em> the trigger, so a mob that is
 * reloading or cooling down still closes or circles -- it stands
 * <em>exposed</em> through a reload, not still. And the hysteresis band that
 * decides whether to push in or drift out is on <em>squared</em> distance, so
 * its edges are at about 0.87 and 0.5 of the range, not 0.75 and 0.25.
 *
 * <p>Randomness comes through {@link Dice}, and is drawn at exactly the two
 * points the original inline code drew it -- once when a new path delay is
 * chosen, once every twentieth strafing tick -- so an adapter that binds it to
 * the mob's own random source consumes that source identically. The one
 * allocation per tick is the returned {@link Outputs}; a {@link Movement}
 * record for Repath and KeepCourse is shared, and a Strafe carries two booleans.
 *
 * <p>Mutable: a machine is one engagement's worth of state and is
 * {@link #reset()} between engagements.
 */
public final class FireControl {

    /** Consecutive visible ticks before the mob trusts its line of sight enough to hold and fire. */
    static final int SEE_TICKS_TO_FIRE = 5;
    /** Ticks between rolls to flip the circling direction. */
    static final int STRAFE_PERIOD_TICKS = 20;
    /** Chance, on each such roll, that the direction flips. */
    static final float STRAFE_FLIP_CHANCE = 0.3f;
    /** Beyond this fraction of the squared range the mob pushes inward. */
    static final double HOLD_OUTER = 0.75;
    /** Inside this fraction of the squared range the mob backs off. */
    static final double HOLD_INNER = 0.25;
    /** A new path is computed every one to two seconds while closing. */
    static final int PATH_DELAY_MIN_TICKS = 20;
    static final int PATH_DELAY_MAX_TICKS = 40;

    /**
     * The only randomness fire control uses.
     *
     * <p>requires: {@code nextFloat()} returns a value in {@code [0, 1)};
     * {@code nextInt(bound)} returns a value in {@code [0, bound)} for
     * {@code bound >= 1}
     */
    public interface Dice {
        float nextFloat();

        int nextInt(int bound);
    }

    /**
     * One tick's view of the world.
     *
     * <p>RI: {@code distSqr >= 0}, {@code rangeSqr >= 0},
     * {@code reloadDuration >= 1}, {@code fireCooldown >= 1}. A reload of zero
     * ticks would report RELOAD_STARTED every tick and never finish; a weapon
     * with no capacity must be refused before it reaches this machine.
     *
     * @param canSee         whether the mob has line of sight to its target this tick
     * @param distSqr        squared distance to the target
     * @param rangeSqr       squared engagement range of the held weapon
     * @param ammoEmpty      whether the held weapon has no rounds
     * @param reloadDuration ticks a reload takes, if one starts this tick
     * @param fireCooldown   ticks the trigger waits after a shot, if one fires this tick
     */
    public record Inputs(boolean canSee, double distSqr, double rangeSqr, boolean ammoEmpty,
                         int reloadDuration, int fireCooldown) {
        /**
         * @throws IllegalArgumentException if any precondition fails
         */
        public Inputs {
            if (!(distSqr >= 0)) {
                throw new IllegalArgumentException("distSqr must be >= 0, was " + distSqr);
            }
            if (!(rangeSqr >= 0)) {
                throw new IllegalArgumentException("rangeSqr must be >= 0, was " + rangeSqr);
            }
            if (reloadDuration < 1) {
                throw new IllegalArgumentException("reloadDuration must be >= 1, was " + reloadDuration);
            }
            if (fireCooldown < 1) {
                throw new IllegalArgumentException("fireCooldown must be >= 1, was " + fireCooldown);
            }
        }
    }

    /** What the mob's legs should do this tick. */
    public sealed interface Movement {
        /** Compute a fresh path to the target and follow it. */
        record Repath() implements Movement {}

        /** Do nothing; the last path stands. */
        record KeepCourse() implements Movement {}

        /**
         * Stop pathing and sidestep around the target.
         *
         * @param backwards whether to also back away (else push in)
         * @param clockwise which way round
         */
        record Strafe(boolean backwards, boolean clockwise) implements Movement {}
    }

    /** What the mob's trigger finger should do this tick. */
    public enum Action {
        /** Nothing: cooling down, mid-reload, or not in position. */
        IDLE,
        /** The gun is empty; a reload has begun and will take {@code reloadDuration} ticks. */
        RELOAD_STARTED,
        /** The reload just completed; the adapter should refill the weapon. */
        RELOAD_FINISHED,
        /** Fire one shot now. */
        FIRE
    }

    /**
     * One tick's decisions.
     *
     * @param movement what the legs do
     * @param action   what the trigger does
     */
    public record Outputs(Movement movement, Action action) {}

    private static final Movement REPATH = new Movement.Repath();
    private static final Movement KEEP_COURSE = new Movement.KeepCourse();

    private final Dice dice;
    private int seeTime;
    private int cooldown;
    private int reloadTicks;
    private int updatePathDelay;
    private int strafeTicks = -1;
    private boolean strafeClockwise;
    private boolean strafeBackwards;

    // Abstraction function:
    //   AF(seeTime, cooldown, reloadTicks, updatePathDelay, strafeTicks,
    //      strafeClockwise, strafeBackwards) = the state of one engagement in which
    //     - line of sight has held for seeTime consecutive ticks (seeTime > 0),
    //       or been lost for -seeTime consecutive ticks (seeTime < 0), or no
    //       tick has happened since the last reset (seeTime == 0);
    //     - the trigger is READY (cooldown == 0 && reloadTicks == 0), COOLING
    //       for `cooldown` more ticks, or RELOADING with `reloadTicks` left;
    //     - the mob is CLOSING on its target, re-pathing in updatePathDelay
    //       ticks (strafeTicks == -1), or CIRCLING it, strafeTicks ticks since
    //       the last direction roll (0..19), clockwise iff strafeClockwise,
    //       backing off iff strafeBackwards;
    //     - strafeClockwise and strafeBackwards are also the directions
    //       circling will resume in.
    // Rep invariant:
    //   reloadTicks >= 0 && cooldown >= 0
    //   !(reloadTicks > 0 && cooldown > 0)
    //   strafeTicks == -1 || (0 <= strafeTicks && strafeTicks <= 19)
    //   updatePathDelay >= 0
    //   strafeTicks >= 0  implies  updatePathDelay == 0
    // Safety from rep exposure:
    //   every field is private and primitive except dice, which is the client's
    //   own object and is never returned; Inputs and Outputs are records of
    //   primitives and immutable Movement values.

    /**
     * @param dice the machine's only source of randomness; never null
     */
    public FireControl(Dice dice) {
        if (dice == null) {
            throw new IllegalArgumentException("dice must not be null");
        }
        this.dice = dice;
        checkRep();
    }

    /**
     * Advances one tick.
     *
     * <p>effects, in this order:
     * <ol>
     * <li>the line-of-sight streak is extended if {@code canSee} matches its
     *     sign, else restarted: {@code seeTime} becomes +1 or -1;</li>
     * <li>the mob is <em>in position</em> iff {@code distSqr <= rangeSqr} and
     *     the streak is at least {@value #SEE_TICKS_TO_FIRE};</li>
     * <li>movement: out of position, the path delay counts down and a
     *     {@link Movement.Repath} is emitted when it expires (with a fresh delay
     *     of {@value #PATH_DELAY_MIN_TICKS} plus {@code dice.nextInt(21)}), else
     *     {@link Movement.KeepCourse}; in position, the path delay is zeroed and
     *     a {@link Movement.Strafe} is emitted, whose clockwise flag is rolled to
     *     flip with {@value #STRAFE_FLIP_CHANCE} chance every
     *     {@value #STRAFE_PERIOD_TICKS} ticks and whose backwards flag follows
     *     the hysteresis band: forward beyond {@value #HOLD_OUTER} of the
     *     squared range, backward inside {@value #HOLD_INNER}, unchanged
     *     between;</li>
     * <li>action: a reload in progress counts down and reports
     *     {@link Action#RELOAD_FINISHED} on its last tick; else a cooldown in
     *     progress counts down; else an empty gun starts a reload of
     *     {@code reloadDuration} ticks and reports {@link Action#RELOAD_STARTED};
     *     else, in position, the gun fires and the trigger cools for
     *     {@code fireCooldown} ticks; else {@link Action#IDLE}.</li>
     * </ol>
     *
     * @param in this tick's view of the world; never null
     * @return this tick's decisions
     */
    public Outputs tick(Inputs in) {
        // 1. line of sight
        if (in.canSee() != seeTime > 0) {
            seeTime = 0;
        }
        seeTime += in.canSee() ? 1 : -1;

        // 2. position
        boolean inPosition = in.distSqr() <= in.rangeSqr() && seeTime >= SEE_TICKS_TO_FIRE;

        // 3. movement
        Movement movement;
        if (!inPosition) {
            strafeTicks = -1;
            if (--updatePathDelay <= 0) {
                updatePathDelay = PATH_DELAY_MIN_TICKS
                        + dice.nextInt(PATH_DELAY_MAX_TICKS - PATH_DELAY_MIN_TICKS + 1);
                movement = REPATH;
            } else {
                movement = KEEP_COURSE;
            }
        } else {
            updatePathDelay = 0;
            if (strafeTicks < 0) {
                strafeTicks = 0;
            }
            if (++strafeTicks >= STRAFE_PERIOD_TICKS) {
                if (dice.nextFloat() < STRAFE_FLIP_CHANCE) {
                    strafeClockwise = !strafeClockwise;
                }
                strafeTicks = 0;
            }
            if (in.distSqr() > in.rangeSqr() * HOLD_OUTER) {
                strafeBackwards = false;
            } else if (in.distSqr() < in.rangeSqr() * HOLD_INNER) {
                strafeBackwards = true;
            }
            movement = new Movement.Strafe(strafeBackwards, strafeClockwise);
        }

        // 4. action
        Action action;
        if (reloadTicks > 0) {
            action = --reloadTicks == 0 ? Action.RELOAD_FINISHED : Action.IDLE;
        } else if (cooldown > 0) {
            cooldown--;
            action = Action.IDLE;
        } else if (in.ammoEmpty()) {
            reloadTicks = in.reloadDuration();
            action = Action.RELOAD_STARTED;
        } else if (inPosition) {
            // inPosition already implies canSee: a streak of five is only
            // possible on a tick that could see.
            cooldown = in.fireCooldown();
            action = Action.FIRE;
        } else {
            action = Action.IDLE;
        }

        checkRep();
        return new Outputs(movement, action);
    }

    /**
     * Forgets the current engagement.
     *
     * <p>effects: the line-of-sight streak, cooldown and reload are cleared and
     * the mob is closing again; the path delay and the circling directions are
     * kept, so the next engagement resumes circling the way the last one ended.
     */
    public void reset() {
        seeTime = 0;
        cooldown = 0;
        reloadTicks = 0;
        strafeTicks = -1;
        checkRep();
    }

    private void checkRep() {
        assert reloadTicks >= 0 : "reloadTicks negative";
        assert cooldown >= 0 : "cooldown negative";
        assert !(reloadTicks > 0 && cooldown > 0) : "reloading and cooling at once";
        assert strafeTicks == -1 || (strafeTicks >= 0 && strafeTicks < STRAFE_PERIOD_TICKS) : "strafeTicks out of range";
        assert updatePathDelay >= 0 : "updatePathDelay negative";
        assert strafeTicks < 0 || updatePathDelay == 0 : "circling with a pending path delay";
    }
}
