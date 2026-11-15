package dev.solclient.mods;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Raw "badlion:timers" plugin message. */
public record TimersPayload(byte[] data) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<TimersPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("badlion", "timers"));

	public static final StreamCodec<FriendlyByteBuf, TimersPayload> CODEC = StreamCodec.ofMember(
			(payload, buf) -> buf.writeBytes(payload.data()), buf -> {
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				return new TimersPayload(bytes);
			});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
