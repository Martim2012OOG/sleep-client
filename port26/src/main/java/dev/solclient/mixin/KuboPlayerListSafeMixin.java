package dev.solclient.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a vanilla 1.21.11 crash (java.util.ConcurrentModificationException in ClientPlayNetworkHandler): chat messages
 * with a player head (e.g. "[Name head] Welcome Name to the server!") look the player up in the tab list on a worker
 * thread, while the network thread adds/removes players from the same HashMap. A ConcurrentHashMap never throws that.
 * Done by reflection and fully guarded: if anything is different, nothing changes and the game still starts.
 */
@Mixin(ClientPacketListener.class)
public class KuboPlayerListSafeMixin {

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void kubo$threadSafePlayerList(CallbackInfo ci) {
		try {
			for (Field f : ClientPacketListener.class.getDeclaredFields()) {
				if (!Map.class.isAssignableFrom(f.getType()) || !(f.getGenericType() instanceof ParameterizedType p)) {
					continue;
				}
				Type[] a = p.getActualTypeArguments();
				if (a.length != 2 || a[0] != UUID.class || a[1] != PlayerInfo.class) {
					continue;
				}
				f.setAccessible(true);
				Object old = f.get(this);
				if (old instanceof Map<?, ?> m && !(old instanceof ConcurrentHashMap)) {
					@SuppressWarnings("unchecked")
					Map<UUID, PlayerInfo> copy = new ConcurrentHashMap<>((Map<UUID, PlayerInfo>) m);
					f.set(this, copy);
				}
				return;
			}
		} catch (Throwable t) {
			System.err.println("[Caos] player-list thread-safety patch not applied: " + t);
		}
	}
}
