package dev.sleepclient.voice;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.sleepclient.Gfx;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Voice icons: after names above heads (talking / muted / deafened) and your own mic in the corner. */
public final class VoiceIcons {

	private VoiceIcons() {
	}

	private static Component glyph(String ch) {
		JsonObject o = new JsonObject();
		o.addProperty("text", ch);
		o.addProperty("font", "sleep_client:voice");
		o.addProperty("color", "white");
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(Component.literal(""));
	}

	private static boolean on() {
		VoiceChatMod m = VoiceChatMod.instance;
		return m != null && m.enabled && m.showIcons.on() && VoiceEngine.INSTANCE.running;
	}

	/** The name with a voice icon after it, or the name unchanged. */
	public static Component decorate(UUID id, Component name) {
		if (!on() || id == null || name == null) {
			return name;
		}
		String ch = null;
		Minecraft mc = Minecraft.getInstance();
		VoiceChatMod m = VoiceChatMod.instance;
		if (mc.player != null && id.equals(mc.player.getUUID())) {
			// voice on: grey speaker, green while talking
			ch = m.deafened.on() ? "" : m.muted.on() ? "" : VoiceEngine.INSTANCE.talking ? "" : m.connected() ? "" : null;
		} else {
			String u = id.toString().replace("-", "");
			VoiceEngine.Peer p = VoiceEngine.INSTANCE.peer(u);
			if (p != null) {
				ch = VoiceEngine.INSTANCE.isTalking(u) ? "" : p.on && p.deafened ? "" : p.on && p.muted ? "" : p.on ? "" : null;
			}
		}
		if (ch == null) {
			return name;
		}
		return Component.empty().append(name).append(Component.literal(" ")).append(glyph(ch));
	}

	/** True when something of voice chat is drawn bottom-left (notifications sit above it). */
	public static boolean visible() {
		VoiceChatMod m = VoiceChatMod.instance;
		return m != null && m.enabled && m.hudIcon.on() && VoiceEngine.INSTANCE.running && (VoiceNet.groupMode() || !m.connected()
				|| m.deafened.on() || m.muted.on() || VoiceEngine.INSTANCE.talking);
	}

	/** Your own mic state, bottom-left of the screen; in a voice group also everyone's heads (green frame = talking). */
	public static void drawHud(GuiGraphicsExtractor c) {
		VoiceChatMod m = VoiceChatMod.instance;
		if (m == null || !m.enabled || !m.hudIcon.on() || !VoiceEngine.INSTANCE.running) {
			return;
		}
		if (VoiceNet.groupMode() && VoiceNet.ready()) {
			int h0 = Minecraft.getInstance().getWindow().getGuiScaledHeight();
			java.util.List<dev.sleepclient.gui.AvatarStack.Person> people = new java.util.ArrayList<>();
			for (VoiceNet.Member mm : VoiceNet.list()) {
				boolean me = mm.uuid().equals(VoiceNet.you());
				people.add(new dev.sleepclient.gui.AvatarStack.Person(mm.uuid(), mm.name(), me ? VoiceEngine.INSTANCE.talking : VoiceEngine.INSTANCE.isTalking(mm.uuid())));
			}
			dev.sleepclient.gui.AvatarStack.draw(c, "hudvoice", 26, h0 - 23, 18, 6, people, 6);
		}
		String icon;
		if (!m.connected()) {
			icon = "disconnected";
		} else if (m.deafened.on()) {
			icon = "speaker_off";
		} else if (m.muted.on()) {
			icon = "microphone_off";
		} else if (VoiceEngine.INSTANCE.talking || VoiceNet.groupMode()) {
			icon = "microphone";
		} else {
			return;
		}
		int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		Gfx.tex(c, Gfx.id("textures/voice/" + icon + ".png"), 6, h - 22, 16, 16, 16, 16, 0xFFFFFFFF);
	}
}
