package dev.sleepclient.mixin.cosmetic;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's "render state of an entity for the inventory" helper (used by the Locker preview). */
@Mixin(InventoryScreen.class)
public interface InventoryScreenAccessor {

	@Invoker("extractRenderState")
	static EntityRenderState plegumo$state(LivingEntity entity) {
		throw new AssertionError();
	}
}
