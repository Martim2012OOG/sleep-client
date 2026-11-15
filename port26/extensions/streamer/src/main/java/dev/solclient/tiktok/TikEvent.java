package dev.solclient.tiktok;

/** One thing that happened in the TikTok live (already parsed, immutable). */
public record TikEvent(Type type, String user, String nick, String text, String gift, int giftId, int coinsEach, int repeat,
		boolean streaking, String id) {

	public enum Type {
		CHAT, GIFT, FOLLOW, SHARE, SUBSCRIBE
	}

	/** Total coins of a gift event (price of one x how many). */
	public int coins() {
		return Math.max(0, coinsEach) * Math.max(1, repeat);
	}

	/** Name to show: nickname, else @user. */
	public String who() {
		if (nick != null && !nick.isBlank()) {
			return nick;
		}
		return user == null || user.isBlank() ? "Someone" : user;
	}

	/** Key that identifies one viewer's running gift streak. */
	public String streakKey() {
		return user + "|" + giftId + "|" + gift;
	}

	public static TikEvent chat(String user, String nick, String text) {
		return new TikEvent(Type.CHAT, user, nick, text, "", 0, 0, 0, false, null);
	}

	public static TikEvent gift(String user, String nick, String gift, int coinsEach, int repeat) {
		return new TikEvent(Type.GIFT, user, nick, "", gift, 0, coinsEach, repeat, false, null);
	}

	public static TikEvent simple(Type t, String user, String nick) {
		return new TikEvent(t, user, nick, "", "", 0, 0, 0, false, null);
	}
}
