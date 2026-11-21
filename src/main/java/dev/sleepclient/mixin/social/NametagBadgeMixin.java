package dev.sleepclient.mixin.social;

import dev.sleepclient.social.SolarBadge;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Plegumo logo in front of the nametag of players who use the client. */
@Mixin(EntityRenderer.class)
public abstract class NametagBadgeMixin {

	/** entity id -> {time, decorated name}: the name is rebuilt 4 times a second, not every frame for every player. */
	@org.spongepowered.asm.mixin.Unique
	private static final java.util.Map<Integer, Object[]> kubo$names = new java.util.HashMap<>();
	@org.spongepowered.asm.mixin.Unique
	private static long kubo$cleanAt;

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$badge(Entity entity, CallbackInfoReturnable<Text> cir) {
		if ((entity instanceof PlayerEntity || entity instanceof net.minecraft.entity.decoration.ArmorStandEntity) && cir.getReturnValue() != null) {
			long now = System.currentTimeMillis();
			Object[] c = kubo$names.get(entity.getId());
			if (c != null && now - (Long) c[0] < 250L) {
				cir.setReturnValue((Text) c[1]);
				return;
			}
			if (now - kubo$cleanAt > 10_000L) {
				kubo$cleanAt = now;
				kubo$names.entrySet().removeIf(e -> now - (Long) e.getValue()[0] > 5_000L);
			}
			kubo$decorate(entity, cir);
			kubo$names.put(entity.getId(), new Object[] {now, cir.getReturnValue()});
			return;
		}
		kubo$decorate(entity, cir);
	}

	@org.spongepowered.asm.mixin.Unique
	private static void kubo$decorate(Entity entity, CallbackInfoReturnable<Text> cir) {
		if (entity instanceof PlayerEntity p && cir.getReturnValue() != null) {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			if (p != mc.player && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) == null) {
				return; // a server NPC (a fake player, not in the tab list): no badges, voice icons or logos to look up
			}
			Text name = dev.sleepclient.mods.NickHiderMod.apply(cir.getReturnValue());
			name = dev.sleepclient.mods.NameColourMod.apply(name);
			name = SolarBadge.uses(p.getUuid(), p.getName().getString()) ? SolarBadge.decorate(name) : name;
			name = dev.sleepclient.ext.KuboApi.decorateName(p, name);
			name = dev.sleepclient.social.ProfileBadges.decorate(p.getUuid(), p.getName().getString(), name);
			name = dev.sleepclient.mods.TotemPopsMod.decorate(p, name);
			cir.setReturnValue(dev.sleepclient.voice.VoiceIcons.decorate(p.getUuid(), name));
		} else if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity && cir.getReturnValue() != null) {
			// servers that show the rank + name on an invisible armor stand instead of the player's own nametag
			cir.setReturnValue(dev.sleepclient.social.NameInject.apply(cir.getReturnValue()));
		}
	}
}
