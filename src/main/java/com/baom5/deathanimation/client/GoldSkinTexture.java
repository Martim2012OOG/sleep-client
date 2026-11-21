package com.baom5.deathanimation.client;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class GoldSkinTexture {
   private static final UUID PROFILE_ID = UUID.fromString("0388404b-3a7f-414d-be2f-338a9f97066e");
   private static final Identifier FALLBACK = Identifier.of("deathanimation", "textures/entity/gold_skin.png");
   private static final AtomicBoolean REQUEST_STARTED = new AtomicBoolean();
   private static volatile Identifier profileTexture;

   private GoldSkinTexture() {
   }

   public static Identifier texture() {
      Identifier loaded = profileTexture;
      if (loaded != null) {
         return loaded;
      }

      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (REQUEST_STARTED.compareAndSet(false, true)) {
         CompletableFuture.<Optional<com.mojang.authlib.GameProfile>>supplyAsync(() -> minecraft.getApiServices().profileResolver().getProfileById(PROFILE_ID))
            .thenCompose(
               profile -> profile.<CompletionStage<Optional<net.minecraft.entity.player.SkinTextures>>>map(value -> minecraft.getSkinProvider().fetchSkinTextures(value))
                  .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()))
            )
            .thenAccept(skin -> skin.ifPresent(textures -> {
               Identifier texture = textures.body().texturePath();
               if (!texture.getPath().contains("entity/player/")) {
                  profileTexture = texture;
               }
            }))
            .exceptionally(error -> null);
      }

      return FALLBACK;
   }
}
