package dev.solclient.mods;

import dev.solclient.social.Social;

public class FriendsHudMod extends TextHudMod {

	public FriendsHudMod() {
		super("Friends Online", "friends_hud", "How many of your Solar friends are online, and how many are on your server.", 0.01F, 0.50F);
	}

	@Override
	protected String text(boolean edit) {
		if (Social.me == null) {
			return edit ? "2 friends online" : null;
		}
		int on = 0, here = 0;
		for (Social.User u : Social.friends) {
			if (u.online()) {
				on++;
			}
			if (u.onMyServer()) {
				here++;
			}
		}
		if (on == 0 && !edit) {
			return null;
		}
		return on + (on == 1 ? " friend" : " friends") + " online" + (here > 0 ? "  •  " + here + " here" : "");
	}
}
