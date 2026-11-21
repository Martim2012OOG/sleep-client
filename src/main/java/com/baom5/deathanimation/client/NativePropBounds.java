package com.baom5.deathanimation.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
final class NativePropBounds {
   private static final NativePropBounds.Bounds UNIT = new NativePropBounds.Bounds(new Vector3f(-0.5F), new Vector3f(0.5F));
   private static final Map<Identifier, NativePropBounds.Bounds> CACHE = new HashMap<>();

   private NativePropBounds() {
   }

   static NativePropBounds.Bounds of(ItemDisplayEntity display) {
      Identifier id = (Identifier)display.getItemStack().get(DataComponentTypes.ITEM_MODEL);
      if (id != null && id.getNamespace().equals("deathanimation")) {
         if (!CACHE.containsKey(id)) {
            CACHE.put(id, load(id));
         }

         return CACHE.get(id);
      } else {
         return UNIT;
      }
   }

   private static Vector3f vector(JsonArray array, Vector3f fallback) {
      return array == null ? new Vector3f(fallback) : new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
   }

   private static NativePropBounds.Bounds load(Identifier id) {
      try (InputStream stream = NativePropBounds.class.getResourceAsStream("/assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json")) {
         if (stream == null) {
            return UNIT;
         }

         JsonObject model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
         if (!model.has("elements")) {
            return UNIT;
         }

         JsonArray elements = model.getAsJsonArray("elements");
         if (elements.isEmpty()) {
            return null;
         }

         JsonObject fixed = model.has("display") ? model.getAsJsonObject("display").getAsJsonObject("fixed") : null;
         Vector3f angles = fixed == null ? new Vector3f() : vector(fixed.getAsJsonArray("rotation"), new Vector3f());
         Vector3f scale = fixed == null ? new Vector3f(1.0F) : vector(fixed.getAsJsonArray("scale"), new Vector3f(1.0F));
         Vector3f translation = fixed == null ? new Vector3f() : vector(fixed.getAsJsonArray("translation"), new Vector3f()).div(16.0F);
         Quaternionf rotation = new Quaternionf()
            .rotationXYZ((float)Math.toRadians(angles.x), (float)Math.toRadians(angles.y), (float)Math.toRadians(angles.z));
         Quaternionf displayRotation = new Quaternionf().rotationY(3.1415927F);
         Vector3f low = new Vector3f(1.0F / 0.0F);
         Vector3f high = new Vector3f(-1.0F / 0.0F);

         for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            Vector3f a = vector(element.getAsJsonArray("from"), new Vector3f());
            Vector3f b = vector(element.getAsJsonArray("to"), new Vector3f());

            for (int corner = 0; corner < 8; corner++) {
               Vector3f point = new Vector3f((corner & 1) == 0 ? a.x : b.x, (corner & 2) == 0 ? a.y : b.y, (corner & 4) == 0 ? a.z : b.z);
               if (element.has("rotation")) {
                  JsonObject turn = element.getAsJsonObject("rotation");
                  Vector3f origin = vector(turn.getAsJsonArray("origin"), new Vector3f(8.0F));
                  float angle = (float)Math.toRadians(turn.get("angle").getAsFloat());
                  point.sub(origin);
                  switch (turn.get("axis").getAsString()) {
                     case "x":
                        point.rotateX(angle);
                        break;
                     case "y":
                        point.rotateY(angle);
                        break;
                     case "z":
                        point.rotateZ(angle);
                  }

                  point.add(origin);
               }

               point.div(16.0F).sub(0.5F, 0.5F, 0.5F).mul(scale);
               rotation.transform(point);
               point.add(translation);
               displayRotation.transform(point);
               low.min(point);
               high.max(point);
            }
         }

         return new NativePropBounds.Bounds(low, high);
      } catch (Exception exception) {
         System.err.println("[Death Animations +] Using default preview bounds for " + id + ": " + exception.getMessage());
         return UNIT;
      }
   }

   @Environment(EnvType.CLIENT)
   record Bounds(Vector3f low, Vector3f high) {
   }
}
