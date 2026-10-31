package dev.solclient.mods;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Raw "badlion:timers" plugin message. */
public record TimersPayload(byte[] data) implements CustomPayload {

	public static final CustomPayload.Id<TimersPayload> ID = new CustomPayload.Id<>(Identifier.of("badlion", "timers"));

	public static final PacketCodec<PacketByteBuf, TimersPayload> CODEC = PacketCodec.of(
			(payload, buf) -> buf.writeBytes(payload.data()), buf -> {
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				return new TimersPayload(bytes);
			});

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
