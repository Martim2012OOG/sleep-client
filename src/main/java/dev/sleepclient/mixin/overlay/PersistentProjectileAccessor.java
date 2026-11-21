package dev.sleepclient.mixin.overlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.11: the "inGround" field became isInGround(). */
@Mixin(targets = "net.minecraft.entity.projectile.PersistentProjectileEntity")
public interface PersistentProjectileAccessor {

	@Invoker("isInGround")
	boolean sol$inGround();
}
