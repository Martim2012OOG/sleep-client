package dev.sleepclient.social;

import com.google.gson.JsonObject;
import dev.sleepclient.Config;
import dev.sleepclient.SleepClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.MinecraftClient;

/** Settings in the cloud: your whole Sleep config saved on your Sleep account (needs the site endpoint /me/config). */
public final class CloudSync {

	public static volatile String status = "";
	public static volatile String lastSync = "";

	private CloudSync() {
	}

	public static void upload() {
		if (Social.me == null) {
			status = "Log in to Sleep first";
			return;
		}
		status = "Uploading...";
		Social.IO.execute(() -> {
			try {
				Path tmp = Files.createTempFile("kubo-config", ".json");
				Config.saveTo(tmp);
				byte[] raw = Files.readAllBytes(tmp);
				Files.deleteIfExists(tmp);
				ByteArrayOutputStream bo = new ByteArrayOutputStream();
				try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
					gz.write(raw);
				}
				JsonObject body = new JsonObject();
				body.addProperty("data", Base64.getEncoder().encodeToString(bo.toByteArray()));
				body.addProperty("version", 1);
				SolarApi.Res r = SolarApi.put("/me/config", body);
				if (r.status() == 404) {
					status = "The site needs the /me/config update";
				} else if (!r.ok()) {
					status = "Upload failed: " + (r.error() == null ? r.status() : r.error());
				} else {
					status = "Saved in the cloud";
					lastSync = java.time.LocalTime.now().withNano(0).toString();
				}
			} catch (Exception e) {
				status = "Upload failed: " + e.getMessage();
			}
		});
	}

	public static void download() {
		if (Social.me == null) {
			status = "Log in to Sleep first";
			return;
		}
		status = "Downloading...";
		Social.IO.execute(() -> {
			try {
				SolarApi.Res r = SolarApi.get("/me/config");
				if (r.status() == 404) {
					status = "Nothing in the cloud yet (or the site needs the update)";
					return;
				}
				if (!r.ok()) {
					status = "Download failed: " + (r.error() == null ? r.status() : r.error());
					return;
				}
				String data = Json.str(r.obj(), "data");
				if (data == null) {
					status = "Nothing in the cloud yet";
					return;
				}
				byte[] raw;
				try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
					raw = gz.readAllBytes();
				}
				Path tmp = Files.createTempFile("kubo-config", ".json");
				Files.write(tmp, raw);
				MinecraftClient.getInstance().execute(() -> {
					Config.loadFrom(tmp, SleepClient.MODS);
					Config.save();
					try {
						Files.deleteIfExists(tmp);
					} catch (Exception ignored) {
					}
					status = "Settings loaded from the cloud";
					lastSync = java.time.LocalTime.now().withNano(0).toString();
				});
			} catch (Exception e) {
				status = "Download failed: " + e.getMessage();
			}
		});
	}
}
