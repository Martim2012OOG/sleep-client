package dev.sleepclient.mixin.extra;

import dev.sleepclient.net.PrivateConnection;
import java.net.InetSocketAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Private Connection: the game connects to the relay on this computer instead of straight to the server. */
@Mixin(targets = "net.minecraft.network.Connection")
public abstract class PrivateConnectionMixin {

	// only the static connect(InetSocketAddress, NetworkingBackend, ClientConnection): the other "connect"s take a host name
	// and made the whole injection fail (Private Connection then never took effect)
	@ModifyVariable(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private static InetSocketAddress kubo$privateConnection(InetSocketAddress address) {
		return PrivateConnection.redirect(address);
	}
}
