package dev.sleepclient.addons.pvp;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fight statistics of the PvP Practice add-on. A "round" starts when the bots spawn and ends when you kill all of
 * them (won) or die (lost). The counters are written by the practice server thread (hits, damage, combos) and by
 * the client thread (swings, counted from left clicks), so the cross-thread ones are atomic / volatile.
 * The result of the last round is kept in {@link #last} so the HUD can show it.
 */
public final class FightStats {

	/** Frozen result of one round. */
	public record Round(int number, boolean won, float dealt, float taken, int swings, int hits, int hitsTaken,
			int bestCombo, int combos, int smashes, int seconds) {

		/** Hits / swings, in percent. */
		public int accuracy() {
			return swings <= 0 ? 0 : Math.min(100, Math.round(hits * 100F / swings));
		}
	}

	/** A combo = hits in a row, each less than this many ticks after the previous, without you being hit. */
	private static final int COMBO_WINDOW = 40;

	public static volatile Round last;
	/** System time (ms) when {@link #last} was produced. */
	public static volatile long shownAt;
	public static volatile boolean live;
	public static volatile int wins, losses;

	private static final AtomicInteger SWINGS = new AtomicInteger();
	private static volatile float dealt, taken;
	private static volatile int hits, hitsTaken, bestCombo, combos, smashes, curCombo, comboAge, ticks, roundNo;

	private FightStats() {
	}

	/** New practice session: forget everything. */
	public static void reset() {
		live = false;
		last = null;
		wins = 0;
		losses = 0;
		roundNo = 0;
	}

	public static void stop() {
		live = false;
	}

	public static void startRound() {
		roundNo++;
		dealt = 0;
		taken = 0;
		hits = 0;
		hitsTaken = 0;
		bestCombo = 0;
		combos = 0;
		smashes = 0;
		curCombo = 0;
		comboAge = 0;
		ticks = 0;
		SWINGS.set(0);
		live = true;
	}

	/** Client thread: one left click = one swing. */
	public static void click() {
		if (live) {
			SWINGS.incrementAndGet();
		}
	}

	/** Server thread, once per tick while a round runs. */
	public static void tick() {
		if (!live) {
			return;
		}
		ticks++;
		if (curCombo > 0 && ++comboAge > COMBO_WINDOW) {
			curCombo = 0;
		}
	}

	/** You hit a bot for {@code dmg} health points. {@code smash} = it was a mace smash (falling mace hit). */
	public static void hit(float dmg, boolean smash) {
		if (!live) {
			return;
		}
		dealt += dmg;
		hits++;
		if (smash) {
			smashes++;
		}
		curCombo = (curCombo > 0 && comboAge <= COMBO_WINDOW) ? curCombo + 1 : 1;
		comboAge = 0;
		if (curCombo == 2) {
			combos++;
		}
		bestCombo = Math.max(bestCombo, curCombo);
	}

	/** A bot hurt you for {@code dmg} health points (breaks your combo). */
	public static void took(float dmg) {
		if (!live || dmg <= 0) {
			return;
		}
		taken += dmg;
		hitsTaken++;
		curCombo = 0;
	}

	/** Closes the round. Returns null if no round was running. */
	public static Round end(boolean won) {
		if (!live) {
			return null;
		}
		live = false;
		Round r = new Round(roundNo, won, dealt, taken, Math.max(SWINGS.get(), hits), hits, hitsTaken, bestCombo,
				combos, smashes, ticks / 20);
		if (won) {
			wins++;
		} else {
			losses++;
		}
		last = r;
		shownAt = System.currentTimeMillis();
		return r;
	}
}
