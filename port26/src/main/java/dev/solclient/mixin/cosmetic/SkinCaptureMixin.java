package dev.solclient.mixin.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import dev.solclient.cosmetic.SkinPixels;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 3D Skin Layers: remembers the pixels of every skin the game registers (they are needed to build the voxels). */
@Mixin(SkinTextureDownloader.class)
public abstract class SkinCaptureMixin {

	@Inject(method = "registerTextureInManager(Lnet/minecraft/core/ClientAsset$Texture;Lcom/mojang/blaze3d/platform/NativeImage;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), require = 0)
	private void sleep$capture(ClientAsset.Texture asset, NativeImage image, CallbackInfoReturnable<?> cir) {
		try {
			SkinPixels.put(asset.texturePath(), image);
		} catch (RuntimeException ignored) {
		}
	}
}
