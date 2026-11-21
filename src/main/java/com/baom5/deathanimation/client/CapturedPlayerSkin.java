package com.baom5.deathanimation.client;

import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
final class CapturedPlayerSkin {
   private final GameProfile profile;
   private final WeakReference<AbstractClientPlayerEntity> source;
   private SkinTextures latest;
   private CompletableFuture<Optional<SkinTextures>> pending;
   private long retryAfter;

   CapturedPlayerSkin(AbstractClientPlayerEntity source) {
      this(source.getGameProfile(), readSource(source), source);
   }

   CapturedPlayerSkin(GameProfile profile, SkinTextures initial) {
      this(profile, initial, null);
   }

   private CapturedPlayerSkin(GameProfile profile, SkinTextures initial, AbstractClientPlayerEntity source) {
      this.profile = profile;
      this.source = new WeakReference<>(source);
      this.latest = initial == null ? DefaultSkinHelper.getSkinTextures(profile) : initial;
   }

   GameProfile profile() {
      return this.profile;
   }

   SkinTextures get() {
      AbstractClientPlayerEntity player = this.source.get();
      if (player != null) {
         this.remember(readSource(player));
      }

      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayNetworkHandler connection = client.getNetworkHandler();
      PlayerListEntry info = connection == null ? null : connection.getPlayerListEntry(this.profile.id());
      if (isDefault(this.latest) && info != null) {
         try {
            this.remember(info.getSkinTextures());
         } catch (RuntimeException var6) {
         }
      }

      if (!isDefault(this.latest)) {
         return this.latest;
      }

      if (this.pending != null && this.pending.isDone()) {
         this.pending.getNow(Optional.empty()).ifPresent(this::remember);
         this.pending = null;
         this.retryAfter = System.nanoTime() + 60000000000L;
      }

      if (isDefault(this.latest) && this.pending == null && (this.retryAfter == 0L || System.nanoTime() >= this.retryAfter)) {
         this.pending = CompletableFuture.completedFuture(info == null ? this.profile : info.getProfile())
            .thenCompose(client.getSkinProvider()::fetchSkinTextures)
            .thenCompose(
               skin -> skin.isPresent() && !isDefault((SkinTextures)skin.get())
                  ? CompletableFuture.completedFuture((Optional<SkinTextures>)skin)
                  : CompletableFuture.<Optional<GameProfile>>supplyAsync(() -> client.getApiServices().profileResolver().getProfileById(this.profile.id()))
                     .thenCompose(
                        resolved -> resolved.<CompletionStage<Optional<SkinTextures>>>map(value -> client.getSkinProvider().fetchSkinTextures(value))
                           .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()))
                     )
            )
            .exceptionally(error -> Optional.empty());
      }

      return this.latest;
   }

   void remember(SkinTextures candidate) {
      if (candidate != null && (!isDefault(candidate) || isDefault(this.latest))) {
         this.latest = candidate;
      }
   }

   private static SkinTextures readSource(AbstractClientPlayerEntity player) {
      try {
         return player.getSkin();
      } catch (RuntimeException ignored) {
         return DefaultSkinHelper.getSkinTextures(player.getGameProfile());
      }
   }

   static boolean isDefault(SkinTextures skin) {
      if (skin == null) {
         return true;
      }

      Identifier texture = skin.body().texturePath();
      String path = texture.getPath();
      return texture.getNamespace().equals("minecraft")
         && (path.startsWith("textures/entity/player/") || path.equals("textures/entity/steve.png") || path.equals("textures/entity/alex.png"));
   }
}
