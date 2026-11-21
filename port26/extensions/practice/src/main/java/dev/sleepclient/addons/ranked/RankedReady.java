package dev.sleepclient.addons.ranked;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by a player's game to the match host once the arena has really finished loading on their screen. */
public record RankedReady() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RankedReady> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("kubo_ext_practice", "ranked_ready"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RankedReady> CODEC = StreamCodec.unit(new RankedReady());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
