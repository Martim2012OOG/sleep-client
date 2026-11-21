package dev.sleepclient.mixin.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ZipResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Much faster resource pack reloads (the 20+ second reload when a server sends its pack).
 * <p>
 * Minecraft finds the files of a zip pack with {@code ZipResourcePack.findResources}, which walks EVERY entry of the zip each time
 * it is asked, and it is asked once per namespace and kind of resource. A server pack made by Nexo / ItemsAdder has hundreds of
 * namespaces (a5rf6l, a1dydz...) and thousands of files, so one reload walked the zips millions of times (and every walk of the same
 * zip waits for the others: the zip is locked while it is read). A thread dump of a real 24 s reload showed 65 % of the worker
 * time right there.
 * <p>
 * Now each zip is read ONCE into a small index (folder "assets/namespace/" -> its entries) and a request only walks the entries of
 * its own namespace. The game's own loop still runs on them, so the result (same files, same order, same checks) does not change.
 */
@Mixin(ZipResourcePack.class)
public abstract class ZipPackIndexMixin {

	@Shadow
	protected abstract String appendOverlayPrefix(String path);

	@Unique
	private static final ThreadLocal<String> sleep$root = new ThreadLocal<>();
	@Unique
	private volatile Map<String, List<ZipEntry>> sleep$index;
	@Unique
	private volatile ZipFile sleep$indexOf;

	/** Remembers which folder this call asks for ("assets/namespace/", with the overlay prefix if the pack has one). */
	@Inject(method = "findResources", at = @At("HEAD"), require = 0)
	private void sleep$remember(ResourceType type, String namespace, String prefix, @org.spongepowered.asm.mixin.injection.Coerce Object consumer,
			CallbackInfo ci) {
		try {
			sleep$root.set(appendOverlayPrefix(type.getDirectory() + "/" + namespace + "/"));
		} catch (Throwable t) {
			sleep$root.remove();
		}
	}

	/** The entries to look at: only those of the folder asked for (or everything, exactly as before, if anything is not as expected). */
	@Redirect(method = "findResources", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;"))
	private Enumeration<? extends ZipEntry> sleep$entries(ZipFile zip) {
		String root = sleep$root.get();
		sleep$root.remove();
		if (root == null) {
			return zip.entries();
		}
		try {
			Map<String, List<ZipEntry>> idx = sleep$index;
			if (idx == null || sleep$indexOf != zip) {
				synchronized (this) {
					idx = sleep$index;
					if (idx == null || sleep$indexOf != zip) {
						idx = sleep$build(zip);
						sleep$index = idx;
						sleep$indexOf = zip;
					}
				}
			}
			List<ZipEntry> list = idx.get(root);
			return list == null ? Collections.emptyEnumeration() : Collections.enumeration(list);
		} catch (Throwable t) {
			return zip.entries(); // never worse than the game's own way
		}
	}

	@Unique
	private Map<String, List<ZipEntry>> sleep$build(ZipFile zip) {
		// how many "/" the folder key has: "assets/namespace/" = 2, one more when the pack uses an overlay folder
		int depth = 0;
		String sample = appendOverlayPrefix("assets/x/");
		for (int i = 0; i < sample.length(); i++) {
			if (sample.charAt(i) == '/') {
				depth++;
			}
		}
		Map<String, List<ZipEntry>> idx = new HashMap<>();
		Enumeration<? extends ZipEntry> en = zip.entries();
		while (en.hasMoreElements()) {
			ZipEntry e = en.nextElement();
			String name = e.getName();
			int cut = -1;
			for (int k = 0; k < depth; k++) {
				cut = name.indexOf('/', cut + 1);
				if (cut < 0) {
					break;
				}
			}
			if (cut < 0) {
				continue; // too short to be inside any "assets/namespace/" folder
			}
			idx.computeIfAbsent(name.substring(0, cut + 1), k -> new ArrayList<>()).add(e);
		}
		return idx;
	}
}
