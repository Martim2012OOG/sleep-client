package dev.solclient.emote;

import java.util.HashMap;
import java.util.Map;

/** Vanilla stand-ins for the props of each emote: {right hand, left hand} (null = nothing). */
public final class EmoteItems {

	private static final Map<String, String[]> MAP = new HashMap<>();

	private static void r(String emote, String right) {
		MAP.put(emote, new String[]{right, null});
	}

	private static void rl(String emote, String right, String left) {
		MAP.put(emote, new String[]{right, left});
	}

	private static final java.util.Set<String> KATANA = java.util.Set.of("katanastrike1", "katanastrike2", "sitwithswords",
			"standwithakatana", "standwithakatana3", "twistthesword", "gaitwithaswordonbelt");

	private static void l(String emote, String left) {
		MAP.put(emote, new String[]{null, left});
	}

	static {
		rl("boxing1", "prop:glove_red", "prop:glove_blue");
		r("cleanfloor", "prop:brush");
		rl("cookonpan", "prop:pan", "prop:spatula");
		r("diggingbayonetshovel", "iron_shovel");
		r("eatfromfork", "prop:fork");
		r("eatfromspoon", "prop:spoon");
		r("fixwithmonkeywrench", "prop:wrench");
		r("flyonumbrella", "prop:umbrella");
		r("glaivestrike1", "prop:spear");
		r("glaivestrike2", "prop:spear");
		r("glaivestrike3", "prop:spear");
		r("ironingthings", "prop:iron");
		r("katanastrike4", "prop:katana");
		r("katanastrike5", "prop:katana");
		r("katanastrike6", "prop:katana");
		r("listentaperecorder", "prop:recorder");
		r("petitionstand", "paper");
		rl("pistolspose", "prop:pistol", "prop:pistol");
		r("protest", "prop:sign");
		MAP.put("readnewspaper", new String[]{null, "prop:newspaper"});
		r("rechargepistol", "prop:pistol");
		r("scythestrike1", "prop:scythe");
		r("scythestrike2", "prop:scythe");
		r("shootfrommachiinegun", "prop:rifle");
		r("shootfrommachinegun", "prop:rifle");
		r("shootyourself", "prop:pistol");
		r("shovelingsnow", "iron_shovel");
		r("showpass", "prop:idcard");
		r("showinginmirror", "prop:mirror");
		r("stickhit1", "stick");
		r("stickhit2", "stick");
		r("stickpose", "stick");
		r("trimscissors", "prop:scissors");
		r("trygetletteroutbottle", "glass_bottle");
		r("washfloormop", "prop:mop");
		r("wipetable", "prop:brush");

		// ---- adereços que faltavam (emotes que têm pista rightItem / leftItem nos ficheiros) ----
		// facas (prop:knife) / catanas (prop:katana)
		for (String e : new String[]{"finishingwithaknife", "ghostfaceattack", "ghostfacepose", "ghostfacerun", "ghostfacewalk",
				"ghostfacewalksit", "jasonattack", "katanastrike1", "katanastrike2", "killerdance", "killersgait", "knifecheck",
				"sitwithswords", "standwithakatana", "standwithakatana3", "twistthesword", "yanderepose", "yandererun",
				"yararara", "gaitwithaswordonbelt", "thekillersstance", "kashimashipose", "kashimashirun", "kashimashiwalk"}) {
			r(e, KATANA.contains(e) ? "prop:katana" : "prop:knife");
		}
		rl("fightingstancewithswords", "iron_sword", "iron_sword");
		rl("runningwithswords", "iron_sword", "iron_sword");
		// espadas grandes / Great Knife
		for (String e : new String[]{"ablowwithaheavysword1", "ablowwithaheavysword2", "standwithaheavysword1", "standwithaheavysword3",
				"pyramidheadattack", "pyramidheadcut", "pyramidheadpose", "pyramidheadrun", "pyramidheadwalk"}) {
			r(e, "prop:greatsword");
		}
		// machados / motosserra
		for (String e : new String[]{"huntressattack", "huntressbait", "huntresspose", "huntressrun",
				"huntressthrowaxe", "huntresswalk"}) {
			r(e, "iron_axe");
		}
		r("bubapose", "prop:chainsaw");
		r("ridingachainsaw", "prop:chainsaw");
		// armas de fogo (mesmo item que o pistolspose)
		for (String e : new String[]{"guntrick", "keepthesafetyontheweapon", "runningwithgun", "runningwithgun1", "walkingwithagun",
				"walkingwithagun1", "pistolpirouette"}) {
			r(e, "prop:pistol");
		}
		// lanças
		for (String e : new String[]{"spearstand1", "spearstand3", "spearstrike1", "spearstrike2", "spearstrike3"}) {
			r(e, "prop:spear");
		}
		// lanternas
		for (String e : new String[]{"posewithlantern", "runwithlantern", "walkwithlantern"}) {
			r(e, "lantern");
		}
		// guitarra (vai na mão esquerda)
		for (String e : new String[]{"playtheguitarvigorously", "rhythmicguitarplaying", "sittingdowntoplaytheguitar", "tunetheguitar"}) {
			l(e, "prop:guitar");
		}
		// bengalas / muletas / vassoura
		r("walkingwithacane", "prop:cane");
		rl("walkingoncrutches", "prop:crutch", "prop:crutch");
		r("flyingonabroom", "prop:broom");
		l("witchpose", "prop:broom");
		r("flyingonthesword", "iron_sword");
		// outros
		r("cooldrinkwater", "prop:cup");
		r("hypnosis", "prop:watch");
		l("runningwithabriefcase", "prop:briefcase");
		r("togiveagift", "prop:gift");
		// skates: the board follows the rightItem track of the emote (under the feet)
		for (String e : new String[]{"skatejump", "skatepose", "skateride", "skateside1", "skateside2", "skatetrick1", "skatetrick2"}) {
			r(e, "prop:skateboard");
		}
	}

	private EmoteItems() {
	}

	/** Props in the hands during emotes are switched off (the emotes play without accessories). Set to true to bring them back. */
	public static final boolean ENABLED = false;

	public static String[] get(String emoteId) {
		return ENABLED ? MAP.get(emoteId) : null;
	}
}
