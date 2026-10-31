package dev.solclient.social;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

/**
 * Animated GIF -> full frames (each frame drawn over the previous ones, like a browser does), with their delays.
 * Pure Java (ImageIO), no Minecraft code: used by the animated capes so a GIF cape no longer needs the embedded browser.
 */
public final class GifFrames {

	public record Anim(List<BufferedImage> frames, int[] delaysMs, int width, int height) {
	}

	private GifFrames() {
	}

	public static boolean isGif(byte[] data) {
		return data != null && data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
	}

	/** maxFrames: longer GIFs are sampled (every n-th frame, delays added up). */
	public static Anim decode(byte[] data, int maxFrames) throws java.io.IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			ImageReader r = ImageIO.getImageReadersByFormatName("gif").next();
			r.setInput(in, false);
			int n = r.getNumImages(true);
			int w = 0, h = 0;
			// logical screen size
			try {
				IIOMetadata sm = r.getStreamMetadata();
				Node root = sm.getAsTree(sm.getNativeMetadataFormatName());
				for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
					if (c.getNodeName().equals("LogicalScreenDescriptor")) {
						w = Integer.parseInt(attr(c, "logicalScreenWidth", "0"));
						h = Integer.parseInt(attr(c, "logicalScreenHeight", "0"));
					}
				}
			} catch (Exception ignored) {
			}
			if (w <= 0 || h <= 0) {
				BufferedImage first = r.read(0);
				w = first.getWidth();
				h = first.getHeight();
			}
			int step = Math.max(1, (n + maxFrames - 1) / maxFrames);
			BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			List<BufferedImage> frames = new ArrayList<>();
			List<Integer> delays = new ArrayList<>();
			int pendingDelay = 0;
			for (int i = 0; i < n; i++) {
				BufferedImage img = r.read(i);
				IIOMetadata md = r.getImageMetadata(i);
				Node root = md.getAsTree(md.getNativeMetadataFormatName());
				int x = 0, y = 0, delay = 100;
				String disposal = "none";
				for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
					if (c.getNodeName().equals("ImageDescriptor")) {
						x = Integer.parseInt(attr(c, "imageLeftPosition", "0"));
						y = Integer.parseInt(attr(c, "imageTopPosition", "0"));
					} else if (c.getNodeName().equals("GraphicControlExtension")) {
						delay = Integer.parseInt(attr(c, "delayTime", "10")) * 10;
						disposal = attr(c, "disposalMethod", "none");
					}
				}
				if (delay < 20) {
					delay = 100; // browsers treat 0 / 10 ms as 100 ms
				}
				BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
				Graphics2D g = canvas.createGraphics();
				g.drawImage(img, x, y, null);
				g.dispose();
				pendingDelay += delay;
				if (i % step == 0) {
					frames.add(copy(canvas));
					delays.add(pendingDelay);
					pendingDelay = 0;
				} else if (!delays.isEmpty()) {
					delays.set(delays.size() - 1, delays.get(delays.size() - 1) + delay);
					pendingDelay = 0;
				}
				if (disposal.equals("restoreToBackgroundColor")) {
					Graphics2D c = canvas.createGraphics();
					c.setComposite(java.awt.AlphaComposite.Clear);
					c.fillRect(x, y, img.getWidth(), img.getHeight());
					c.dispose();
				} else if (before != null) {
					canvas = before;
				}
			}
			r.dispose();
			int[] d = new int[delays.size()];
			for (int i = 0; i < d.length; i++) {
				d[i] = delays.get(i);
			}
			return new Anim(frames, d, w, h);
		}
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	private static String attr(Node n, String name, String def) {
		Node a = n.getAttributes() == null ? null : n.getAttributes().getNamedItem(name);
		return a == null ? def : a.getNodeValue();
	}
}
