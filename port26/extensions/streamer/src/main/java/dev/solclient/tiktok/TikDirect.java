package dev.solclient.tiktok;

import com.google.gson.JsonObject;
import io.github.jwdeveloper.tiktok.api.data.models.users.User;
import io.github.jwdeveloper.tiktok.api.live.LiveClient;
import io.github.jwdeveloper.tiktok.client.TikTokLive;
import java.time.Duration;
import java.util.logging.Level;

/**
 * "Automatic" source: connects straight to TikTok with only your @user (TikTokLiveJava, MIT licence, bundled in this
 * extension). No app, no API key. While you are not live it keeps waiting and checks again every 15 s, so the moment
 * you start a live on your phone / TikTok LIVE Studio / OBS, the chat, gifts and viewer count start by themselves.
 * Every event is turned into the same JSON the TikFinity app sends, so the rest of the mod works exactly the same.
 */
final class TikDirect {

	private volatile LiveClient live;
	private volatile int gen;

	synchronized void start(String user, TikClient sink) {
		stop();
		final int g = ++gen;
		sink.directStatus("Waiting for @" + user + " to go live...");
		Thread t = new Thread(() -> {
			try {
				LiveClient c = TikTokLive.newClient(user)
						.configure(s -> {
							s.setClientLanguage("en");
							s.setLogLevel(Level.OFF);
							s.setPrintToConsole(false);
							s.setRetryOnConnectionFailure(true); // not live yet: try again until you are
							s.setRetryConnectionTimeout(Duration.ofSeconds(15));
						})
						.onConnected((lc, e) -> {
							if (g == gen) {
								sink.directStatus("Connected - @" + user + " is live");
								// every gift's picture and price, for the Parkour Live panel icons
								try {
									for (var gift : lc.getGiftManager().toList()) {
										TikGifts.learn(gift.getName(), gift.getPicture() == null ? null : gift.getPicture().getLink(), gift.getDiamondCost());
									}
								} catch (Throwable ignored) {
									// only icons
								}
							}
						})
						.onDisconnected((lc, e) -> {
							if (g == gen) {
								sink.directStatus("Waiting for @" + user + " to go live...");
							}
						})
						.onLiveEnded((lc, e) -> {
							if (g == gen) {
								sink.directStatus("Live ended - waiting for the next one");
							}
						})
						.onError((lc, e) -> {
							if (g == gen && !sink.status().startsWith("Connected")) {
								Throwable x = e.getException();
								String m = x == null || x.getMessage() == null ? "" : x.getMessage();
								sink.directStatus("Waiting for @" + user + " to go live" + (m.isBlank() ? "..." : " (" + shortMsg(m) + ")"));
							}
						})
						.onComment((lc, e) -> {
							if (g == gen) {
								JsonObject d = who(e.getUser());
								d.addProperty("comment", e.getText());
								sink.inject(msg("chat", d));
							}
						})
						.onGift((lc, e) -> {
							if (g == gen && e.getGift() != null) {
								JsonObject d = who(e.getUser());
								d.addProperty("giftName", e.getGift().getName());
								d.addProperty("giftId", e.getGift().getId());
								d.addProperty("diamondCount", e.getGift().getDiamondCost());
								d.addProperty("repeatCount", Math.max(1, e.getCombo()));
								d.addProperty("giftType", 0); // the library already sends one event at the end of a streak
								try {
									if (e.getGift().getPicture() != null) {
										d.addProperty("giftPictureUrl", e.getGift().getPicture().getLink());
									}
								} catch (Throwable ignored) {
									// no picture
								}
								sink.inject(msg("gift", d));
							}
						})
						.onFollow((lc, e) -> {
							if (g == gen) {
								sink.inject(msg("follow", who(e.getUser())));
							}
						})
						.onShare((lc, e) -> {
							if (g == gen) {
								sink.inject(msg("share", who(e.getUser())));
							}
						})
						.onLike((lc, e) -> {
							if (g == gen) {
								JsonObject d = who(e.getUser());
								d.addProperty("likeCount", e.getLikes());
								d.addProperty("totalLikeCount", e.getTotalLikes());
								sink.inject(msg("like", d));
							}
						})
						.onRoomInfo((lc, e) -> {
							if (g == gen && e.getRoomInfo() != null) {
								JsonObject d = new JsonObject();
								d.addProperty("viewerCount", e.getRoomInfo().getViewersCount());
								sink.inject(msg("roomUser", d));
							}
						})
						.build();
				if (g != gen) {
					return;
				}
				live = c;
				c.connect();
			} catch (Throwable ex) {
				if (g == gen) {
					String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
					sink.directStatus("Could not connect: " + shortMsg(m));
				}
			}
		}, "kubo-tiktok-direct");
		t.setDaemon(true);
		t.start();
	}

	synchronized void stop() {
		gen++;
		LiveClient c = live;
		live = null;
		if (c != null) {
			try {
				c.disconnect();
			} catch (Throwable ignored) {
				// already gone
			}
		}
	}

	private static JsonObject who(User u) {
		JsonObject d = new JsonObject();
		if (u != null) {
			d.addProperty("uniqueId", u.getName() == null ? "" : u.getName());
			d.addProperty("nickname", u.getProfileName() == null ? "" : u.getProfileName());
		}
		return d;
	}

	private static String msg(String event, JsonObject data) {
		JsonObject o = new JsonObject();
		o.addProperty("event", event);
		o.add("data", data);
		return o.toString();
	}

	private static String shortMsg(String m) {
		m = m.replace('\n', ' ');
		return m.length() > 80 ? m.substring(0, 80) + "..." : m;
	}
}
