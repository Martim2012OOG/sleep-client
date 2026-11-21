package dev.sleepclient.mods;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingMod extends CounterHudMod {
	public PingMod() {
		super("Ping", "ping", "Display the latency to the server.", 0.01F, 0.08F);
	}

	@Override
	protected boolean higherIsBetter() {
		return false;
	}

	@Override
	public int getIntValue() {
		ClientPacketListener handler = mc.getConnection();
		if (handler == null || mc.player == null) {
			return 0;
		}
		PlayerInfo entry = handler.getPlayerInfo(mc.player.getUUID());
		return entry == null ? 0 : entry.getLatency();
	}

	@Override
	public String getSuffix() {
		return "ms";
	}
}
