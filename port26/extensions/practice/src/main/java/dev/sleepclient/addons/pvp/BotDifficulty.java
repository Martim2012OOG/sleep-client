package dev.sleepclient.addons.pvp;

/** Bot strength. Tweak the numbers here to rebalance. */
public enum BotDifficulty {
	//          name       speed  cooldown  reach  accuracy dmgMul strafe crits heals swaps comboCd
	EASY("Easy",        0.15F, 16, 2.6F, 0.50F, 0.65F, false, false, 0, false, 200),
	MEDIUM("Medium",    0.19F, 13, 2.9F, 0.70F, 0.85F, false, false, 1, false, 130),
	HARD("Hard",        0.23F, 11, 3.0F, 0.85F, 1.00F, true,  true,  2, true,   85),
	EXTREME("Extreme",  0.27F,  9, 3.2F, 0.95F, 1.15F, true,  true,  4, true,   55);

	public final String label;
	/** Blocks per tick when chasing (sprinting player is about 0.28). */
	public final float speed;
	/** Ticks between swings. */
	public final int cooldown;
	/** Distance at which the bot can hit. */
	public final float reach;
	/** Chance a swing connects. */
	public final float accuracy;
	/** Multiplies the kit's base bot damage. */
	public final float dmgMul;
	public final boolean strafe;
	public final boolean crits;
	/** Healing "potions" the bot can use per life. */
	public final int heals;
	/** Bot does attribute swaps (lunge swap with the spear, axe swap against shields, faster swap hits). */
	public final boolean swaps;
	/** Ticks between two wind-charge + mace launches (also the spear lunge cadence is tied to this). */
	public final int comboCd;

	BotDifficulty(String label, float speed, int cooldown, float reach, float accuracy, float dmgMul, boolean strafe, boolean crits, int heals, boolean swaps, int comboCd) {
		this.label = label;
		this.speed = speed;
		this.cooldown = cooldown;
		this.reach = reach;
		this.accuracy = accuracy;
		this.dmgMul = dmgMul;
		this.strafe = strafe;
		this.crits = crits;
		this.heals = heals;
		this.swaps = swaps;
		this.comboCd = comboCd;
	}
}
