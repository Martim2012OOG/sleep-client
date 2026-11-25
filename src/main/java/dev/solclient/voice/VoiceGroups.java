package dev.solclient.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.solclient.Lang;
import dev.solclient.gui.Notify;
import dev.solclient.social.KuboLive;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * Voice groups (v99): talk with friends wherever each of you is playing (no distance). A group is a secret room id
 * plus a name and the members (Minecraft names, in the order you arranged them). Invites and changes go to the
 * friends through {@link KuboLive}; the voice itself goes through {@link VoiceNet}. Removing someone gives the group
 * a new secret id, so they can't come back in on their own.
 */
public final class VoiceGroups {

	public static final class Group {
		public String id, name, owner;
		public List<String> members = new ArrayList<>();
		/** Someone invited you and you haven't joined yet. */
		public boolean invite;
		public String invitedBy;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final SecureRandom RNG = new SecureRandom();
	private static List<Group> groups;
	private static String active;

	private VoiceGroups() {
	}

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("voice-groups.json");
	}

	private static String me() {
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	public static synchronized List<Group> all() {
		if (groups == null) {
			groups = new ArrayList<>();
			try {
				Path f = file();
				if (Files.exists(f)) {
					List<Group> l = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Group>>() {
					}.getType());
					if (l != null) {
						groups.addAll(l);
					}
				}
			} catch (Exception e) {
				System.err.println("[Caos] voice groups: " + e);
			}
		}
		return groups;
	}

	private static synchronized void save() {
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(all()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.err.println("[Caos] voice groups: " + e);
		}
	}

	public static Group get(String id) {
		for (Group g : all()) {
			if (g.id.equals(id)) {
				return g;
			}
		}
		return null;
	}

	/** The group you are talking in, or null (then voice is the server's, by distance). */
	public static String activeId() {
		return active != null && get(active) != null ? active : null;
	}

	public static String activeName() {
		Group g = active == null ? null : get(active);
		return g == null ? "" : g.name;
	}

	private static String newId() {
		String abc = "abcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 24; i++) {
			b.append(abc.charAt(RNG.nextInt(abc.length())));
		}
		return b.toString();
	}

	public static Group create(String name) {
		Group g = new Group();
		g.id = newId();
		g.name = name == null || name.isBlank() ? Lang.tr("My group", "O meu grupo") : name.strip();
		g.owner = me();
		g.members.add(me());
		all().add(0, g);
		save();
		return g;
	}

	public static boolean isOwner(Group g) {
		return g.owner != null && g.owner.equalsIgnoreCase(me());
	}

	public static boolean has(Group g, String name) {
		for (String m : g.members) {
			if (m.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Adds a friend and sends them the invite (and tells the others). */
	public static void invite(Group g, String friend) {
		if (friend == null || has(g, friend)) {
			return;
		}
		g.members.add(friend);
		if (!KuboLive.voiceGroup("vinvite", friend, g.id, null, g.name, g.members)) {
			// the live link is down (the EndLock Worker is old / offline): nothing reached them, so don't add them
			g.members.removeIf(m -> m.equalsIgnoreCase(friend));
			Notify.show(Notify.Kind.ERROR, Lang.tr("Invite not sent", "Convite não enviado"),
					Lang.tr("Live link is down: run Publicar.command again", "Ligação em direto em baixo: corre o Publicar.command outra vez"), friend);
			return;
		}
		save();
		tellOthers(g, null, friend);
		Notify.show(Notify.Kind.SENT, Lang.tr("Invite sent", "Convite enviado"), Lang.tr("To ", "Para ") + friend + " · " + g.name, friend);
	}

	/** Takes someone out (owner): new secret id for everybody else. */
	public static void remove(Group g, String name) {
		if (!has(g, name) || name.equalsIgnoreCase(me())) {
			return;
		}
		g.members.removeIf(m -> m.equalsIgnoreCase(name));
		String old = g.id;
		g.id = newId();
		if (old.equals(active)) {
			active = g.id;
		}
		save();
		tellOthers(g, old, null);
		KuboLive.voiceGroup("vgroup", name, g.id, old, g.name, g.members); // without them in it: it disappears from their list
	}

	private static void tellOthers(Group g, String old, String except) {
		for (String m : g.members) {
			if (!m.equalsIgnoreCase(me()) && (except == null || !m.equalsIgnoreCase(except))) {
				KuboLive.voiceGroup("vgroup", m, g.id, old, g.name, g.members);
			}
		}
	}

	public static void join(Group g) {
		g.invite = false;
		active = g.id;
		save();
	}

	/** Back to the server's voice (by distance). */
	public static void leaveCall() {
		active = null;
	}

	/** Leaves the group for good. */
	public static void leave(Group g) {
		for (String m : g.members) {
			if (!m.equalsIgnoreCase(me())) {
				KuboLive.voiceLeave(m, g.id);
			}
		}
		if (g.id.equals(active)) {
			active = null;
		}
		all().remove(g);
		save();
	}

	/** Drag and drop in the member list: only changes the order you see. */
	public static void move(Group g, int from, int to) {
		if (from < 0 || from >= g.members.size() || to < 0 || to >= g.members.size() || from == to) {
			return;
		}
		g.members.add(to, g.members.remove(from));
		save();
	}

	// ------------------------------------------------------------------ from friends (KuboLive, render thread)
	static boolean isFriend(String name) {
		for (var u : dev.solclient.social.Social.friends) {
			if (name != null && (name.equalsIgnoreCase(u.mcUsername) || name.equalsIgnoreCase(u.display()))) {
				return true;
			}
		}
		return false;
	}

	public static void onInvite(String from, String id, String name, List<String> members) {
		if (from == null || !isFriend(from)) {
			return; // only friends can invite you
		}
		Group g = get(id);
		if (g == null) {
			g = new Group();
			g.id = id;
			all().add(0, g);
		}
		g.name = name;
		g.owner = from;
		g.members = new ArrayList<>(members);
		g.invite = true;
		g.invitedBy = from;
		save();
		Notify.show(Notify.Kind.VOICE, from + Lang.tr(" invited you to talk", " convidou-te para falar"), name + Lang.tr("  ·  V to answer", "  ·  V para responder"), from);
	}

	public static void onUpdate(String from, String id, String old, String name, List<String> members) {
		Group g = get(old != null && !old.isEmpty() ? old : id);
		if (g == null) {
			g = get(id);
		}
		if (g == null || from == null || !has(g, from)) {
			return;
		}
		boolean stillIn = false;
		for (String m : members) {
			stillIn |= m.equalsIgnoreCase(me());
		}
		if (!stillIn) {
			if (g.id.equals(active)) {
				active = null;
			}
			all().remove(g);
			save();
			return;
		}
		if (g.id.equals(active)) {
			active = id;
		}
		g.id = id;
		g.name = name;
		// keep your own order, add the new people at the end, drop the ones that left
		List<String> next = new ArrayList<>();
		for (String m : g.members) {
			for (String n : members) {
				if (n.equalsIgnoreCase(m)) {
					next.add(m);
				}
			}
		}
		for (String n : members) {
			boolean known = false;
			for (String m : next) {
				known |= m.equalsIgnoreCase(n);
			}
			if (!known) {
				next.add(n);
			}
		}
		g.members = next;
		save();
	}

	public static void onLeft(String from, String id) {
		Group g = get(id);
		if (g == null || from == null) {
			return;
		}
		if (g.owner != null && g.owner.equalsIgnoreCase(from) && !isOwner(g) && g.members.size() <= 2) {
			// the owner left a two-person group: nothing left
			all().remove(g);
		} else {
			g.members.removeIf(m -> m.equalsIgnoreCase(from));
		}
		save();
	}

	public static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}
}
