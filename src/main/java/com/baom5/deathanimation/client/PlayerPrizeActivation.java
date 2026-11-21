package com.baom5.deathanimation.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class PlayerPrizeActivation {
   private static DeathAvatar prize;
   private static long started;
   private static int nextId = -2147483648;
   private static float wobbleX;
   private static float wobbleY;

   private PlayerPrizeActivation() {
   }

   static void start(DeathScene scene) {
      clear();
      prize = new DeathAvatar(scene.level, scene.avatar, nextId++);
      prize.setYaw(180.0F);
      prize.bodyYaw = prize.lastBodyYaw = 180.0F;
      prize.headYaw = prize.lastHeadYaw = 180.0F;
      prize.syncOldPosition();
      started = scene.level.getTime();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      wobbleX = random.nextFloat() * 2.0F - 1.0F;
      wobbleY = random.nextFloat() * 2.0F - 1.0F;
   }

   static void register() {
      HudElementRegistry.addLast(
         Identifier.of("deathanimation", "player_prize_activation"), (graphics, delta) -> render(graphics, delta.getTickProgress(false))
      );
   }

   private static void render(DrawContext graphics, float partial) {
      if (prize != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.world != null && client.player != null && prize.getEntityWorld() == client.world) {
            float elapsed = (float)(client.world.getTime() - started) + partial;
            if (elapsed >= 40.0F) {
               clear();
            } else {
               float t = Math.clamp(elapsed / 40.0F, 0.0F, 1.0F);
               float t2 = t * t;
               float t3 = t * t2;
               float phase = (10.25F * t3 * t2 - 24.95F * t2 * t2 + 25.5F * t3 - 13.8F * t2 + 4.0F * t) * 3.1415927F;
               float pulse = (float)Math.sin(phase);
               float wobble = Math.abs((float)Math.sin(phase * 2.0F));
               int width = client.getWindow().getScaledWidth();
               int height = client.getWindow().getScaledHeight();
               int dx = Math.round(wobbleX * width * 0.12F * wobble);
               int dy = Math.round(wobbleY * height * 0.12F * wobble);
               float envelope = Math.min(1.0F, elapsed / 3.0F) * Math.min(1.0F, (40.0F - elapsed) / 4.0F);
               float scale = Math.min(width, height) * (0.075F + 0.16F * pulse) * envelope;
               if (!(scale < 0.1F)) {
                  float tilt = (float)Math.toRadians(6.0 * Math.cos(t * 8.0F));
                  Quaternionf rotation = new Quaternionf()
                     .rotationZ(3.1415927F)
                     .rotateY((float)Math.toRadians(900.0F * Math.abs(pulse)))
                     .rotateX(tilt)
                     .rotateZ(tilt);
                  Vector3f offset = rotation.transform(new Vector3f(0.0F, 0.9F, 0.0F)).negate();
                  EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(prize);
                  EntityRenderState state = renderer.getAndUpdateRenderState(prize, 1.0F);
                  state.light = 15728880;
                  state.shadowRadius = 0.0F;
                  state.shadowPieces.clear();
                  state.displayName = null;
                  graphics.addEntity(state, scale, offset, rotation, new Quaternionf(), dx, dy, width + dx, height + dy);
               }
            }
         } else {
            clear();
         }
      }
   }

   static void clear() {
      if (prize != null) {
         prize.discard();
      }

      prize = null;
   }
}
