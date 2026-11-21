package com.baom5.deathanimation.client;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AfterEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class DeathSceneRenderer {
   private DeathSceneRenderer() {
   }

   public static float previewZoom() {
      DeathScene scene = DeathEffectController.hoverScene();
      if (scene == null) {
         return 22.0F;
      }

      return switch (scene.animation) {
         case GORY_EXPLOSION -> 10.0F;
         case BURN, ENDER -> 16.0F;
         default -> 22.0F;
      };
   }

   private static DeathSceneRenderer.Fit fit(DeathScene scene, float scale, int width, int height) {
      boolean miniatureFocus = scene.nestedDoll != null && scene.nestedDoll.zoomTinyPreview();
      Vector3f low = new Vector3f(1.0F / 0.0F);
      Vector3f high = new Vector3f(-1.0F / 0.0F);
      Quaternionf rotation = previewRotation(scene);
      Consumer<Vector3f> include = pointx -> {
         rotation.transform(pointx);
         low.min(pointx);
         high.max(pointx);
      };
      List<Float> shot = AnimationCatalog.contains(scene.animation) ? AnimationCatalog.recipe(scene.animation).previewBounds() : null;
      if (shot != null && shot.size() == 6) {
         for (int corner = 0; corner < 8; corner++) {
            include.accept(new Vector3f(shot.get((corner & 1) == 0 ? 0 : 3), shot.get((corner & 2) == 0 ? 1 : 4), shot.get((corner & 4) == 0 ? 2 : 5)));
         }

         Vector3f span = new Vector3f(high).sub(low);
         float fitted = Math.min(scale, Math.min(Math.max(1, width - 6) / Math.max(0.15F, span.x), Math.max(1, height - 6) / Math.max(0.15F, span.y)));
         return new DeathSceneRenderer.Fit(low.add(high).mul(-0.5F).setComponent(2, 0.0F), Math.max(1.0F, fitted));
      } else {
         if (scene.nestedDoll != null && scene.nestedDoll.shellBounds() != null) {
            Box bounds = scene.nestedDoll.shellBounds();

            for (int corner = 0; corner < 8; corner++) {
               include.accept(
                  new Vector3f(
                     (float)(((corner & 1) == 0 ? bounds.minX : bounds.maxX) - scene.x),
                     (float)(((corner & 2) == 0 ? bounds.minY : bounds.maxY) - scene.y),
                     (float)(((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) - scene.z)
                  )
               );
            }
         }

         if (scene.dustfallSand != null && scene.dustfallSand.sandScene()) {
            Box bounds = scene.dustfallSand.bounds(scene);

            for (int corner = 0; corner < 8; corner++) {
               include.accept(
                  new Vector3f(
                     (float)(((corner & 1) == 0 ? bounds.minX : bounds.maxX) - scene.x),
                     (float)(((corner & 2) == 0 ? bounds.minY : bounds.maxY) - scene.y),
                     (float)(((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) - scene.z)
                  )
               );
            }
         }

         if (scene.animation == DeathAnimationConfig.Animation.STATIC) {
            for (int corner = 0; corner < 8; corner++) {
               include.accept(new Vector3f((corner & 1) == 0 ? -1.85F : 1.85F, (corner & 2) == 0 ? 0.0F : 2.3F, (corner & 4) == 0 ? -1.25F : 1.25F));
            }
         }

         if (scene.bodyVisible() && scene.tint() >>> 24 > 2) {
            Acrobatics.posedVertices(scene, scene.root(), include);
         }

         if (scene.gold() != null && scene.gold().time() >= 64.0F) {
            Box bounds = scene.gold().bounds();

            for (int corner = 0; corner < 8; corner++) {
               include.accept(
                  new Vector3f(
                     (float)(((corner & 1) == 0 ? bounds.minX : bounds.maxX) - scene.x),
                     (float)(((corner & 2) == 0 ? bounds.minY : bounds.maxY) - scene.y),
                     (float)(((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) - scene.z)
                  )
               );
            }
         }

         for (Entity prop : scene.props) {
            PlayerActors.Binding actor = PlayerActors.forEntity(prop);
            if (!miniatureFocus || actor != null && scene.nestedDoll.isFinalActor(actor) && PlayerActors.visible(actor)) {
               if (actor != null) {
                  if (PlayerActors.visible(actor)) {
                     PlayerActors.vertices(actor, pointx -> include.accept(pointx.sub((float)scene.x, (float)scene.y, (float)scene.z)));
                  }
               } else if (!prop.isRemoved() && prop instanceof DisplayEntity display && display.getRenderState() != null) {
                  Matrix4fc matrix = ((AffineTransformation)display.getRenderState().transformation().interpolate(1.0F)).getMatrix();
                  if (!(matrix.getScale(new Vector3f()).lengthSquared() < 7.5E-5F)) {
                     if (prop instanceof TextDisplayEntity text) {
                        if (!scene.gui) {
                           TextRenderer font = MinecraftClient.getInstance().textRenderer;
                           List<OrderedText> lines = font.wrapLines(text.getText(), 200);
                           float halfWidth = (lines.stream().mapToInt(font::getWidth).max().orElse(1) + 2) * 0.0125F;
                           float textHeight = Math.max(1, lines.size()) * 0.25F;

                           for (int corner = 0; corner < 4; corner++) {
                              Vector3f point = new Vector3f((corner & 1) == 0 ? -halfWidth : halfWidth, (corner & 2) == 0 ? 0.0F : textHeight, 0.0F);
                              matrix.transformPosition(point);
                              point.add((float)(prop.getX() - scene.x), (float)(prop.getY() - scene.y), (float)(prop.getZ() - scene.z));
                              include.accept(point);
                           }
                        }
                     } else {
                        boolean item = prop instanceof ItemDisplayEntity;
                        NativePropBounds.Bounds nativeBounds = item ? NativePropBounds.of((ItemDisplayEntity)prop) : null;
                        if (!item || nativeBounds != null) {
                           for (int corner = 0; corner < 8; corner++) {
                              Vector3f point = new Vector3f((corner & 1) == 0 ? 0.0F : 1.0F, (corner & 2) == 0 ? 0.0F : 1.0F, (corner & 4) == 0 ? 0.0F : 1.0F);
                              if (item) {
                                 point.set(
                                    (corner & 1) == 0 ? nativeBounds.low().x : nativeBounds.high().x,
                                    (corner & 2) == 0 ? nativeBounds.low().y : nativeBounds.high().y,
                                    (corner & 4) == 0 ? nativeBounds.low().z : nativeBounds.high().z
                                 );
                              }

                              matrix.transformPosition(point);
                              point.add((float)(prop.getX() - scene.x), (float)(prop.getY() - scene.y), (float)(prop.getZ() - scene.z));
                              include.accept(point);
                           }
                        }
                     }
                  }
               }
            }
         }

         if (!Float.isFinite(low.x)) {
            return new DeathSceneRenderer.Fit(new Vector3f(0.0F, 0.9F, 0.0F), scale);
         }

         Vector3f span = new Vector3f(high).sub(low);
         float minimumSpan = miniatureFocus ? 1.0E-5F : 0.15F;
         float fitted = Math.min(
            scale, Math.min(Math.max(1, width - 6) / Math.max(minimumSpan, span.x), Math.max(1, height - 6) / Math.max(minimumSpan, span.y))
         );
         return new DeathSceneRenderer.Fit(low.add(high).mul(-0.5F).setComponent(2, 0.0F), Math.max(1.0F, fitted));
      }
   }

   public static void previewBody(DrawContext graphics, float scale, int x1, int y1, int x2, int y2) {
      DeathScene scene = DeathEffectController.hoverScene();
      if (scene != null) {
         if (scene.nestedDoll != null) {
            AnimationCatalog.animate(scene);
         } else {
            AnimationCatalog.animateFrame(scene);
         }

         if (HangingRagdoll.supports(scene.animation)) {
            AnimationCatalog.animate(scene);
         }

         EntityRenderer renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(scene.avatar);
         EntityRenderState state = renderer.getAndUpdateRenderState(scene.avatar, 1.0F);
         float adjustedScale = scene.nestedDoll == null ? scale : scene.nestedDoll.previewScale(scale);
         DeathSceneRenderer.Fit frame = fit(scene, adjustedScale, x2 - x1, y2 - y1);
         state.light = 15728880;
         state.shadowRadius = 0.0F;
         state.shadowPieces.clear();
         if (scene.animation == DeathAnimationConfig.Animation.HEAVEN && scene.progress() < 0.9F) {
            int center = (x1 + x2) / 2;
            graphics.fill(center - 2, y1, center + 2, y2, 1442836654);
         }

         graphics.addEntity(state, frame.scale, frame.offset, previewRotation(scene), new Quaternionf(), x1, y1, x2, y2);
         drawPreviewCaptions(graphics, scene, x1, y1, x2, y2);
      }
   }

   private static void drawPreviewCaptions(DrawContext graphics, DeathScene scene, int x1, int y1, int x2, int y2) {
      if (x2 - x1 > 4 && y2 - y1 > 4) {
         if (scene.clawMachine != null && scene.progress() >= 0.94F) {
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            String text = scene.clawMachine.claimedAge < 0.0F ? "PICK ME" : "PRIZE CLAIMED";
            float scale = Math.min(1.5F, (x2 - x1 - 4.0F) / Math.max(1, font.getWidth(text)));
            Matrix3x2fStack matrix = graphics.getMatrices();
            matrix.pushMatrix();
            matrix.translate((x1 + x2) * 0.5F, y1 + 3);
            matrix.scale(scale, scale);
            graphics.drawCenteredTextWithShadow(font, text, 0, 0, -6518);
            matrix.popMatrix();
         } else if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
            CalendarAgeOut.drawPreviewCaption(graphics, scene, x1, y1, x2 - x1, y2 - y1);
         } else if (scene.animation == DeathAnimationConfig.Animation.BATCH01_AIRLOCK_VERDICT) {
            AmongUsEjected.draw(graphics, scene, x1, y1, x2 - x1, y2 - y1);
         } else if (AnimationCatalog.contains(scene.animation)) {
            if (scene.shellGame() != null) {
               TextRenderer font = MinecraftClient.getInstance().textRenderer;
               String caption = scene.shellGame().caption();
               float textScale = Math.min(2.0F, Math.min((x2 - x1 - 4.0F) / Math.max(1, font.getWidth(caption)), (y2 - y1) * 0.12F / 9.0F));
               Matrix3x2fStack pose = graphics.getMatrices();
               pose.pushMatrix();
               pose.translate((x1 + x2) * 0.5F, y1 + 3.0F);
               pose.scale(textScale, textScale);
               graphics.drawCenteredTextWithShadow(font, caption, 0, 0, -5981);
               pose.popMatrix();
            } else {
               List<AnimationCatalog.Cue> cues = AnimationCatalog.recipe(scene.animation).props();
               boolean pilot = "Pilot 65".equals(AnimationCatalog.recipe(scene.animation).collection());
               float captionY = y1 + 3.0F;

               for (int i = 0; i < Math.min(cues.size(), scene.props.size()); i++) {
                  AnimationCatalog.Cue cue = cues.get(i);
                  if (cue.text() != null
                     && !(scene.progress() < cue.start())
                     && !(scene.progress() >= cue.end())
                     && scene.props.get(i) instanceof TextDisplayEntity text) {
                     TextRenderer font = MinecraftClient.getInstance().textRenderer;
                     float scale = Math.min(3.0F, Math.min((x2 - x1 - 4.0F) / Math.max(1, font.getWidth(text.getText())), (y2 - y1) * 0.075F / 9.0F));
                     Matrix3x2fStack pose = graphics.getMatrices();
                     pose.pushMatrix();
                     pose.translate((x1 + x2) * 0.5F, pilot ? captionY : y1 + 3.0F);
                     pose.scale(scale, scale);
                     graphics.drawCenteredTextWithShadow(font, text.getText(), 0, 0, -1);
                     pose.popMatrix();
                     if (pilot) {
                        captionY += 9.0F * scale + 1.0F;
                     }
                  }
               }
            }
         }
      }
   }

   public static void previewProps(DrawContext graphics, float scale, int x1, int y1, int x2, int y2) {
   }

   public static void submitPreviewProps(DeathScene scene, MatrixStack poses, OrderedRenderCommandQueue collector, CameraRenderState camera) {
      if (scene.gui || HangingRagdoll.supports(scene.animation)) {
         BilliardsTableModel.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         PhotocopierMassProduction.submitPrintedPages(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         PaperAirplaneMesh.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         PaperShredderMesh.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         NestedDollMesh.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         CalendarBoneDust.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         DustfallSand.submit(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         Kamehameha.submitBeam(scene, poses, collector, new Vec3d(scene.x, scene.y, scene.z));
         EntityRenderManager dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();

         for (Entity prop : scene.props) {
            if (!prop.isRemoved() && !(prop instanceof TextDisplayEntity)) {
               if (prop instanceof SkeletonEntity skeleton) {
                  submitCalendarSkeleton(scene, skeleton, poses, collector, camera, true);
               } else {
                  EntityRenderer renderer = dispatcher.getRenderer(prop);
                  EntityRenderState state = renderer.getAndUpdateRenderState(prop, 1.0F);
                  if (scene.gui) {
                     state.light = 15728880;
                  }

                  state.shadowRadius = 0.0F;
                  state.shadowPieces.clear();
                  poses.push();
                  poses.translate(prop.getX() - scene.x, prop.getY() - scene.y, prop.getZ() - scene.z);
                  renderer.render(state, poses, collector, camera);
                  poses.pop();
               }
            }
         }
      }
   }

   private static Quaternionf previewRotation(DeathScene scene) {
      float elevation = switch (scene.animation) {
         case BATCH01_BANK_SHOT -> -0.65F;
         case FLOWER_BLOOM, GOLD, PILOT_SHELL_GAME -> -0.45F;
         case GRAVE -> -0.35F;
         default -> 0.1F;
      };

      float yaw = switch (scene.animation) {
         case GOLD -> -0.9F;
         case BATCH01_KI_RECOIL -> -0.95F;
         default -> -0.28F;
      };
      return new Quaternionf().rotationZ(3.1415927F).rotateX(elevation).rotateY(yaw);
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES
         .register(
            (AfterEntities)context -> {
               for (DeathScene scene : DeathEffectController.scenes()) {
                  BilliardsTableModel.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  PhotocopierMassProduction.submitPrintedPages(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  PaperAirplaneMesh.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  PaperShredderMesh.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  NestedDollMesh.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  CalendarBoneDust.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  DustfallSand.submit(scene, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState.pos);
                  if (scene.animation == DeathAnimationConfig.Animation.PILOT_CALENDAR) {
                     SkeletonEntity skeleton = CalendarAgeOut.skeleton(scene);
                     if (skeleton != null) {
                        submitCalendarSkeleton(scene, skeleton, context.matrices(), context.commandQueue(), context.worldState().cameraRenderState, false);
                     }
                  }
               }

               if (DeathAnimationConfig.INSTANCE.visualEffects) {
                  MatrixStack poses = context.matrices();
                  Vec3d camera = context.worldState().cameraRenderState.pos;

                  for (DeathScene scene : DeathEffectController.scenes()) {
                     if (Kamehameha.supports(scene)) {
                        Kamehameha.submitBeam(scene, poses, context.commandQueue(), camera);
                     } else {
                        boolean drone = scene.animation == DeathAnimationConfig.Animation.BATCH01_DRONE_ELIMINATION;
                        if (scene.animation == DeathAnimationConfig.Animation.HEAVEN || drone) {
                           float p = scene.progress();
                           float strength = drone
                              ? AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.25F, 0.3F))
                                 * (1.0F - AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.76F, 0.8F)))
                              : AnimationTimeline.smooth(AnimationTimeline.phase(p, 0.0F, 0.18F)) * (1.0F - AnimationTimeline.phase(p, 0.8F, 1.0F));
                           if (!(strength < 0.01F)) {
                              poses.push();
                              Vector3f beam = drone ? FortniteElimination.beamCenter(scene) : new Vector3f();
                              poses.translate(scene.x + beam.x - camera.x - 0.5, scene.y + beam.y - camera.y, scene.z + beam.z - camera.z - 0.5);
                              BeaconBlockEntityRenderer.renderBeam(
                                 poses,
                                 context.commandQueue(),
                                 BeaconBlockEntityRenderer.BEAM_TEXTURE,
                                 1.0F,
                                 scene.age,
                                 0,
                                 drone ? 3 : 12,
                                 drone ? -13254657 : -3646,
                                 (drone ? 0.09F : 0.16F) * strength,
                                 (drone ? 0.95F : 0.38F) * strength
                              );
                              poses.pop();
                           }
                        }
                     }
                  }
               }
            }
         );
   }

   private static void submitCalendarSkeleton(
      DeathScene scene, SkeletonEntity skeleton, MatrixStack poses, OrderedRenderCommandQueue collector, CameraRenderState camera, boolean preview
   ) {
      float scale = CalendarAgeOut.skeletonScale(scene);
      if (!skeleton.isInvisible() && !(scale < 0.005F)) {
         EntityRenderer renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(skeleton);
         EntityRenderState state = renderer.getAndUpdateRenderState(skeleton, 1.0F);
         CalendarAgeOut.trackSkeleton(state, scene);
         if (scene.gui) {
            state.light = 15728880;
         }

         state.shadowRadius = 0.0F;
         state.shadowPieces.clear();
         poses.push();
         if (preview) {
            poses.translate(skeleton.getX() - scene.x, skeleton.getY() - scene.y, skeleton.getZ() - scene.z);
         } else {
            poses.translate(skeleton.getX() - camera.pos.x, skeleton.getY() - camera.pos.y, skeleton.getZ() - camera.pos.z);
         }

         poses.translate(0.0F, 0.98F - CalendarAgeOut.skeletonBottom(scene), 0.0F);
         poses.multiply(new Quaternionf().rotationY((float)Math.toRadians(scene.sceneFacing)));
         poses.multiply(new Quaternionf().rotationZ((float)Math.toRadians(CalendarAgeOut.skeletonFall(scene))));
         poses.multiply(new Quaternionf().rotationY(-((float)Math.toRadians(scene.sceneFacing))));
         poses.scale(scale, scale, scale);
         poses.translate(0.0F, -0.98F, 0.0F);
         renderer.render(state, poses, collector, camera);
         poses.pop();
      }
   }

   @Environment(EnvType.CLIENT)
   private record Fit(Vector3f offset, float scale) {
   }
}
