package com.baom5.deathanimation.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class SandRenderMath {
   static int alpha(double now, double landed) {
      if (!(landed < 0.0) && !(now - landed <= 100.0)) {
         float p = Math.min(1.0F, ((float)(now - landed) - 100.0F) / 20.0F);
         return Math.round(255.0F * (1.0F - p * p * (3.0F - 2.0F * p)));
      } else {
         return 255;
      }
   }

   static int visibleFaces(double cameraX, double cameraY, double cameraZ, float x, float y, float z, float hx, float hy, float hz) {
      int faces = 0;
      if (cameraX < x - hx) {
         faces |= 1;
      } else if (cameraX > x + hx) {
         faces |= 2;
      }

      if (cameraY < y - hy) {
         faces |= 4;
      } else if (cameraY > y + hy) {
         faces |= 8;
      }

      if (cameraZ < z - hz) {
         faces |= 16;
      } else if (cameraZ > z + hz) {
         faces |= 32;
      }

      return faces;
   }
}
