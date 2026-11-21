package dev.sleepclient.mods;

/** An iPhone-style pill at the top: server, players, ping and time. */
public class DynamicIslandMod extends TextHudMod {

	public DynamicIslandMod() {
		super("Dynamic Island", "dynamic_island", "A pill at the top with server, players and time.", 0.5F, 0.005F);
	}

	@Override
	protected String text(boolean edit) {
		if (mc.player == null) return edit ? "\u25cf play.server.net | 42 online | 35ms | 21:30" : null;
		var entry = mc.getCurrentServer();
		String where = entry == null ? "Singleplayer" : entry.ip;
		int online = mc.getConnection() == null ? 1 : mc.getConnection().getOnlinePlayers().size();
		String ping = "";
		if (mc.getConnection() != null) {
			var e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (e != null && entry != null) ping = " | " + e.getLatency() + "ms";
		}
		String time = java.time.LocalTime.now().withSecond(0).withNano(0).toString();
		return "\u25cf " + where + " | " + online + " online" + ping + " | " + time;
	}
}
