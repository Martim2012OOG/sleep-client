package dev.solclient.mods;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

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
		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (handler == null || mc.player == null) {
			return 0;
		}
		PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}

	@Override
	public String getSuffix() {
		return "ms";
	}
}
