package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
final class PreviewEffects {
   private PreviewEffects() {
   }

   static void draw(DrawContext graphics, DeathScene scene, int x1, int y1, int x2, int y2) {
      if (scene != null
         && DeathAnimationConfig.INSTANCE.visualEffects
         && scene.animation != DeathAnimationConfig.Animation.RAGDOLL
         && !scene.isJointed()
         && scene.animation != DeathAnimationConfig.Animation.NONE) {
         if (scene.gold() == null || !(scene.age < 6.0F) && !(scene.age >= 54.0F)) {
            float p = scene.progress();
            float time = scene.age;
            float burst = -1.0F;
            String var10000;
            if (AnimationCatalog.contains(scene.animation)) {
               var10000 = AnimationCatalog.recipe(scene.animation).particle();
            } else {
               switch (scene.animation) {
                  case BURN:
                     var10000 = p < 0.3F ? "flame" : "smoke";
                     break;
                  case GHOST:
                     var10000 = "soul";
                     break;
                  case HEAVEN:
                  case GOLD:
                  case TOTEM:
                     var10000 = "glow";
                     break;
                  case FREEZE:
                     var10000 = "snow";
                     break;
                  case VOID:
                  case ENDER:
                  case OPPONENT:
                     var10000 = "portal";
                     break;
                  default:
                     var10000 = "cloud";
               }
            }

            String kind = var10000;
            if (AnimationCatalog.contains(scene.animation)
               && AnimationCatalog.recipe(scene.animation).events() != null
               && !kind.equals("wind")
               && !kind.equals("water_vortex")) {
               kind = "none";

               for (AnimationCatalog.EffectCue cue : AnimationCatalog.recipe(scene.animation).events()) {
                  if (cue.count() > 0 && cue.particle() != null && !cue.particle().equals("none") && p >= cue.p() && p < cue.p() + 0.12F) {
                     kind = cue.particle();
                     burst = AnimationTimeline.phase(p, cue.p(), cue.p() + 0.12F);
                  }
               }
            }

            if (kind.equals("wind") || kind.equals("water_vortex")) {
               swirl(graphics, scene, x1, y1, x2, y2, kind.equals("wind"));
            } else if (!AnimationCatalog.contains(scene.animation) || !kind.equals("none") && (!kind.equals("cloud") || !(burst < 0.0F))) {
               switch (scene.animation) {
                  case EXPLOSION:
                     burst = AnimationTimeline.phase(p, 0.72F, 1.0F);
                     break;
                  case GORY_EXPLOSION:
                     burst = AnimationTimeline.phase(p, 0.08F, 0.5F);
                     break;
                  case GRAVE:
                     burst = AnimationTimeline.phase(p, 0.08F, 0.5F);
                     break;
                  case ANVIL:
                     burst = AnimationTimeline.phase(p, 0.34F, 0.8F);
               }

               if (burst != 0.0F && !(burst >= 1.0F) && (scene.animation != DeathAnimationConfig.Animation.BURN || !(p > 0.55F))) {
                  String texture = kind.equals("flame") ? "flame" : (kind.equals("soul") ? "soul_fire_flame" : (kind.equals("note") ? "note" : "generic_0"));
                  Identifier sprite = Identifier.ofVanilla("textures/particle/" + texture + ".png");

                  int rgb = switch (kind) {
                     case "portal" -> 12882167;
                     case "glow" -> 16773539;
                     case "bubble", "snow", "soul" -> 11071999;
                     case "sand" -> 14073472;
                     case "smoke" -> 7829367;
                     default -> 16777215;
                  };
                  float width = x2 - x1;
                  float height = y2 - y1;
                  int count = kind.equals("poof") ? 16 : (scene.animation == DeathAnimationConfig.Animation.GORY_EXPLOSION ? 5 : 7);

                  for (int i = 0; i < count; i++) {
                     float life = burst >= 0.0F ? burst : (time * 0.035F + i * 0.127F) % 1.0F;
                     float angle = i * 2.39996F + (float)(scene.seed % 31L);
                     float radius = burst >= 0.0F ? life * width * 0.42F : width * 0.28F;
                     int px = Math.round((x1 + x2) * 0.5F + (float)Math.cos(angle) * radius);
                     int py = Math.round((y1 + y2) * 0.5F + (burst >= 0.0F ? (float)Math.sin(angle) * radius : height * (0.45F - life) * 0.7F));
                     int size = Math.max(2, Math.round(width * (!kind.equals("flame") && !kind.equals("poof") ? 0.08F : 0.16F) * (1.0F - life * 0.5F)));
                     int alpha = Math.round(200.0F * (1.0F - life));
                     int spriteSize = texture.equals("generic_0") ? 8 : 16;
                     graphics.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        sprite,
                        px - size / 2,
                        py - size / 2,
                        0.0F,
                        0.0F,
                        size,
                        size,
                        spriteSize,
                        spriteSize,
                        spriteSize,
                        spriteSize,
                        alpha << 24 | rgb
                     );
                  }
               }
            }
         }
      }
   }

   private static void swirl(DrawContext graphics, DeathScene scene, int x1, int y1, int x2, int y2, boolean wind) {
      float p = scene.progress();
      if (!wind) {
         waterFunnel(graphics, scene, x1, y1, x2, y2);
      } else if (!(p < 0.08F) && !(p > 0.7F)) {
         float w = x2 - x1;
         float h = y2 - y1;
         Identifier sprite = Identifier.ofVanilla("textures/particle/generic_0.png");
         int size = Math.max(3, Math.round(w * 0.035F));
         int count = 6;

         for (int i = 0; i < count; i++) {
            float angle = scene.age * 0.25F + i * 1.0472F;
            float rise = (i / 5.0F - 0.5F) * h * 0.48F;
            float radius = w * (0.12F + i * 0.02F);
            int px = Math.round((x1 + x2) * 0.5F + (float)Math.cos(angle) * radius);
            int py = Math.round((y1 + y2) * 0.5F + rise + (float)Math.sin(angle) * h * 0.045F);
            int color = -2137410881;
            int textureSize = 8;
            graphics.drawTexture(
               RenderPipelines.GUI_TEXTURED,
               sprite,
               px - size / 2,
               py - size / 2,
               0.0F,
               0.0F,
               size,
               size,
               textureSize,
               textureSize,
               textureSize,
               textureSize,
               color
            );
         }
      }
   }

   private static void waterFunnel(DrawContext graphics, DeathScene scene, int x1, int y1, int x2, int y2) {
      WhirlpoolMotion.Flow flow = WhirlpoolMotion.flow(scene, scene.progress());
      float formation = 0.22F + 0.78F * AnimationTimeline.smooth(AnimationTimeline.phase(flow.progress(), 0.015F, 0.22F));
      float fade = WhirlpoolMotion.funnelFade(scene) * formation;
      if (!(fade <= 0.015F)) {
         float w = x2 - x1;
         float h = y2 - y1;
         float cx = (x1 + x2) * 0.5F;
         float cy = y1 + h * 0.78F;
         float funnelWidth = 0.29F;
         float funnelHeight = 0.08F + 0.18F * flow.tightening();
         Identifier sprite = Identifier.ofVanilla("textures/particle/bubble.png");
         int segments = 8;
         int layers = 6;

         for (int layer = 0; layer < layers; layer++) {
            float v = (float)layer / (layers - 1);
            float radius = w * (0.025F + funnelWidth * (float)Math.pow(v, 0.8799999952316284));
            float level = -h * funnelHeight * v;

            for (int i = 0; i < segments; i++) {
               float angle = flow.angle() + i * (float)(6.283185307179586 / segments) + v * 3.5F;
               float px = cx + (float)Math.sin(angle) * radius;
               float py = cy + level + (float)Math.cos(angle) * radius * 0.28F;
               int size = Math.max(2, Math.round(w * (0.026F - 0.009F * v) * fade));
               int alpha = Math.round(220.0F * fade * (0.72F + 0.28F * (1.0F - v)));
               int color = alpha << 24 | 15203071;
               graphics.drawTexture(
                  RenderPipelines.GUI_TEXTURED,
                  sprite,
                  Math.round(px - size * 0.5F),
                  Math.round(py - size * 0.5F),
                  0.0F,
                  0.0F,
                  size,
                  size,
                  16,
                  16,
                  16,
                  16,
                  color
               );
            }
         }

         int coreSize = Math.max(3, Math.round(w * 0.045F * fade));
         graphics.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            sprite,
            Math.round(cx - coreSize * 0.5F),
            Math.round(cy - coreSize * 0.5F),
            0.0F,
            0.0F,
            coreSize,
            coreSize,
            16,
            16,
            16,
            16,
            Math.round(185.0F * fade) << 24 | 13235455
         );
      }
   }
}
