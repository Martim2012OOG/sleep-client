package com.baom5.deathanimation.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class DeathanimationClient implements ClientModInitializer {
   private static final Category CATEGORY = Category.create(Identifier.of("deathanimation", "main"));
   public static final KeyBinding OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.deathanimation.open_menu", 79, CATEGORY));
   public static final KeyBinding PREVIEW = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.deathanimation.preview", 80, CATEGORY));

   public static void resetKeybinds() {
      OPEN_MENU.setBoundKey(OPEN_MENU.getDefaultKey());
      PREVIEW.setBoundKey(PREVIEW.getDefaultKey());
      OPEN_MENU.setPressed(false);
      PREVIEW.setPressed(false);
      KeyBinding.updateKeysByCode();
      MinecraftClient.getInstance().options.write();
   }

   public void onInitializeClient() {
      GoldMaterial.init();
      PlayerDeathDetection.register();
      DeathEffectController.register();
      DeathSceneRenderer.register();
      FnafBiteFlicker.register();
      AmongUsEjected.register();
      ShellGame.register();
      PlayerPrizeActivation.register();
      if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("deathanimation.deathReview")) {
         try {
            Class.forName("com.baom5.deathanimation.client.DeathDetectionReview").getMethod("register").invoke(null);
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Death detection review tools were not compiled", exception);
         }
      }

      if (FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("deathanimation.review")) {
         try {
            Class.forName("com.baom5.deathanimation.client.AnimationReview").getMethod("register").invoke(null);
         } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Animation review tools were not compiled", exception);
         }
      }

      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         while (PREVIEW.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
               DeathEffectController.startPreview();
            }
         }

         while (OPEN_MENU.wasPressed()) {
            if (client.currentScreen == null) {
               client.setScreen(new DeathAnimationsScreen());
            }
         }
      });
   }
}
