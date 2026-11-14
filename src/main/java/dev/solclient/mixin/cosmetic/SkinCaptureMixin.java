package dev.solclient.mixin.cosmetic;

import dev.solclient.cosmetic.SkinPixels;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 3D Skin Layers: remembers the pixels of every skin the game registers (they are needed to build the voxels). */
@Mixin(PlayerSkinTextureDownloader.class)
public abstract class SkinCaptureMixin {

	@Inject(method = "registerTexture(Lnet/minecraft/util/AssetInfo$TextureAsset;Lnet/minecraft/client/texture/NativeImage;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), require = 0)
	private void sleep$capture(AssetInfo.TextureAsset asset, NativeImage image, CallbackInfoReturnable<?> cir) {
		try {
			SkinPixels.put(asset.texturePath(), image);
		} catch (RuntimeException ignored) {
		}
	}
}
