package dev.sleepclient.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dev.sleepclient.Lang;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * "Import world" and "Export world" buttons on the Select World screen.
 * Import: pick a .zip with a world inside (level.dat at the top or in one folder, like downloaded maps) and it is unpacked
 * into saves/. Export: the selected world is zipped (without session.lock) to a file you choose (Downloads by default).
 */
public final class WorldTransfer {

	/** Only one import or export at a time. */
	private static final AtomicBoolean BUSY = new AtomicBoolean();
	/** Biggest world we unpack (stops broken or hostile zips from filling the disk). */
	private static final long MAX_UNPACKED = 8L * 1024 * 1024 * 1024;

	private WorldTransfer() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof SelectWorldScreen)) {
				return;
			}
			try {
				addButtons(client, screen, w);
			} catch (Throwable t) {
				System.err.println("[Sleep] world import/export buttons failed: " + t);
			}
		});
	}

	private static void addButtons(MinecraftClient mc, Screen screen, int w) {
		// beside the search box: Import on its left, Export on its right; top corners if the window is too narrow
		int bh = 20, ix, ex, iy, bw;
		net.minecraft.client.gui.widget.TextFieldWidget search = null;
		for (var e : screen.children()) {
			if (e instanceof net.minecraft.client.gui.widget.TextFieldWidget tf) {
				search = tf;
				break;
			}
		}
		int side = search == null ? 0 : Math.min(search.getX(), w - search.getX() - search.getWidth()) - 12;
		if (side >= 64) {
			bw = Math.min(110, side);
			iy = search.getY() + (search.getHeight() - bh) / 2;
			ix = search.getX() - 6 - bw;
			ex = search.getX() + search.getWidth() + 6;
		} else {
			bw = Math.min(110, Math.max(64, w / 2 - 90));
			iy = 4;
			ix = 6;
			ex = w - 6 - bw;
		}
		ButtonWidget imp = ButtonWidget.builder(Text.literal(Lang.tr("Import world", "Importar mundo")), b -> importWorld(mc, screen))
				.dimensions(ix, iy, bw, bh).build();
		imp.setTooltip(Tooltip.of(Text.literal(Lang.tr("Add a world from a .zip file (downloaded maps too)",
				"Adiciona um mundo a partir de um ficheiro .zip (mapas descarregados também)"))));
		ButtonWidget exp = ButtonWidget.builder(Text.literal(Lang.tr("Export world", "Exportar mundo")), b -> exportWorld(mc, screen))
				.dimensions(ex, iy, bw, bh).build();
		exp.setTooltip(Tooltip.of(Text.literal(Lang.tr("Save the selected world as a .zip file", "Guarda o mundo selecionado num ficheiro .zip"))));
		Screens.getButtons(screen).add(imp);
		Screens.getButtons(screen).add(exp);
		ScreenEvents.afterTick(screen).register(sc -> {
			WorldListWidget list = list(sc);
			exp.active = !BUSY.get() && list != null && list.getSelectedAsOptional().isPresent();
			imp.active = !BUSY.get();
		});
	}

	private static WorldListWidget list(Screen screen) {
		for (var e : screen.children()) {
			if (e instanceof WorldListWidget l) {
				return l;
			}
		}
		return null;
	}

	private static void toast(MinecraftClient mc, String title, String desc) {
		mc.execute(() -> SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(title), Text.literal(desc)));
	}

	/** Reloads the world list if the Select World screen is still open. */
	private static void reload(MinecraftClient mc, Screen screen) {
		mc.execute(() -> {
			if (mc.currentScreen == screen) {
				WorldListWidget l = list(screen);
				if (l != null) {
					l.refresh();
				}
			}
		});
	}

	// ================================================================ import
	private static void importWorld(MinecraftClient mc, Screen screen) {
		if (!BUSY.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				String path;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(1);
					filters.put(stack.UTF8("*.zip"));
					filters.flip();
					path = TinyFileDialogs.tinyfd_openFileDialog(Lang.tr("Choose a world (.zip)", "Escolhe um mundo (.zip)"), "", filters, "ZIP", false);
				}
				if (path == null || path.isBlank()) {
					return;
				}
				toast(mc, Lang.tr("Importing world...", "A importar o mundo..."), Path.of(path).getFileName().toString());
				String name = unpack(mc, Path.of(path));
				toast(mc, Lang.tr("World imported!", "Mundo importado!"), name);
				reload(mc, screen);
			} catch (Throwable e) {
				System.err.println("[Sleep] world import failed: " + e);
				toast(mc, Lang.tr("Could not import the world", "Não deu para importar o mundo"), String.valueOf(e.getMessage()));
			} finally {
				BUSY.set(false);
			}
		}, "Sleep-world-import");
		t.setDaemon(true);
		t.start();
	}

	/** Unpacks the world in the zip into saves/ and returns the new folder name. */
	static String unpack(MinecraftClient mc, Path zip) throws IOException {
		Path saves = mc.getLevelStorage().getSavesDirectory();
		return unpack(zip, saves);
	}

	/** Separate from Minecraft so it can be tested on its own. */
	static String unpack(Path zip, Path saves) throws IOException {
		return unpack(zip, saves, null);
	}

	/**
	 * Unpacks the world in a zip into {@code saves}. {@code wanted} = folder name to use (null: the name in the zip);
	 * " (2)" etc. is added when it is taken. Returns the folder name. Used by add-ons that ship a map.
	 */
	public static String unpack(Path zip, Path saves, String wanted) throws IOException {
		try (ZipFile zf = new ZipFile(zip.toFile())) {
			// the world root is the folder with the shallowest level.dat (ignoring the macOS __MACOSX junk)
			String root = null;
			for (ZipEntry e : java.util.Collections.list(zf.entries())) {
				String n = e.getName().replace('\\', '/');
				if (e.isDirectory() || n.startsWith("__MACOSX/") || !(n.equals("level.dat") || n.endsWith("/level.dat"))) {
					continue;
				}
				String r = n.substring(0, n.length() - "level.dat".length());
				if (root == null || r.split("/", -1).length < root.split("/", -1).length) {
					root = r;
				}
			}
			if (root == null) {
				throw new IOException(Lang.tr("No world in this zip (no level.dat)", "Este zip não tem nenhum mundo (falta o level.dat)"));
			}
			String base = wanted != null && !wanted.isBlank() ? wanted : root.isEmpty() ? zip.getFileName().toString().replaceFirst("(?i)\\.zip$", "") : root.substring(0, root.length() - 1);
			base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
			if (base.isEmpty() || base.equals(".") || base.equals("..")) {
				base = "World";
			}
			Files.createDirectories(saves);
			String name = base;
			for (int i = 2; Files.exists(saves.resolve(name), LinkOption.NOFOLLOW_LINKS); i++) {
				name = base + " (" + i + ")";
			}
			Path dest = saves.resolve(name).normalize();
			Path tmp = saves.resolve(".kubo-import-" + System.nanoTime());
			long total = 0;
			try {
				Files.createDirectories(tmp);
				for (ZipEntry e : java.util.Collections.list(zf.entries())) {
					String n = e.getName().replace('\\', '/');
					if (!n.startsWith(root) || n.startsWith("__MACOSX/")) {
						continue;
					}
					String rel = n.substring(root.length());
					if (rel.isEmpty() || rel.equals("session.lock")) {
						continue;
					}
					Path out = tmp.resolve(rel).normalize();
					if (!out.startsWith(tmp)) {
						throw new IOException("Bad path in zip: " + n); // zip slip
					}
					if (e.isDirectory()) {
						Files.createDirectories(out);
						continue;
					}
					Files.createDirectories(out.getParent());
					try (InputStream in = zf.getInputStream(e); OutputStream os = Files.newOutputStream(out)) {
						byte[] buf = new byte[64 * 1024];
						for (int r; (r = in.read(buf)) > 0;) {
							total += r;
							if (total > MAX_UNPACKED) {
								throw new IOException(Lang.tr("World is too big", "O mundo é grande demais"));
							}
							os.write(buf, 0, r);
						}
					}
				}
				Files.move(tmp, dest);
			} catch (IOException | RuntimeException ex) {
				deleteTree(tmp);
				throw ex;
			}
			return name;
		}
	}

	// ================================================================ export
	private static void exportWorld(MinecraftClient mc, Screen screen) {
		WorldListWidget list = list(screen);
		var sel = list == null ? null : list.getSelectedAsOptional().orElse(null);
		if (sel == null || !BUSY.compareAndSet(false, true)) {
			return;
		}
		String folder = sel.getLevel().getName();
		String display = sel.getLevel().getDisplayName();
		Path world = mc.getLevelStorage().getSavesDirectory().resolve(folder);
		Thread t = new Thread(() -> {
			try {
				String safe = display.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
				if (safe.isEmpty()) {
					safe = folder;
				}
				Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
				Path suggest = (Files.isDirectory(downloads) ? downloads : Path.of(System.getProperty("user.home"))).resolve(safe + ".zip");
				String path;
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer filters = stack.mallocPointer(1);
					filters.put(stack.UTF8("*.zip"));
					filters.flip();
					path = TinyFileDialogs.tinyfd_saveFileDialog(Lang.tr("Save the world as", "Guardar o mundo como"), suggest.toString(), filters, "ZIP");
				}
				if (path == null || path.isBlank()) {
					return;
				}
				if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
					path += ".zip";
				}
				toast(mc, Lang.tr("Exporting world...", "A exportar o mundo..."), display);
				pack(world, safe, Path.of(path));
				toast(mc, Lang.tr("World exported!", "Mundo exportado!"), Path.of(path).getFileName().toString());
			} catch (Throwable e) {
				System.err.println("[Sleep] world export failed: " + e);
				toast(mc, Lang.tr("Could not export the world", "Não deu para exportar o mundo"), String.valueOf(e.getMessage()));
			} finally {
				BUSY.set(false);
			}
		}, "Sleep-world-export");
		t.setDaemon(true);
		t.start();
	}

	/** Zips the world folder as "<name>/..." (so it imports anywhere), without session.lock. */
	static void pack(Path world, String name, Path zipOut) throws IOException {
		Path tmp = zipOut.resolveSibling(zipOut.getFileName() + ".part");
		List<Path> files = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(world)) {
			walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(files::add);
		}
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
			for (Path p : files) {
				String rel = world.relativize(p).toString().replace('\\', '/');
				if (rel.equals("session.lock")) {
					continue;
				}
				zos.putNextEntry(new ZipEntry(name + "/" + rel));
				Files.copy(p, zos);
				zos.closeEntry();
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
		Files.move(tmp, zipOut, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	private static void deleteTree(Path dir) {
		if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// best effort
				}
			});
		} catch (IOException ignored) {
			// best effort
		}
	}
}
