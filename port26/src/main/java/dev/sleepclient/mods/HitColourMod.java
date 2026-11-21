package dev.sleepclient.mods;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sleepclient.option.Option;
import java.lang.reflect.Field;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Changes the colour of the red "hurt" overlay by rewriting the overlay texture. */
public class HitColourMod extends Mod {

	private static final int VANILLA = 0xB2FF0000;
	private static Object overlay;
	private static int applied = VANILLA;

	public static HitColourMod instance;

	private final Option.Col colour = colour("Colour", VANILLA);

	public HitColourMod() {
		super("Hit Colour", "hit_colour", "Customise hit colour.", Category.VISUAL);
		instance = this;
	}

	/** Called by OverlayTextureMixin when the vanilla overlay texture is created. */
	public static void onCreate(Object overlayTexture) {
		overlay = overlayTexture;
		applied = VANILLA;
	}

	private static void write(int argb) {
		if (overlay == null || applied == argb) {
			return;
		}
		try {
			for (Field f : overlay.getClass().getDeclaredFields()) {
				if (DynamicTexture.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					DynamicTexture tex = (DynamicTexture) f.get(overlay);
					NativeImage img = tex.getPixels();
					if (img != null) {
						for (int y = 0; y < 8; y++) {
							for (int x = 0; x < 16; x++) {
								img.setPixel(x, y, argb);
							}
						}
						tex.upload();
						applied = argb;
					}
					return;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Sleep] Hit Colour failed: " + e);
			applied = argb;
		}
	}

	/** Called every tick: decides the overlay colour (Overlay mod can disable it completely). */
	public static void refresh() {
		int desired = VANILLA;
		if (OverlayMod.disableDamage()) {
			desired = 0;
		} else if (instance != null && instance.enabled) {
			desired = instance.colour.c();
		}
		write(desired);
	}
}
