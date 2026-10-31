package dev.solclient.addons.pvp;

import java.util.ArrayList;
import java.util.List;

/**
 * A kit = vanilla commands run on the practice world's server (so it only depends on vanilla command syntax,
 * not on game internals). "@P" in a command is replaced by the player name, "@B" by the bot's UUID.
 * To add a kit: add a {@code kit(...)} call in the static block.
 *
 * The {@link Style} tells the bot AI how to fight with the kit; {@link #w} lists the weapons the bot can swap between.
 */
public final class PracticeKit {

	/** How the bot plays this kit. */
	public enum Style {
		/** plain melee */
		SWORD,
		/** axe vs shields */
		AXE,
		/** wind charge launch + mace smash, sword as backup */
		MACE,
		/** spear lunge, then wind charge + mace smash */
		SPEAR_MACE,
		/** attribute swaps: lunge swap with the spear, axe swap vs shields, mace launches */
		SWAP
	}

	public enum Weapon { SWORD, AXE, MACE, SPEAR }

	/** The kits that are written in this file. Use {@link #all()} to also get the ones made in the kit editor. */
	public static final List<PracticeKit> ALL = new ArrayList<>();

	/** Built-in kits followed by the player's custom kits (see {@link KitStore}). */
	public static List<PracticeKit> all() {
		List<PracticeKit> l = new ArrayList<>(ALL);
		l.addAll(KitStore.kits());
		return l;
	}

	/** Builds a kit made in the kit editor (not added to {@link #ALL}). */
	static PracticeKit custom(String name, String desc, Style style, float botDamage, String[] player, String[] bot,
			String sword, String axe, String mace, String spear, int density) {
		PracticeKit k = new PracticeKit(name, desc, style, player, bot, botDamage).w(sword, axe, mace, spear, density);
		k.custom = true;
		return k;
	}

	public final String name;
	public final String description;
	public final Style style;
	/** Commands that give the player the kit. */
	public final String[] player;
	/** Commands that equip each bot. */
	public final String[] bot;
	/** Hit damage of a bot with the sword/axe (before difficulty multiplier). */
	public final float botDamage;

	/** Weapons the bot can hold (null = kit does not have it). */
	public String sword, axe, mace, spear;
	/** True for kits made in the kit editor (they can be edited and deleted). */
	public boolean custom;
	/** Density level of the bot's mace (adds to smash damage per block fallen). */
	public int density = 3;

	private PracticeKit(String name, String description, Style style, String[] player, String[] bot, float botDamage) {
		this.name = name;
		this.description = description;
		this.style = style;
		this.player = player;
		this.bot = bot;
		this.botDamage = botDamage;
	}

	private PracticeKit w(String sword, String axe, String mace, String spear, int density) {
		this.sword = sword;
		this.axe = axe;
		this.mace = mace;
		this.spear = spear;
		this.density = density;
		return this;
	}

	/** Item the bot holds when it is not in the middle of a combo. */
	public Weapon rest() {
		return switch (style) {
			case SPEAR_MACE -> Weapon.SPEAR;
			case AXE -> Weapon.AXE;
			default -> Weapon.SWORD;
		};
	}

	public String item(Weapon wp) {
		return switch (wp) {
			case SWORD -> sword;
			case AXE -> axe;
			case MACE -> mace;
			case SPEAR -> spear;
		};
	}

	private static PracticeKit kit(String name, String desc, Style style, float botDamage, String[] player, String[] bot) {
		PracticeKit k = new PracticeKit(name, desc, style, player, bot, botDamage);
		ALL.add(k);
		return k;
	}

	private static String give(String slot, String item) {
		return "item replace entity @P " + slot + " with " + item;
	}

	private static String wear(String slot, String item) {
		return "item replace entity @B " + slot + " with " + item;
	}

	private static String[] armorP(String type, String ench) {
		return new String[] {
				give("armor.head", "minecraft:" + type + "_helmet" + ench),
				give("armor.chest", "minecraft:" + type + "_chestplate" + ench),
				give("armor.legs", "minecraft:" + type + "_leggings" + ench),
				give("armor.feet", "minecraft:" + type + "_boots" + ench)
		};
	}

	private static String[] armorB(String type, String ench) {
		return new String[] {
				wear("armor.head", "minecraft:" + type + "_helmet" + ench),
				wear("armor.chest", "minecraft:" + type + "_chestplate" + ench),
				wear("armor.legs", "minecraft:" + type + "_leggings" + ench),
				wear("armor.feet", "minecraft:" + type + "_boots" + ench)
		};
	}

	private static String[] cat(String[] a, String... b) {
		String[] r = new String[a.length + b.length];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}

	private static final String HEAL = "minecraft:splash_potion[potion_contents={potion:\"minecraft:strong_healing\"}]";

	static {
		String diaP = "[enchantments={protection:4,unbreaking:3}]";
		String neoP = "[enchantments={protection:4,unbreaking:3,mending:1}]";

		String diaSword3 = "minecraft:diamond_sword[enchantments={sharpness:3,unbreaking:3}]";
		String diaSword5 = "minecraft:diamond_sword[enchantments={sharpness:5,unbreaking:3}]";
		String neoSword = "minecraft:netherite_sword[enchantments={sharpness:5,unbreaking:3,mending:1}]";
		String neoAxe = "minecraft:netherite_axe[enchantments={sharpness:5,unbreaking:3,mending:1}]";
		String maceP = "minecraft:mace[enchantments={density:5,wind_burst:3,unbreaking:3}]";
		String maceB = "minecraft:mace[enchantments={density:5}]";
		String spearP = "minecraft:netherite_spear[enchantments={lunge:3,unbreaking:3}]";

		// ------------------------------------------------------------------ plain melee kits
		kit("Sword", "Diamond gear, sharp sword, shield and golden apples.", Style.SWORD, 7F,
				cat(armorP("diamond", diaP),
						give("hotbar.0", diaSword3),
						give("weapon.offhand", "minecraft:shield"),
						give("hotbar.1", "minecraft:golden_apple 16"),
						give("hotbar.2", "minecraft:ender_pearl 8"),
						give("hotbar.3", "minecraft:cooked_beef 64")),
				cat(armorB("diamond", diaP),
						wear("weapon.offhand", "minecraft:shield")))
				.w("minecraft:diamond_sword[enchantments={sharpness:3}]", null, null, null, 3);

		{
			String[] p = new String[] {
					give("hotbar.0", diaSword5),
					give("hotbar.1", "minecraft:ender_pearl 16"),
					give("hotbar.2", "minecraft:splash_potion[potion_contents={potion:\"minecraft:strong_swiftness\"}]"),
					give("hotbar.8", "minecraft:cooked_beef 64")
			};
			List<String> l = new ArrayList<>(List.of(p));
			for (int i = 3; i <= 7; i++) {
				l.add(give("hotbar." + i, HEAL));
			}
			for (int i = 0; i <= 7; i++) {
				l.add(give("inventory." + i, HEAL));
			}
			kit("NoDebuff", "Diamond armor, sharp 5 sword and lots of healing / speed potions.", Style.SWORD, 6.5F,
					cat(armorP("diamond", diaP), l.toArray(new String[0])),
					armorB("diamond", diaP))
					.w("minecraft:diamond_sword[enchantments={sharpness:5}]", null, null, null, 3);
		}

		kit("Netherite", "Full netherite, sharp 5 sword, totem and gapples.", Style.SWORD, 8F,
				cat(armorP("netherite", neoP),
						give("hotbar.0", neoSword),
						give("hotbar.1", "minecraft:enchanted_golden_apple 8"),
						give("hotbar.2", "minecraft:golden_apple 16"),
						give("hotbar.3", "minecraft:ender_pearl 16"),
						give("hotbar.4", "minecraft:cooked_beef 64"),
						give("weapon.offhand", "minecraft:totem_of_undying")),
				cat(armorB("netherite", neoP),
						wear("weapon.offhand", "minecraft:totem_of_undying")))
				.w("minecraft:netherite_sword[enchantments={sharpness:5}]", null, null, null, 3);

		kit("Axe & Shield", "Iron gear, diamond axe + shield. Bot swaps to the axe to break your shield.", Style.AXE, 8.5F,
				cat(armorP("iron", "[enchantments={protection:3}]"),
						give("hotbar.0", "minecraft:diamond_axe[enchantments={sharpness:3}]"),
						give("hotbar.1", "minecraft:diamond_sword[enchantments={sharpness:3}]"),
						give("weapon.offhand", "minecraft:shield"),
						give("hotbar.2", "minecraft:golden_apple 8"),
						give("hotbar.3", "minecraft:cooked_beef 64")),
				cat(armorB("iron", "[enchantments={protection:3}]"),
						wear("weapon.offhand", "minecraft:shield")))
				.w("minecraft:diamond_sword[enchantments={sharpness:3}]", "minecraft:diamond_axe[enchantments={sharpness:3}]", null, null, 3);

		// ------------------------------------------------------------------ mace / spear kits
		kit("Mace", "Diamond armor, mace + wind charges, sword as backup. Bot launches itself and smashes.", Style.MACE, 7F,
				cat(armorP("diamond", diaP),
						give("hotbar.0", "minecraft:mace[enchantments={density:3,wind_burst:2,unbreaking:3}]"),
						give("hotbar.1", "minecraft:diamond_sword[enchantments={sharpness:3}]"),
						give("hotbar.2", "minecraft:wind_charge 64"),
						give("hotbar.3", "minecraft:golden_apple 16"),
						give("hotbar.4", "minecraft:cooked_beef 64")),
				armorB("diamond", diaP))
				.w("minecraft:diamond_sword[enchantments={sharpness:3}]", null, "minecraft:mace[enchantments={density:3}]", null, 3);

		kit("Spear Mace", "Netherite, lunge spear + mace + wind charges. Bot lunges in, then launches and smashes.", Style.SPEAR_MACE, 7F,
				cat(armorP("netherite", neoP),
						give("hotbar.0", spearP),
						give("hotbar.1", maceP),
						give("hotbar.2", "minecraft:wind_charge 64"),
						give("hotbar.3", neoSword),
						give("hotbar.4", "minecraft:enchanted_golden_apple 8"),
						give("hotbar.5", "minecraft:golden_apple 16"),
						give("hotbar.6", "minecraft:ender_pearl 16"),
						give("hotbar.8", "minecraft:cooked_beef 64"),
						give("weapon.offhand", "minecraft:totem_of_undying")),
				cat(armorB("netherite", neoP),
						wear("weapon.offhand", "minecraft:totem_of_undying")))
				.w("minecraft:netherite_sword[enchantments={sharpness:5}]", null, maceB, spearP, 5);

		kit("Attr Swap", "Sword, axe, mace and lunge spear. Bot does lunge swaps and swaps to the axe vs your shield.", Style.SWAP, 7.5F,
				cat(armorP("netherite", neoP),
						give("hotbar.0", neoSword),
						give("hotbar.1", neoAxe),
						give("hotbar.2", maceP),
						give("hotbar.3", spearP),
						give("hotbar.4", "minecraft:wind_charge 64"),
						give("hotbar.5", "minecraft:golden_apple 16"),
						give("hotbar.6", "minecraft:ender_pearl 16"),
						give("hotbar.8", "minecraft:cooked_beef 64"),
						give("weapon.offhand", "minecraft:shield")),
				cat(armorB("netherite", neoP),
						wear("weapon.offhand", "minecraft:shield")))
				.w("minecraft:netherite_sword[enchantments={sharpness:5}]",
						"minecraft:netherite_axe[enchantments={sharpness:5}]", maceB, spearP, 5);

		kit("Starter", "Iron gear, iron sword and shield. Good to warm up.", Style.SWORD, 5.5F,
				cat(armorP("iron", ""),
						give("hotbar.0", "minecraft:iron_sword"),
						give("weapon.offhand", "minecraft:shield"),
						give("hotbar.1", "minecraft:golden_apple 4"),
						give("hotbar.2", "minecraft:cooked_beef 64")),
				armorB("iron", ""))
				.w("minecraft:iron_sword", null, null, null, 3);
	}
}
