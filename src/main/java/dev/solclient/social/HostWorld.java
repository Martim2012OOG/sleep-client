package dev.solclient.social;

import dev.solclient.Lang;
import dev.solclient.mods.HostWorldMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.NetworkUtils;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

/**
 * Host World: opens the singleplayer world (vanilla "Open to LAN"), opens the port on the router with UPnP so friends
 * from other houses can join, and keeps out anyone who is not allowed (Invite Only / Friends Only / Public) or when
 * the world is full. Invites go through the Sleep chat as a card with a Join button.
 */
public final class HostWorld {

	private static volatile boolean hosting, closed;
	private static volatile int port;
	private static volatile String publicIp, localIp, status = "";
	private static volatile boolean routerOk;
	private static Upnp.Gateway gateway;
	private static final Set<String> invited = java.util.Collections.synchronizedSet(new HashSet<>());
	private static int tick;
	private static boolean autoHostedThisWorld;

	private HostWorld() {
	}

	public static boolean hosting() {
		return hosting;
	}

	public static boolean canHost() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.isInSingleplayer() && mc.getServer() != null && mc.player != null;
	}

	public static String status() {
		return status;
	}

	public static int port() {
		return port;
	}

	public static boolean routerOk() {
		return routerOk;
	}

	/** Address friends on the internet use (public IP:port), or the home-network one while that is unknown. */
	public static String address() {
		String r = KuboRelay.hostAddress();
		if (r != null && (KuboRelay.hostConnected() || !routerOk)) {
			// works from any house, for friends with Sleep. Even while the relay is still connecting this is the address
			// to give: the home-network one (192.168...) only works on your own Wi-Fi and left friends "connecting" forever
			return r;
		}
		return ipAddress();
	}

	/** Plain IP address (also for friends without Sleep, when the router port is open). */
	public static String ipAddress() {
		if (publicIp != null && routerOk) {
			return publicIp + ":" + port;
		}
		return localAddress();
	}

	public static String localAddress() {
		return (localIp != null ? localIp : "localhost") + ":" + port;
	}

	public static String worldName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			IntegratedServer s = mc.getServer();
			if (s != null) {
				return s.getSaveProperties().getLevelName();
			}
		} catch (RuntimeException ignored) {
		}
		return "World";
	}

	public static int playerCount() {
		IntegratedServer s = MinecraftClient.getInstance().getServer();
		return s == null ? 0 : s.getPlayerManager().getPlayerList().size();
	}

	public static String statusLine() {
		if (!hosting) {
			return canHost() ? Lang.tr("Not hosting", "Não estás a hospedar") : Lang.tr("Open a singleplayer world first", "Abre primeiro um mundo singleplayer");
		}
		return Lang.tr("Hosting on ", "A hospedar em ") + address() + " · " + playerCount() + "/" + Math.round(HostWorldMod.instance.slots.f());
	}

	// ------------------------------------------------------------------ start / stop
	public static boolean host() {
		MinecraftClient mc = MinecraftClient.getInstance();
		IntegratedServer server = mc.getServer();
		if (!canHost() || server == null) {
			status = Lang.tr("Open a singleplayer world first", "Abre primeiro um mundo singleplayer");
			return false;
		}
		HostWorldMod o = HostWorldMod.instance;
		if (hosting) {
			applyLive();
			return true;
		}
		GameMode gm = switch (o.gameMode.idx()) {
			case 1 -> GameMode.CREATIVE;
			case 2 -> GameMode.ADVENTURE;
			case 3 -> GameMode.SPECTATOR;
			default -> GameMode.SURVIVAL;
		};
		int p = parsePort(o.port.get());
		boolean ok;
		if (server.isRemote()) {
			ok = true; // already open to LAN (vanilla button): just take over
			p = server.getServerPort();
		} else {
			ok = server.openToLan(gm, o.cheats.on(), p);
		}
		if (!ok) {
			status = Lang.tr("Could not open the world on port ", "Não deu para abrir o mundo na porta ") + p;
			return false;
		}
		port = p;
		hosting = true;
		closed = false;
		routerOk = false;
		publicIp = null;
		applyLive();
		status = Lang.tr("Hosting! Connecting...", "A hospedar! A ligar...");
		if (o.anywhere.on()) {
			KuboRelay.startHost(p, mc.getSession().getUsername());
		}
		final int fp = p;
		Social.IO.execute(() -> openRouter(fp));
		return true;
	}

	/** Things that can change while already hosting. */
	private static void applyLive() {
		IntegratedServer server = MinecraftClient.getInstance().getServer();
		if (server == null) {
			return;
		}
		// non-original accounts: a hosted world checks every login with Mojang, and those friends drop out while joining
		try {
			server.setOnlineMode(!HostWorldMod.instance.nonPremium.on());
		} catch (Throwable ignored) {
			// keep Minecraft's default
		}
		Difficulty d = switch (HostWorldMod.instance.difficulty.idx()) {
			case 0 -> Difficulty.PEACEFUL;
			case 1 -> Difficulty.EASY;
			case 3 -> Difficulty.HARD;
			default -> Difficulty.NORMAL;
		};
		server.execute(() -> {
			try {
				server.setDifficulty(d, true);
			} catch (RuntimeException ignored) {
			}
		});
		postActivity();
	}

	/** The relay connected / dropped: update the status line and what friends see. */
	static void relayChanged() {
		if (!hosting) {
			return;
		}
		postActivity();
	}

	/** The line shown in the Host World screen. */
	public static String fullStatus() {
		if (!hosting) {
			return status;
		}
		if (KuboRelay.hostAddress() != null) {
			if (KuboRelay.hostConnected()) {
				return Lang.tr("Ready! Friends with Sleep can join from anywhere.", "Pronto! Amigos com Sleep podem entrar de qualquer lado.")
						+ (routerOk ? Lang.tr(" Others: use the IP.", " Outros: usem o IP.") : "");
			}
			String err = KuboRelay.hostError();
			return Lang.tr("Connecting to the Sleep relay...", "A ligar ao relay do Sleep...") + (err != null ? " (" + err + ")" : "");
		}
		return status;
	}

	private static void postActivity() {
		if (!hosting) {
			return;
		}
		int pr = HostWorldMod.instance.privacy.idx();
		// "Playing on <address>" shows the Join button to your friends (not for Invite Only: only invited people get it)
		ServerShare.postActivity(pr == 0 ? "Hosting " + worldName() : "Playing on " + address());
	}

	private static int parsePort(String s) {
		try {
			int p = Integer.parseInt(s.trim());
			if (p > 1024 && p < 65536) {
				return p;
			}
		} catch (RuntimeException ignored) {
		}
		return NetworkUtils.findLocalPort();
	}

	private static void openRouter(int p) {
		try {
			localIp = null;
			if (HostWorldMod.instance.openRouter.on()) {
				Upnp.Gateway g = Upnp.discover(3500);
				if (g != null) {
					localIp = g.localAddress.getHostAddress();
					if (Upnp.addMapping(g, p, "Sleep Host World")) {
						gateway = g;
						String ext = Upnp.externalIp(g);
						if (ext == null) {
							ext = webIp();
						}
						publicIp = ext;
						if (Upnp.isPrivate(ext)) {
							status = Lang.tr("Your internet provider blocks hosting (CGNAT). Friends on your Wi-Fi can still join.",
									"A tua operadora bloqueia hospedar (CGNAT). Amigos na tua rede ainda podem entrar.");
						} else {
							routerOk = true;
							status = Lang.tr("Ready! Friends can join from anywhere.", "Pronto! Os amigos podem entrar de qualquer lado.");
						}
						postActivity();
						return;
					}
				}
			}
			if (localIp == null) {
				try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
					s.connect(java.net.InetAddress.getByName("8.8.8.8"), 53);
					localIp = s.getLocalAddress().getHostAddress();
				}
			}
			publicIp = webIp();
			status = HostWorldMod.instance.openRouter.on()
					? Lang.tr("The router didn't open the port (UPnP off?). Friends on your Wi-Fi can join; for others open port " + p + " on the router.",
							"O router não abriu a porta (UPnP desligado?). Amigos na tua rede podem entrar; para outros abre a porta " + p + " no router.")
					: Lang.tr("Hosting on your network. Open port " + p + " on the router for friends outside.",
							"A hospedar na tua rede. Abre a porta " + p + " no router para amigos de fora.");
			postActivity();
		} catch (Exception e) {
			status = Lang.tr("Hosting on your network only: ", "A hospedar só na tua rede: ") + e.getMessage();
		}
	}

	private static String webIp() {
		try {
			HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
			HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create("https://api.ipify.org")).timeout(Duration.ofSeconds(5)).build(),
					HttpResponse.BodyHandlers.ofString());
			String ip = r.body().trim();
			return ip.matches("[0-9a-fA-F.:]{3,45}") ? ip : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Stops letting people in, closes the router port and sends the guests back (the world itself keeps running). */
	public static void stop() {
		if (!hosting) {
			return;
		}
		hosting = false;
		closed = true;
		KuboRelay.stopHost();
		status = Lang.tr("Stopped hosting", "Deixaste de hospedar");
		Upnp.Gateway g = gateway;
		int p = port;
		gateway = null;
		routerOk = false;
		if (g != null) {
			Social.IO.execute(() -> Upnp.removeMapping(g, p));
		}
		ServerShare.postActivity("Playing singleplayer");
		enforceNow();
	}

	// ------------------------------------------------------------------ whitelist / bans
	public static List<String> list(dev.solclient.option.Option.Text t) {
		List<String> out = new java.util.ArrayList<>();
		for (String n : t.get().split(",")) {
			n = n.trim();
			if (n.matches("[A-Za-z0-9_]{3,16}") && out.stream().noneMatch(n::equalsIgnoreCase)) {
				out.add(n);
			}
		}
		return out;
	}

	private static void put(dev.solclient.option.Option.Text t, List<String> l) {
		t.set(String.join(",", l));
		dev.solclient.Config.save();
	}

	public static boolean onList(dev.solclient.option.Option.Text t, String name) {
		return name != null && list(t).stream().anyMatch(name::equalsIgnoreCase);
	}

	public static void addTo(dev.solclient.option.Option.Text t, String name) {
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			return;
		}
		List<String> l = list(t);
		if (l.stream().noneMatch(name::equalsIgnoreCase)) {
			l.add(name);
			put(t, l);
		}
	}

	public static void removeFrom(dev.solclient.option.Option.Text t, String name) {
		List<String> l = list(t);
		l.removeIf(name::equalsIgnoreCase);
		put(t, l);
	}

	/** Names of the guests in the world right now (not you). */
	public static List<String> guests() {
		MinecraftClient mc = MinecraftClient.getInstance();
		IntegratedServer s = mc.getServer();
		List<String> out = new java.util.ArrayList<>();
		if (s == null || mc.player == null) {
			return out;
		}
		UUID me = mc.player.getUuid();
		try {
			for (ServerPlayerEntity p : List.copyOf(s.getPlayerManager().getPlayerList())) {
				if (!p.getUuid().equals(me)) {
					out.add(p.getName().getString());
				}
			}
		} catch (RuntimeException ignored) {
		}
		return out;
	}

	public static void kick(String name, String why) {
		IntegratedServer s = MinecraftClient.getInstance().getServer();
		if (s == null) {
			return;
		}
		s.execute(() -> {
			ServerPlayerEntity p = s.getPlayerManager().getPlayer(name);
			if (p != null) {
				p.networkHandler.disconnect(Text.literal(why));
			}
		});
	}

	public static void ban(String name) {
		addTo(HostWorldMod.instance.banned, name);
		removeFrom(HostWorldMod.instance.whitelist, name);
		invited.remove(name.toLowerCase(Locale.ROOT));
		kick(name, "You were banned from this world.");
	}

	/** What the page on host.endlock.net/<name> shows. */
	static String infoJson() {
		HostWorldMod o = HostWorldMod.instance;
		String w = worldName().replace("\\", "").replace("\"", "'");
		return "{\"t\":\"info\",\"world\":\"" + w + "\",\"players\":" + Math.max(1, playerCount()) + ",\"slots\":" + Math.round(o.slots.f())
				+ ",\"privacy\":\"" + o.privacy.label() + "\"}";
	}

	// ------------------------------------------------------------------ invites
	public static boolean invited(String mcName) {
		return mcName != null && (invited.contains(mcName.toLowerCase(Locale.ROOT)) || onList(HostWorldMod.instance.whitelist, mcName));
	}

	public static void invite(Social.User u) {
		if (!hosting || u == null) {
			return;
		}
		if (u.mcUsername != null) {
			invited.add(u.mcUsername.toLowerCase(Locale.ROOT));
			addTo(HostWorldMod.instance.whitelist, u.mcUsername);
			removeFrom(HostWorldMod.instance.banned, u.mcUsername);
		}
		Social.sendInviteTo(u.id, worldName(), address());
	}

	// ------------------------------------------------------------------ every tick
	public static void tick(MinecraftClient mc) {
		if (mc.getServer() == null || mc.world == null) {
			if (hosting || closed) {
				Upnp.Gateway g = gateway;
				int p = port;
				if (g != null) {
					Social.IO.execute(() -> Upnp.removeMapping(g, p));
				}
				gateway = null;
				KuboRelay.stopHost();
				hosting = false;
				closed = false;
				routerOk = false;
				invited.clear();
				status = "";
			}
			autoHostedThisWorld = false;
			return;
		}
		if (!hosting && !autoHostedThisWorld && mc.player != null && HostWorldMod.on() && HostWorldMod.instance.autoHost.on()) {
			autoHostedThisWorld = true;
			host();
		}
		if ((hosting || closed) && ++tick % 20 == 0) {
			enforceNow();
		}
	}

	private static void enforceNow() {
		MinecraftClient mc = MinecraftClient.getInstance();
		IntegratedServer server = mc.getServer();
		if (server == null || mc.player == null) {
			return;
		}
		UUID host = mc.player.getUuid();
		int pr = HostWorldMod.instance.privacy.idx();
		int slots = Math.round(HostWorldMod.instance.slots.f());
		Set<String> friends = new HashSet<>();
		for (Social.User u : Social.friends) {
			if (u.mcUsername != null) {
				friends.add(u.mcUsername.toLowerCase(Locale.ROOT));
			}
		}
		Set<String> white = new HashSet<>(), bans = new HashSet<>();
		for (String n : list(HostWorldMod.instance.whitelist)) {
			white.add(n.toLowerCase(Locale.ROOT));
		}
		for (String n : list(HostWorldMod.instance.banned)) {
			bans.add(n.toLowerCase(Locale.ROOT));
		}
		boolean shut = closed;
		server.execute(() -> {
			List<ServerPlayerEntity> list = List.copyOf(server.getPlayerManager().getPlayerList());
			int count = 0;
			for (ServerPlayerEntity sp : list) {
				if (sp.getUuid().equals(host)) {
					count++;
					continue;
				}
				String n = sp.getName().getString().toLowerCase(Locale.ROOT);
				boolean listed = invited.contains(n) || white.contains(n);
				boolean allowed = !shut && !bans.contains(n) && (pr == 2 || listed || pr == 1 && friends.contains(n));
				if (!allowed) {
					sp.networkHandler.disconnect(Text.literal(shut ? "The host stopped hosting this world."
							: bans.contains(n) ? "You are banned from this world."
							: pr == 1 ? "This world is for the host's Sleep friends only." : "You are not on this world's whitelist. Ask the host to add you!"));
					continue;
				}
				if (++count > slots) {
					sp.networkHandler.disconnect(Text.literal("This world is full (" + slots + " players)."));
				}
			}
		});
	}
}
