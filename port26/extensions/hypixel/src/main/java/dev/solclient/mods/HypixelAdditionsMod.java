package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.multiplayer.ServerData;

/** Auto GG / Auto GL / Hide GG / Hide GL (patterns based on Sk1er's AutoGG triggers). */
public class HypixelAdditionsMod extends Mod {

	private static final List<Pattern> GG_TRIGGERS = Stream.of(
			"^ +1st Killer - ?\\[?\\w*\\+*\\]? \\w+ - \\d+(?: Kills?)?$",
			"^ *1st (?:Place ?)?(?:-|:)? ?\\[?\\w*\\+*\\]? \\w+(?: : \\d+| - \\d+(?: Points?)?| - \\d+(?: x .)?| \\(\\w+ .{1,6}\\) - \\d+ Kills?|: \\d+:\\d+| - \\d+ (?:Zombie )?(?:Kills?|Blocks? Destroyed)| - \\[LINK\\])?$",
			"^ +Winn(?:er #1 \\(\\d+ Kills\\): \\w+ \\(\\w+\\)|er(?::| - )(?:Hiders|Seekers|Defenders|Attackers|PLAYERS?|MURDERERS?|Red|Blue|RED|BLU|\\w+)(?: Team)?|ers?: ?\\[?\\w*\\+*\\]? \\w+(?:, ?\\[?\\w*\\+*\\]? \\w+)?|ing Team ?[\\:-] (?:Animals|Hunters|Red|Green|Blue|Yellow|RED|BLU|Survivors|Vampires))$",
			"^ +Alpha Infected: \\w+ \\(\\d+ infections?\\)$",
			"^ +Murderer: \\w+ \\(\\d+ Kills?\\)$",
			"^ +You survived \\d+ rounds!$",
			"^ +(((?:UHC|SkyWars|(The )?Bridge|Sumo|Classic|OP|MegaWalls|Bow|NoDebuff|Blitz|Combo|Bow Spleef|Boxing) (?:Duel|Doubles|Teams|Deathmatch|2v2v2v2|3v3v3v3)?)|Hypixel Parkour) ?- \\d+:\\d+$",
			"^ +They captured all wools!$",
			"^ +Game over!$",
			"^ +[\\d\\.]+k?/[\\d\\.]+k? \\w+$",
			"^ +(?:Criminal|Cop)s won the game!$",
			"^ +\\[?\\w*\\+*\\]? \\w+ - \\d+ Final Kills$",
			"^ +Zombies - \\d*:?\\d+:\\d+ \\(Round \\d+\\)$",
			"^ +. YOUR STATISTICS .$",
			"^ {36}Winner(s?)$",
			"^ {21}Bridge CTF [a-zA-Z]+ - \\d\\d:\\d\\d$"
	).map(Pattern::compile).toList();

	private static final List<Pattern> HIDE_GG = Stream.of(
			".*: (([gG]{2})|([gG]ood [gG]ame))",
			"\\+\\d* Karma!").map(Pattern::compile).toList();

	private static final Pattern HIDE_GL = Pattern.compile(".*: [gG](ood )?[lL](uck,? ?)?([hH](ave )?[fF](un)?!?)?");
	private static final String GL_TRIGGER = "The game starts in 1 second!";

	private final Option.Bool autogg = bool("Auto GG", true);
	private final Option.Choice autoggMessage = choice("Auto GG Message", 0, "gg", "gf", "good game", "good fight");
	private final Option.Bool hidegg = bool("Hide GG", false);
	private final Option.Bool autogl = bool("Auto GL", false);
	private final Option.Choice autoglMessage = choice("Auto GL Message", 0, "glhf", "gl", "good luck!",
			"good luck, have fun!");
	private final Option.Bool hidegl = bool("Hide GL", false);
	// v75: own GG text, Auto Who, Auto Tip and the Hypixel API key (Level Head / Stats Overlay)
	private final Option.Text customGg = text("Custom GG Message", "", 60, "empty = use the list above");
	private final Option.Bool autoWho = bool("Auto Who (list players at game start)", false);
	private final Option.Bool autoTip = bool("Auto Tip (every 20 min)", false);
	public final Option.Text apiKey = text("Hypixel API Key", "", 64, "developer.hypixel.net");
	private long lastTip;

	private static HypixelAdditionsMod instance;

	/** The Hypixel API key typed in this mod (used by Level Head and the Stats Overlay). */
	public static String key() {
		return instance == null ? "" : instance.apiKey.get().trim();
	}

	public static boolean onHypixel() {
		return instance != null && instance.isHypixel();
	}
	private boolean donegg, donegl;
	private int ticksUntilGl = -1;
	private Object lastWorld;

	public HypixelAdditionsMod() {
		super("Hypixel Additions", "hypixel_util", "Various improvements to Hypixel.", Category.UTILITY);
		instance = this;
		apiKey.secret = true;
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (instance == null || !instance.enabled || overlay || !instance.isHypixel()) {
				return true;
			}
			return instance.onMessage(message.getString());
		});
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private boolean isHypixel() {
		ServerData info = mc.getCurrentServer();
		return info != null && info.ip != null && info.ip.toLowerCase().contains("hypixel.net");
	}

	/** @return false to hide the message. */
	private boolean onMessage(String message) {
		if (autogg.on() && !donegg) {
			for (Pattern p : GG_TRIGGERS) {
				if (p.matcher(message).matches()) {
					donegg = true;
					String gg = customGg.get().trim();
					send("achat " + (gg.isEmpty() ? autoggMessage.label() : gg));
					return true;
				}
			}
		}
		if (autoWho.on() && message.equals(GL_TRIGGER)) {
			mc.execute(() -> send("who"));
		}
		if (autogl.on() && !donegl && message.equals(GL_TRIGGER)) {
			ticksUntilGl = 20;
			return true;
		}
		if (hidegg.on()) {
			for (Pattern p : HIDE_GG) {
				if (p.matcher(message).matches()) {
					return false;
				}
			}
		}
		if (hidegl.on() && HIDE_GL.matcher(message).matches()) {
			return false;
		}
		return true;
	}

	private void send(String command) {
		if (mc.player != null && mc.player.connection != null) {
			mc.player.connection.sendCommand(command);
		}
	}

	@Override
	public void tick() {
		if (mc.level != lastWorld) {
			lastWorld = mc.level;
			donegg = false;
			donegl = false;
		}
		long now = System.currentTimeMillis();
		if (autoTip.on() && isHypixel() && now - lastTip > 20 * 60_000L) {
			lastTip = now;
			send("tip all");
		}
		if (ticksUntilGl != -1 && --ticksUntilGl == 0) {
			send("achat " + autoglMessage.label());
			donegl = true;
			ticksUntilGl = -1;
		}
	}
}
