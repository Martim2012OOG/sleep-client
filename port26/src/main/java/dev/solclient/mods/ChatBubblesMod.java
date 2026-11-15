package dev.solclient.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** What players say in chat appears in a speech bubble above their head. */
public class ChatBubblesMod extends Mod {

	public static ChatBubblesMod instance;

	public final Option.Choice style = choice("Style", 0, "Classic", "Dark", "Sleep");
	public final Option.Num duration = num("Duration", 6, 2, 15, 1, "s");
	public final Option.Num maxBubbles = num("Bubbles Per Player", 3, 1, 5, 1, "");
	public final Option.Num maxWidth = num("Max Width", 150, 40, 480, 10, "px");
	public final Option.Num range = num("Range", 32, 8, 64, 4, " blocks");
	public final Option.Num size = num("Size", 100, 20, 300, 5, "%");
	public final Option.Bool own = bool("Show My Bubbles", true);
	public final Option.Bool serverFormats = bool("Read Server Chat Formats", true);
	public final Option.Bool throughWalls = bool("Show Through Walls", false);

	public static final class Bubble {
		final String text;
		final long born;
		final long life;
		List<FormattedCharSequence> lines;
		int wrapW = -1, width;

		Bubble(String text, long life) {
			this.text = text;
			this.born = System.currentTimeMillis();
			this.life = life;
		}
	}

	private static final Map<UUID, List<Bubble>> BUBBLES = new HashMap<>();

	private static final Pattern VANILLA = Pattern.compile("^<(?:\\[[^\\]]{0,24}\\] ?)*([A-Za-z0-9_]{2,16})> (.+)$");
	private static final Pattern RANKED = Pattern.compile(
			"^(?:\\[[^\\]]{0,24}\\] ?){0,3}(?:[^\\w\\s:<>\\[\\]]{1,3} ?)?([A-Za-z0-9_]{2,16})(?: ?\\[[^\\]]{0,24}\\]){0,2} ?(?::|»|>|\\|) (.+)$");

	public ChatBubblesMod() {
		super("Chat Bubbles", "chat_bubbles", "Shows what players say in a bubble above their head.", Category.VISUAL);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	@Override
	protected void onDisable() {
		BUBBLES.clear();
	}

	// ------------------------------------------------------------------ input

	/** A signed player chat message. */
	public static void onPlayerChat(UUID sender, String text) {
		if (on() && sender != null) {
			instance.add(sender, text);
		}
	}

	/** A system message: servers like Hypixel format player chat themselves ("[MVP+] Name: hi"). */
	public static void onGameMessage(String raw) {
		if (!on() || !instance.serverFormats.on() || raw == null || raw.length() > 400) {
			return;
		}
		String s = ChatFormatting.stripFormatting(raw);
		if (s == null) {
			return;
		}
		s = s.trim();
		Matcher m = VANILLA.matcher(s);
		if (!m.matches()) {
			m = RANKED.matcher(s);
			if (!m.matches() || m.start(2) > 70) {
				return;
			}
		}
		UUID id = instance.idByName(m.group(1));
		if (id != null) {
			instance.add(id, m.group(2));
		}
	}

	/**
	 * Every line that is added to the chat window (signed chat, system messages AND the unsigned / "profileless" chat
	 * that cracked and plugin-formatted servers use). The same message can arrive through more than one hook, so
	 * add() ignores a repeat of the same text within a moment.
	 */
	private static final Pattern TIME = Pattern.compile("^\\[\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?\\]\\s*");

	public static void onChatLine(String raw) {
		if (!on() || !instance.serverFormats.on() || raw == null || raw.length() > 400) {
			return;
		}
		String s = ChatFormatting.stripFormatting(raw);
		if (s == null) {
			return;
		}
		s = TIME.matcher(s.trim()).replaceFirst("").trim();
		Matcher m = VANILLA.matcher(s);
		if (!m.matches()) {
			m = RANKED.matcher(s);
			if (!m.matches() || m.start(2) > 70) {
				return;
			}
		}
		UUID id = instance.idByName(m.group(1));
		if (id != null) {
			instance.add(id, m.group(2));
		}
	}

	private UUID idByName(String name) {
		if (mc.getConnection() == null) {
			return null;
		}
		String low = name.toLowerCase(Locale.ROOT);
		if (mc.level != null) {
			for (Player p : mc.level.players()) {
				if (p.getName().getString().toLowerCase(Locale.ROOT).equals(low)) {
					return p.getUUID();
				}
			}
		}
		for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
			String n = profileName(e.getProfile());
			if (n != null && n.toLowerCase(Locale.ROOT).equals(low)) {
				return dev.solclient.social.SolarBadge.idOf(e.getProfile());
			}
		}
		return null;
	}

	private static java.lang.reflect.Method nameMethod;

	private static String profileName(Object profile) {
		try {
			if (nameMethod == null) {
				try {
					nameMethod = profile.getClass().getMethod("name");
				} catch (NoSuchMethodException e) {
					nameMethod = profile.getClass().getMethod("getName");
				}
			}
			Object v = nameMethod.invoke(profile);
			return v instanceof String s ? s : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private void add(UUID id, String text) {
		if (text == null) {
			return;
		}
		text = ChatFormatting.stripFormatting(text);
		if (text == null) {
			return;
		}
		text = text.replaceAll("\\s+", " ").trim();
		if (text.isEmpty() || text.startsWith("/")) {
			return;
		}
		if (text.length() > 160) {
			text = text.substring(0, 157) + "...";
		}
		long life = (long) (duration.f() * 1000) + Math.min(5000, text.length() * 45L);
		List<Bubble> list = BUBBLES.computeIfAbsent(id, k -> new ArrayList<>());
		if (!list.isEmpty()) {
			Bubble last = list.get(list.size() - 1);
			if (last.text.equals(text) && System.currentTimeMillis() - last.born < 1500L) {
				return; // same message that came through another hook
			}
		}
		list.add(new Bubble(text, life));
		while (list.size() > maxBubbles.i()) {
			list.remove(0);
		}
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, List<Bubble>>> it = BUBBLES.entrySet().iterator();
		while (it.hasNext()) {
			List<Bubble> l = it.next().getValue();
			l.removeIf(b -> now - b.born > b.life);
			if (l.isEmpty()) {
				it.remove();
			}
		}
		if (mc.level == null) {
			BUBBLES.clear();
		}
	}

	/** Bubbles of an entity for the frame being built (null = nothing to draw). */
	public static List<Bubble> forEntity(Entity e) {
		if (!on() || !(e instanceof Player) || BUBBLES.isEmpty()) {
			return null;
		}
		if (e == instance.mc.player && !instance.own.on()) {
			return null;
		}
		List<Bubble> l = BUBBLES.get(e.getUUID());
		return l == null || l.isEmpty() ? null : l;
	}

	// ------------------------------------------------------------------ rendering

	private int[] colours() {
		return switch (style.idx()) {
			case 1 -> new int[] {0xB0000000, 0, 0xFFFFFFFF};
			case 2 -> new int[] {0xF0323849, 0xFF0A0B10, 0xFFFFFFFF};
			default -> new int[] {0xFFEFE6D2, 0xFF151515, 0xFF1E1E1E};
		};
	}

	/** Draws the bubbles of one entity. The matrix stack is at the entity's feet. */
	public static void render(List<Bubble> bubbles, EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue,
			CameraRenderState camera) {
		if (instance == null || bubbles == null || camera == null || state.isInvisible) {
			return;
		}
		double r = instance.range.f();
		if (state.distanceToCameraSq > r * r) {
			return;
		}
		Font tr = instance.mc.font;
		int maxW = instance.maxWidth.i();
		long now = System.currentTimeMillis();
		int[] col = instance.colours();
		Vec3 base = state.nameTagAttachment != null ? state.nameTagAttachment.add(0, 0.42, 0) : new Vec3(0, state.boundingBoxHeight + 0.55, 0);
		float s = 0.025F * instance.size.f() / 100F;

		matrices.pushPose();
		matrices.translate(base.x, base.y, base.z);
		matrices.mulPose(camera.orientation);
		matrices.scale(s, -s, s);

		int light = instance.throughWalls.on() ? 0xF000F0 : state.lightCoords;
		var layerType = instance.throughWalls.on() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
		var bgLayer = instance.throughWalls.on() ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.textBackground();

		float y = 0; // bottom of the stack, growing upwards (negative y)
		for (int i = bubbles.size() - 1; i >= 0; i--) {
			Bubble b = bubbles.get(i);
			if (b.wrapW != maxW || b.lines == null) {
				b.lines = tr.split(FormattedText.of(b.text), maxW);
				if (b.lines.size() > 4) {
					b.lines = new ArrayList<>(b.lines.subList(0, 4));
				}
				b.width = 0;
				for (FormattedCharSequence t : b.lines) {
					b.width = Math.max(b.width, tr.width(t));
				}
				b.wrapW = maxW;
			}
			long age = now - b.born, left = b.life - age;
			float a = Math.min(1F, Math.min(age / 150F, left / 400F));
			if (a <= 0.02F) {
				continue;
			}
			boolean tail = i == bubbles.size() - 1;
			int padX = 4, padY = 3;
			float w = b.width + padX * 2, h = b.lines.size() * 9 + padY * 2 - 1;
			float tailH = tail ? 4 : 0;
			float bottom = y - tailH;
			float top = bottom - h;
			float left2 = -w / 2;
			int fill = mul(col[0], a), edge = mul(col[1], a), text = mul(col[2], a);
			final float fx = left2, fy = top, fw = w, fh = h;
			final boolean hasTail = tail;
			queue.submitCustomGeometry(matrices, bgLayer, (entry, vc) -> {
				float z = -0.15F, x2 = fx + fw, y2 = fy + fh; // behind the text (+z points at the viewer)
				// body
				rect(vc, entry, fx, fy, x2, y2, z, fill, light);
				if (hasTail) {
					rect(vc, entry, -3, y2, 3, y2 + 1, z, fill, light);
					rect(vc, entry, -2, y2 + 1, 2, y2 + 2, z, fill, light);
					rect(vc, entry, -1, y2 + 2, 1, y2 + 3, z, fill, light);
				}
				if ((edge >>> 24) != 0) {
					// border strips (never overlapping the body, so depth order does not matter)
					rect(vc, entry, fx - 1, fy - 1, x2 + 1, fy, z, edge, light);
					rect(vc, entry, fx - 1, fy, fx, y2, z, edge, light);
					rect(vc, entry, x2, fy, x2 + 1, y2, z, edge, light);
					if (hasTail) {
						rect(vc, entry, fx - 1, y2, -3, y2 + 1, z, edge, light);
						rect(vc, entry, 3, y2, x2 + 1, y2 + 1, z, edge, light);
						rect(vc, entry, -3, y2 + 1, -2, y2 + 2, z, edge, light);
						rect(vc, entry, 2, y2 + 1, 3, y2 + 2, z, edge, light);
						rect(vc, entry, -2, y2 + 2, -1, y2 + 3, z, edge, light);
						rect(vc, entry, 1, y2 + 2, 2, y2 + 3, z, edge, light);
						rect(vc, entry, -1, y2 + 3, 1, y2 + 4, z, edge, light);
					} else {
						rect(vc, entry, fx - 1, y2, x2 + 1, y2 + 1, z, edge, light);
					}
				}
			});
			float ty = top + padY;
			for (FormattedCharSequence line : b.lines) {
				float tx = -tr.width(line) / 2F;
				queue.submitText(matrices, tx, ty, line, false, layerType, light, text, 0, 0);
				ty += 9;
			}
			y = top - 4;
		}
		matrices.popPose();
	}

	private static int mul(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * a);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	/** Two-sided quad (works whether or not the layer culls back faces). */
	private static void rect(VertexConsumer vc, PoseStack.Pose e, float x1, float y1, float x2, float y2, float z, int c, int light) {
		vc.addVertex(e, x1, y1, z).setColor(c).setLight(light);
		vc.addVertex(e, x1, y2, z).setColor(c).setLight(light);
		vc.addVertex(e, x2, y2, z).setColor(c).setLight(light);
		vc.addVertex(e, x2, y1, z).setColor(c).setLight(light);
		vc.addVertex(e, x2, y1, z).setColor(c).setLight(light);
		vc.addVertex(e, x2, y2, z).setColor(c).setLight(light);
		vc.addVertex(e, x1, y2, z).setColor(c).setLight(light);
		vc.addVertex(e, x1, y1, z).setColor(c).setLight(light);
	}
}
