package dev.solclient.addons.ranked;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent by a player's game to the match host once the arena has really finished loading on their screen. */
public record RankedReady() implements CustomPayload {

	public static final CustomPayload.Id<RankedReady> ID = new CustomPayload.Id<>(Identifier.of("kubo_ext_practice", "ranked_ready"));
	public static final PacketCodec<RegistryByteBuf, RankedReady> CODEC = PacketCodec.unit(new RankedReady());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
