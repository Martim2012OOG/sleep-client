package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Custom Totem: when a totem saves you, your own picture pops up instead of the vanilla totem. Use your face, or any
 * image you like (a pixel-art totem, a logo, a photo) - choose a file or drop it on the window while this card is open.
 * Pick how it moves (pop, spin, bounce, shake, rise, zoom), how big it is and how long it stays.
 */
public class SkinTotemMod extends Mod {

	public static SkinTotemMod instance;

	private static final Identifier TEX_ID = Identifier.of("sleep_client", "totem/custom");

	private final Option.Choice source = choice("Picture", 0, "Your Face", "My Own Image");
	private final Option.Action pick = action("My Image", "Choose...", this::pickFile, () -> this.status);
	private final Option.Text path = text("Image File", "", 300, "Paste a path, or drop a picture on the window");
	private final Option.Choice anim = choice("Animation", 0, "Pop", "Spin", "Bounce", "Shake", "Rise", "Zoom");
	private final Option.Num size = num("Size", 100, 20, 500, 10, "%");
	private final Option.Num duration = num("Duration", 1.6F, 0.6F, 4F, 0.1F, " s");
	private final Option.Bool glow = bool("Glow Behind", true).tip("A soft golden light behind the picture.");
	private final Option.Bool sparkles = bool("Sparkles", true).tip("Little stars that fly around the picture.");
	private final Option.Bool blink = bool("Blink Out", true).tip("The picture blinks a few times before it goes (like the vanilla totem).");

	private long start;
	private String status = "No image yet";
	private NativeImageBackedTexture tex;
	private int tw, th;
	private String loadedPath = "";
	private boolean busy;

	public SkinTotemMod() {
		super("Custom Totem", "skin_totem", "Your face or your own picture pops up when a totem saves you. Pick the animation.", Category.VISUAL);
		instance = this;
		pick.showIf(() -> source.idx() == 1);
		path.showIf(() -> source.idx() == 1);
	}

	/** true = replace the vanilla totem animation. */
	public static boolean replace(net.minecraft.item.ItemStack stack) {
		if (instance == null || !instance.enabled || stack == null || !stack.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) return false;
		instance.start = System.currentTimeMillis();
		return true;
	}

	// ---------------------------------------------------------------- choosing the image
	private void pickFile() {
		if (busy) {
			return;
		}
		busy = true;
		status = "Choosing...";
		Thread t = new Thread(() -> {
			try {
				String p;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(5);
					filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.bmp")).put(stack.UTF8("*.gif"));
					filters.flip();
					p = TinyFileDialogs.tinyfd_openFileDialog("Choose your totem picture", "", filters, "Images", false);
				} catch (Throwable e) {
					status = "No file picker: drop a picture on the window";
					return;
				}
				if (p == null || p.isBlank()) {
					status = loadedPath.isEmpty() ? "No image yet" : status;
					return;
				}
				use(Path.of(p));
			} finally {
				busy = false;
			}
		}, "kubo-totem-picker");
		t.setDaemon(true);
		t.start();
	}

	/** A picture dropped on the window while this card is open. */
	@Override
	public boolean onFileDropped(Path file) {
		String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif"))) {
			return false;
		}
		use(file);
		return true;
	}

	/** Copies the picture next to the config (so it survives if you move the original) and loads it. */
	private void use(Path src) {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("kubo-totem");
			Files.createDirectories(dir);
			String n = src.getFileName().toString();
			String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".png";
			Path dst = dir.resolve("totem" + ext);
			Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
			path.set(dst.toString());
			source.set(1);
			mc.execute(this::load);
		} catch (Exception e) {
			status = "Could not read that file";
		}
	}

	/** Reads the file in the "Image File" box into a texture (runs on the game thread). */
	private void load() {
		String p = path.get().trim();
		if (p.isEmpty()) {
			status = "No image yet";
			return;
		}
		try {
			Path f = Path.of(p);
			if (!Files.isRegularFile(f)) {
				status = "File not found";
				return;
			}
			BufferedImage bi = javax.imageio.ImageIO.read(f.toFile());
			if (bi == null) {
				status = "That is not a picture";
				return;
			}
			int w = bi.getWidth(), h = bi.getHeight();
			if (w > 2048 || h > 2048) {
				status = "Too big (max 2048 px)";
				return;
			}
			NativeImage img = new NativeImage(w, h, true);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					img.setColorArgb(x, y, bi.getRGB(x, y));
				}
			}
			if (tex != null) {
				mc.getTextureManager().destroyTexture(TEX_ID);
				tex = null;
			}
			tex = new NativeImageBackedTexture(() -> "kubo_totem", img);
			tex.upload();
			mc.getTextureManager().registerTexture(TEX_ID, tex);
			tw = w;
			th = h;
			loadedPath = p;
			status = "Loaded " + w + " x " + h;
		} catch (Throwable t) {
			status = "Could not load that picture";
		}
	}

	// ---------------------------------------------------------------- drawing
	private static float easeOutBack(float t) {
		float c1 = 1.70158F, c3 = c1 + 1F;
		return 1F + c3 * (float) Math.pow(t - 1F, 3) + c1 * (float) Math.pow(t - 1F, 2);
	}

	public void draw(DrawContext c) {
		long t = System.currentTimeMillis() - start;
		long total = Math.round(duration.f() * 1000F);
		if (start == 0 || t > total || mc.player == null) return;
		boolean own = source.idx() == 1;
		if (own && !path.get().trim().isEmpty() && (tex == null || !path.get().trim().equals(loadedPath))) {
			load();
		}
		if (own && tex == null) {
			own = false; // no picture yet: show your face so the effect still plays
		}
		float f = t / (float) total;
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		float base = 96F * size.f() / 100F;

		float scale, rot = 0F, ox = 0F, oy = 0F, alpha = 1F;
		float grow = Math.min(1F, f / 0.2F);
		switch (anim.idx()) {
			case 1 -> { // spin: spins in, settles, then fades
				float s = Math.min(1F, f / 0.55F);
				scale = easeOutBack(Math.min(1F, f / 0.25F)) * (0.6F + 0.4F * s);
				rot = (1F - s) * (float) (Math.PI * 4);
			}
			case 2 -> { // bounce
				scale = easeOutBack(grow);
				oy = -Math.abs((float) Math.sin(f * Math.PI * 3)) * 34F * (1F - f);
			}
			case 3 -> { // shake
				scale = easeOutBack(grow);
				ox = (float) Math.sin(t * 0.07) * 7F * (1F - f);
				rot = (float) Math.sin(t * 0.05) * 0.12F * (1F - f);
			}
			case 4 -> { // rise
				scale = easeOutBack(grow);
				oy = -f * 70F;
			}
			case 5 -> { // zoom
				scale = 0.25F + 1.25F * f;
			}
			default -> scale = grow < 1F ? easeOutBack(grow) : 1F + 0.05F * (float) Math.sin(f * 9F); // pop
		}
		if (f > 0.75F) {
			alpha = Math.max(0F, 1F - (f - 0.75F) / 0.25F);
		}
		if (blink.on() && f > 0.8F && (t / 80) % 2 == 0) {
			return;
		}
		float cx = w / 2F + ox, cy = h / 2F - 8F + oy;
		if (glow.on()) {
			int a = Math.round(alpha * 70);
			for (int i = 5; i >= 1; i--) {
				Gfx.circle(c, cx, cy, base * 0.55F * scale * (0.6F + i * 0.22F), Gfx.withAlpha(0xFFD24A, a * (6 - i) / 5));
			}
		}
		var ms = c.getMatrices();
		ms.pushMatrix();
		ms.translate(cx, cy);
		if (rot != 0F) {
			ms.rotate(rot);
		}
		ms.scale(scale, scale);
		int col = Math.round(alpha * 255F) << 24 | 0xFFFFFF;
		try {
			if (own) {
				float k = base / Math.max(tw, th);
				int dw = Math.max(1, Math.round(tw * k)), dh = Math.max(1, Math.round(th * k));
				c.drawTexture(RenderPipelines.GUI_TEXTURED, TEX_ID, -dw / 2, -dh / 2, 0F, 0F, dw, dh, tw, th, tw, th, col);
			} else {
				int s = Math.round(base);
				net.minecraft.client.gui.PlayerSkinDrawer.draw(c, mc.player.getSkin(), -s / 2, -s / 2, s);
			}
		} catch (Throwable ignored) {
			// skin / picture not ready
		}
		ms.popMatrix();
		if (sparkles.on()) {
			stars(c, cx, cy, base * scale, t, alpha);
		}
	}

	/** Little four-pointed stars flying out of the picture. */
	private void stars(DrawContext c, float cx, float cy, float r, long t, float alpha) {
		for (int i = 0; i < 12; i++) {
			float ph = (t / 900F + i * 0.37F) % 1F;
			float ang = i * 2.399F + t / 1400F;
			float d = r * (0.45F + 0.75F * ph);
			float x = cx + (float) Math.cos(ang) * d, y = cy + (float) Math.sin(ang) * d * 0.9F;
			float a = (float) Math.sin(ph * Math.PI) * alpha;
			int col = Gfx.withAlpha(i % 3 == 0 ? 0xFFFFFF : 0xFFE27A, Math.round(a * 255F));
			int s = 1 + (i % 3);
			int ix = Math.round(x), iy = Math.round(y);
			c.fill(ix - s, iy, ix + s + 1, iy + 1, col);
			c.fill(ix, iy - s, ix + 1, iy + s + 1, col);
		}
	}
}
