package dev.solclient.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Config;
import dev.solclient.Lang;
import dev.solclient.SolClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * HUD Layouts: share your whole HUD (which HUD mods are on, where they are and their size) with a code, paste a
 * friend's code to get theirs, and keep 3 layouts of your own to switch between.
 */
public class HudLayoutsMod extends Mod {

	public static HudLayoutsMod instance;
	private String msg = "";
	private long msgAt;

	public HudLayoutsMod() {
		super("HUD Layouts", "hud_layouts", "Share your HUD with a code, use a friend's HUD, keep 3 layouts to switch.", Category.UTILITY);
		instance = this;
		header("Share");
		action("Copy my HUD code", "Copy", () -> {
			mc.keyboard.setClipboard(export());
			say(Lang.tr("Copied! Send it to a friend", "Copiado! Manda a um amigo"));
		}, this::status);
		action("Use a HUD code", "Paste", () -> say(apply(mc.keyboard.getClipboard()) ? Lang.tr("HUD imported!", "HUD importado!") : Lang.tr("That is not a HUD code", "Isso não é um código de HUD")),
				this::status);
		header("My layouts");
		for (int i = 1; i <= 3; i++) {
			final int slot = i;
			action("Layout " + i, "Save", () -> {
				saveSlot(slot);
				say(Lang.tr("Saved in layout ", "Guardado no layout ") + slot);
			}, () -> Files.exists(slot(slot)) ? Lang.tr("saved", "guardado") : Lang.tr("empty", "vazio"));
			action("Use layout " + i, "Load", () -> say(loadSlot(slot) ? Lang.tr("Layout loaded", "Layout carregado") : Lang.tr("That layout is empty", "Esse layout está vazio")),
					() -> "");
		}
	}

	private void say(String s) {
		msg = s;
		msgAt = System.currentTimeMillis();
	}

	private String status() {
		return System.currentTimeMillis() - msgAt < 4000L ? msg : "";
	}

	private static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		for (Mod m : SolClient.MODS) {
			if (m instanceof HudMod h) {
				JsonObject o = new JsonObject();
				o.addProperty("on", h.enabled);
				o.addProperty("x", Math.round(h.hx * 10000) / 10000.0);
				o.addProperty("y", Math.round(h.hy * 10000) / 10000.0);
				o.addProperty("s", h.scale.get());
				root.add(h.id, o);
			}
		}
		return root;
	}

	private static void restore(JsonObject root) {
		for (Mod m : SolClient.MODS) {
			if (m instanceof HudMod h && root.has(h.id) && root.get(h.id).isJsonObject()) {
				JsonObject o = root.getAsJsonObject(h.id);
				h.setEnabled(o.get("on").getAsBoolean());
				h.hx = o.get("x").getAsFloat();
				h.hy = o.get("y").getAsFloat();
				h.scale.set(o.get("s").getAsFloat());
			}
		}
		Config.save();
	}

	public static String export() {
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
				gz.write(snapshot().toString().getBytes(StandardCharsets.UTF_8));
			}
			return "KUBOHUD1:" + Base64.getEncoder().encodeToString(bo.toByteArray());
		} catch (Exception e) {
			return "";
		}
	}

	public static boolean apply(String code) {
		if (code == null || !code.trim().startsWith("KUBOHUD1:")) {
			return false;
		}
		try {
			byte[] raw = Base64.getDecoder().decode(code.trim().substring(9));
			try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
				restore(JsonParser.parseString(new String(gz.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static Path slot(int i) {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-hud-layouts").resolve("layout" + i + ".json");
	}

	private static void saveSlot(int i) {
		try {
			Files.createDirectories(slot(i).getParent());
			Files.writeString(slot(i), snapshot().toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	private static boolean loadSlot(int i) {
		try {
			if (!Files.exists(slot(i))) {
				return false;
			}
			restore(JsonParser.parseString(Files.readString(slot(i), StandardCharsets.UTF_8)).getAsJsonObject());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
