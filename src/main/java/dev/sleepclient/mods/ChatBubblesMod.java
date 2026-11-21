package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

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
		List<OrderedText> lines;
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
		String s = Formatting.strip(raw);
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
		String s = Formatting.strip(raw);
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
		if (mc.getNetworkHandler() == null) {
			return null;
		}
		String low = name.toLowerCase(Locale.ROOT);
		if (mc.world != null) {
			for (PlayerEntity p : mc.world.getPlayers()) {
				if (p.getName().getString().toLowerCase(Locale.ROOT).equals(low)) {
					return p.getUuid();
				}
			}
		}
		for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
			String n = profileName(e.getProfile());
			if (n != null && n.toLowerCase(Locale.ROOT).equals(low)) {
				return dev.sleepclient.social.SolarBadge.idOf(e.getProfile());
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
		text = Formatting.strip(text);
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
		if (mc.world == null) {
			BUBBLES.clear();
		}
	}

	/** Bubbles of an entity for the frame being built (null = nothing to draw). */
	public static List<Bubble> forEntity(Entity e) {
		if (!on() || !(e instanceof PlayerEntity) || BUBBLES.isEmpty()) {
			return null;
		}
		if (e == instance.mc.player && !instance.own.on()) {
			return null;
		}
		List<Bubble> l = BUBBLES.get(e.getUuid());
		return l == null || l.isEmpty() ? null : l;
	}

	// ------------------------------------------------------------------ rendering

	private int[] colours() {
		// {fill, (unused: the border is gone), text}: rounded soft bubbles, no hard outline
		return switch (style.idx()) {
			case 1 -> new int[] {0xC8141820, 0, 0xFFFFFFFF};
			case 2 -> new int[] {0xF0272C3C, 0, 0xFFFFFFFF};
			default -> new int[] {0xF4FBF7EE, 0, 0xFF22252B};
		};
	}

	/** Draws the bubbles of one entity. The matrix stack is at the entity's feet. */
	public static void render(List<Bubble> bubbles, EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (instance == null || bubbles == null || camera == null || state.invisible) {
			return;
		}
		double r = instance.range.f();
		if (state.squaredDistanceToCamera > r * r) {
			return;
		}
		TextRenderer tr = instance.mc.textRenderer;
		int maxW = instance.maxWidth.i();
		long now = System.currentTimeMillis();
		int[] col = instance.colours();
		Vec3d base = state.nameLabelPos != null ? state.nameLabelPos.add(0, 0.42, 0) : new Vec3d(0, state.height + 0.55, 0);
		float s = 0.025F * instance.size.f() / 100F;

		matrices.push();
		matrices.translate(base.x, base.y, base.z);
		matrices.multiply(camera.orientation);
		matrices.scale(s, -s, s);

		int light = instance.throughWalls.on() ? 0xF000F0 : state.light;
		var layerType = instance.throughWalls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.POLYGON_OFFSET;
		var bgLayer = instance.throughWalls.on() ? RenderLayers.textBackgroundSeeThrough() : RenderLayers.textBackground();

		float y = 0; // bottom of the stack, growing upwards (negative y)
		for (int i = bubbles.size() - 1; i >= 0; i--) {
			Bubble b = bubbles.get(i);
			if (b.wrapW != maxW || b.lines == null) {
				b.lines = tr.wrapLines(dev.sleepclient.Gfx.raw(b.text), maxW);
				if (b.lines.size() > 4) {
					b.lines = new ArrayList<>(b.lines.subList(0, 4));
				}
				b.width = 0;
				for (OrderedText t : b.lines) {
					b.width = Math.max(b.width, tr.getWidth(t));
				}
				b.wrapW = maxW;
			}
			long age = now - b.born, left = b.life - age;
			float a = Math.min(1F, Math.min(age / 150F, left / 400F));
			if (a <= 0.02F) {
				continue;
			}
			boolean tail = i == bubbles.size() - 1;
			int padX = 6, padY = 4;
			float w = b.width + padX * 2, h = b.lines.size() * 10 + padY * 2 - 1;
			float tailH = tail ? 4 : 0;
			float bottom = y - tailH;
			float top = bottom - h;
			float left2 = -w / 2;
			int fill = mul(col[0], a), edge = mul(col[1], a), text = mul(col[2], a);
			final float fx = left2, fy = top, fw = w, fh = h;
			final boolean hasTail = tail;
			queue.submitCustom(matrices, bgLayer, (entry, vc) -> {
				float z = -0.15F, x2 = fx + fw, y2 = fy + fh; // behind the text (+z points at the viewer)
				roundBody(vc, entry, fx, fy, x2, y2, z, fill, light);
				if (hasTail) { // a small triangle pointing at the player
					rect(vc, entry, -3, y2, 3, y2 + 1, z, fill, light);
					rect(vc, entry, -2, y2 + 1, 2, y2 + 2, z, fill, light);
					rect(vc, entry, -1, y2 + 2, 1, y2 + 3, z, fill, light);
				}
			});
			float ty = top + padY;
			for (OrderedText line : b.lines) {
				float tx = -tr.getWidth(line) / 2F;
				queue.submitText(matrices, tx, ty, line, false, layerType, light, text, 0, 0);
				ty += 10;
			}
			y = top - 4;
		}
		matrices.pop();
	}

	/** A rectangle with rounded corners (stair-stepped, 4 units), built from plain quads that never overlap. */
	private static void roundBody(VertexConsumer vc, MatrixStack.Entry e, float x1, float y1, float x2, float y2, float z, int c, int light) {
		final int[] inset = {3, 2, 1, 1};
		for (int k = 0; k < inset.length; k++) {
			rect(vc, e, x1 + inset[k], y1 + k, x2 - inset[k], y1 + k + 1, z, c, light);
			rect(vc, e, x1 + inset[k], y2 - k - 1, x2 - inset[k], y2 - k, z, c, light);
		}
		rect(vc, e, x1, y1 + inset.length, x2, y2 - inset.length, z, c, light);
	}

	private static int mul(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * a);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	/** Two-sided quad (works whether or not the layer culls back faces). */
	private static void rect(VertexConsumer vc, MatrixStack.Entry e, float x1, float y1, float x2, float y2, float z, int c, int light) {
		vc.vertex(e, x1, y1, z).color(c).light(light);
		vc.vertex(e, x1, y2, z).color(c).light(light);
		vc.vertex(e, x2, y2, z).color(c).light(light);
		vc.vertex(e, x2, y1, z).color(c).light(light);
		vc.vertex(e, x2, y1, z).color(c).light(light);
		vc.vertex(e, x2, y2, z).color(c).light(light);
		vc.vertex(e, x1, y2, z).color(c).light(light);
		vc.vertex(e, x1, y1, z).color(c).light(light);
	}
}
