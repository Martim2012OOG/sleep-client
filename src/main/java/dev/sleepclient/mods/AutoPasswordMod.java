package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Auto Password: servers with a login plugin (AuthMe...) ask "/register password password" or "/login password" every time you join.
 * You save your password once; when the server asks, this sends the right command for you.
 *
 * Safety: it only reacts to messages the SERVER sends (never to other players' chat), only in multiplayer, only one command at a
 * time with a pause, at most 4 per join, and it stops for the rest of that join when the server says the password is wrong
 * (so a wrong password cannot get you banned for spamming). The password is saved on this computer only (never in shared
 * profiles or cloud sync) and is never written to the chat or the log.
 */
public class AutoPasswordMod extends Mod {

	public static AutoPasswordMod instance;

	private final Option.Text password = text("Password", "", 64, "your server password");
	private final Option.Choice registerFormat = choice("Register Command", 0, "Detect from the server", "Password once", "Password twice");
	private final Option.Num delay = num("Delay Before Sending", 0, 0, 4000, 50, " ms");
	private final Option.Bool login = bool("Auto Login", true);
	private final Option.Bool register = bool("Auto Register", true);
	private final Option.Text perServer = text("Passwords Per Server", "", 200, "play.example.net=pass1; other.net=pass2");
	private final Option.Text onlyServers = text("Only On These Servers", "", 120, "empty = every server (play.example.net, other.net)");
	private final Option.Bool notify = bool("Tell Me When Used", true);

	private static boolean hooked;
	private Object lastWorld;
	private int sent;
	private boolean done, blocked;
	private long lastSendAt, dueAt;
	private String pendingCmd = "", pendingLabel = "";
	private String status = "Waiting for a server that asks for a password";

	private static final Pattern LOGIN = Pattern.compile("/(login|log|l|logar|entrar)(?![a-z0-9_])", Pattern.CASE_INSENSITIVE);
	private static final Pattern REGISTER = Pattern.compile("/(register|reg|registar|registo|registrar)(?![a-z0-9_])", Pattern.CASE_INSENSITIVE);
	private static final Pattern HINT_LOGIN = Pattern.compile("(please|you must|you need to|to continue|faz|faça|tens de|precisas de)[^.!\\n]{0,30}\\blog ?in\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HINT_REGISTER = Pattern.compile("(please|you must|you need to|to continue|faz|faça|tens de|precisas de)[^.!\\n]{0,30}\\bregist", Pattern.CASE_INSENSITIVE);
	private static final Pattern OK = Pattern.compile("(successful(ly)? (log|login|regist)|you (have|are|were) (now |been )?(successfully )?(logged ?in|registered)|logged in|login success|registered successfully"
			+ "|bem[- ]vindo de volta|autenticad|sessão iniciada|sessao iniciada|registo (efetuado|concluído|com sucesso)|registado com sucesso)", Pattern.CASE_INSENSITIVE);
	private static final Pattern BAD = Pattern.compile("((wrong|incorrect|invalid|bad) (password|pass)|password (is )?(wrong|incorrect|invalid)|senha (incorreta|errada|inválida)"
			+ "|palavra[- ]passe (incorreta|errada)|too many (failed|login|attempts)|demasiadas tentativas)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ARGS_WORD = Pattern.compile("^\\s+(password|pass|senha|palavra|confirm\\w*|repeat\\w*|again|repetir\\w*)", Pattern.CASE_INSENSITIVE);

	public AutoPasswordMod() {
		super("Auto Password", "auto_password", "Saves your server password and sends /login or /register for you when a server asks for it.", Category.UTILITY);
		instance = this;
		password.secret = true;
		perServer.secret = true;
		action("Auto Password Status", "Reset", () -> {
			sent = 0;
			done = false;
			blocked = false;
			status = "Ready again for this server";
		}, () -> status);
		register.tip("When the server says you are not registered, creates the account with your password.");
		perServer.tip("Different password on some servers: address=password, separated by ;");
		onlyServers.tip("Leave empty to use it on every multiplayer server.");
		registerFormat.tip("Most servers say how many times to type the password. This is used when they do not.");
	}

	@Override
	protected void onEnable() {
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				if (enabled) {
					onServerMessage(message.getString());
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Auto Password: no chat events: " + t);
			status = "Chat events are not available";
		}
	}

	@Override
	public void tick() {
		if (mc.world != lastWorld) {
			lastWorld = mc.world; // new world / server: start again
			sent = 0;
			done = false;
			blocked = false;
			pendingCmd = "";
		}
		if (pendingCmd.isEmpty() || System.currentTimeMillis() < dueAt || mc.player == null || mc.getNetworkHandler() == null) {
			return;
		}
		String cmd = pendingCmd;
		String label = pendingLabel;
		pendingCmd = "";
		if (done || blocked || sent >= 4) {
			return;
		}
		sent++;
		lastSendAt = System.currentTimeMillis();
		mc.getNetworkHandler().sendChatCommand(cmd);
		status = "Sent /" + label + " (" + sent + " of 4 this join)";
		if (notify.on()) {
			dev.sleepclient.social.Social.say("Auto Password: /" + label + " sent", false);
		}
	}

	// ------------------------------------------------------------------ reading what the server says

	/** A message from the server (not from a player). */
	private void onServerMessage(String raw) {
		if (raw == null || raw.length() < 4 || raw.length() > 600 || mc.player == null || mc.isInSingleplayer() || mc.getCurrentServerEntry() == null) {
			return;
		}
		if (looksLikePlayerChat(raw)) {
			return;
		}
		if (!serverAllowed()) {
			return;
		}
		if (OK.matcher(raw).find() && !LOGIN.matcher(raw).find() && !REGISTER.matcher(raw).find()) {
			done = true;
			pendingCmd = "";
			status = "Logged in (nothing more to send)";
			return;
		}
		if (BAD.matcher(raw).find()) {
			if (sent > 0) {
				blocked = true;
				pendingCmd = "";
				status = "The server says the password is wrong: stopped for this join";
				dev.sleepclient.social.Social.say("Auto Password stopped: the server says the password is wrong", true);
			}
			return;
		}
		if (done || blocked || sent >= 4 || !pendingCmd.isEmpty() || System.currentTimeMillis() - lastSendAt < 3500L) {
			return;
		}
		String pw = passwordForServer();
		if (pw.isEmpty()) {
			status = "The server asks for a password but none is saved";
			return;
		}
		int kind = detect(raw);
		if (kind == 0) {
			return;
		}
		String cmd, label;
		if (kind == 1) {
			if (!login.on()) {
				return;
			}
			label = commandWord(raw, LOGIN, "login");
			cmd = label + " " + pw;
		} else {
			if (!register.on()) {
				return;
			}
			label = commandWord(raw, REGISTER, "register");
			cmd = label + " " + pw + (twice(raw) ? " " + pw : "");
		}
		pendingCmd = cmd;
		pendingLabel = label; // the label never contains the password
		dueAt = System.currentTimeMillis() + Math.round(delay.f());
	}

	/** 0 nothing, 1 the server wants /login, 2 it wants /register. */
	private static int detect(String raw) {
		boolean login = LOGIN.matcher(raw).find();
		boolean reg = REGISTER.matcher(raw).find();
		if (!login && !reg) {
			login = HINT_LOGIN.matcher(raw).find();
			reg = HINT_REGISTER.matcher(raw).find();
		}
		if (login) {
			return 1; // both named: try the login first; if the server says "not registered" it asks for /register next
		}
		return reg ? 2 : 0;
	}

	/** The command word the server used ("login", "l", "reg"...), so aliases keep working. */
	private static String commandWord(String raw, Pattern p, String def) {
		Matcher m = p.matcher(raw);
		return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : def;
	}

	/** Does /register want the password twice? Reads the usage in the message ("<password> <confirmPassword>"), else the setting. */
	private boolean twice(String raw) {
		Matcher m = REGISTER.matcher(raw);
		if (m.find()) {
			String rest = raw.substring(m.end());
			int brackets = 0;
			Matcher b = Pattern.compile("^\\s*(?:[<\\[({][^>\\])}]*[>\\])}])").matcher(rest);
			int pos = 0;
			while (b.region(pos, rest.length()).find() && brackets < 4) {
				brackets++;
				pos = b.end();
			}
			if (brackets >= 2) {
				return true;
			}
			if (brackets == 1) {
				return false;
			}
			int words = 0;
			pos = 0;
			Matcher w = ARGS_WORD.matcher(rest);
			while (words < 3 && w.region(pos, rest.length()).find()) {
				words++;
				pos = w.end();
				Matcher skip = Pattern.compile("^\\w*").matcher(rest);
				if (skip.region(pos, rest.length()).find()) {
					pos = skip.end();
				}
			}
			if (words >= 2) {
				return true;
			}
			if (words == 1) {
				return false;
			}
		}
		return registerFormat.idx() != 1;
	}

	/** "Name: message" / "<Name> message" with an online player's name at the front: that is chat, not the server. */
	private boolean looksLikePlayerChat(String raw) {
		int cut = -1;
		for (int i = 0; i < Math.min(raw.length(), 48); i++) {
			char c = raw.charAt(i);
			if (c == ':' || c == '»' || c == '>') {
				cut = i;
				break;
			}
		}
		if (cut < 0 || mc.getNetworkHandler() == null) {
			return false;
		}
		String head = raw.substring(0, cut).toLowerCase(Locale.ROOT);
		for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
			String n = dev.sleepclient.social.SolarBadge.nameOf(e.getProfile());
			if (n != null && n.length() > 2 && head.contains(n.toLowerCase(Locale.ROOT)) && !n.equalsIgnoreCase(mc.getSession().getUsername())) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ servers and passwords

	private String serverHost() {
		var e = mc.getCurrentServerEntry();
		if (e == null || e.address == null) {
			return "";
		}
		String a = e.address.trim().toLowerCase(Locale.ROOT);
		int c = a.lastIndexOf(':');
		if (c > 0 && a.indexOf(':') == c) {
			a = a.substring(0, c);
		}
		return a.endsWith(".") ? a.substring(0, a.length() - 1) : a;
	}

	private static boolean hostMatches(String host, String entry) {
		String e = entry.trim().toLowerCase(Locale.ROOT);
		int c = e.lastIndexOf(':');
		if (c > 0 && e.indexOf(':') == c) {
			e = e.substring(0, c);
		}
		return !e.isEmpty() && (host.equals(e) || host.endsWith("." + e));
	}

	private boolean serverAllowed() {
		String list = onlyServers.get().trim();
		if (list.isEmpty()) {
			return true;
		}
		String host = serverHost();
		for (String s : list.split("[,; ]+")) {
			if (hostMatches(host, s)) {
				return true;
			}
		}
		return false;
	}

	private String passwordForServer() {
		String host = serverHost();
		List<String[]> pairs = new ArrayList<>();
		for (String part : perServer.get().split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				pairs.add(new String[] {part.substring(0, eq), part.substring(eq + 1).trim()});
			}
		}
		for (String[] p : pairs) {
			if (hostMatches(host, p[0]) && !p[1].isEmpty()) {
				return p[1];
			}
		}
		return password.get().trim();
	}
}
