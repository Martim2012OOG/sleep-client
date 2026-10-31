package dev.solclient.social;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solclient.social.CapeMediaLogic.Desc;
import dev.solclient.social.CapeMediaLogic.Kind;
import dev.solclient.social.CapeMediaLogic.Side;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Pure logic of the shop capes (no Minecraft needed). What needs the game is listed in CAPES-TESTING.md. */
class CapeMediaLogicTest {

	private static final String BASE = "https://pal-connect-plaza.lovable.app/api/public/gc/capes/media/uploads/";
	private final Side mp4 = CapeMediaLogic.side(BASE + "a.mp4", "video");
	private final Side png = CapeMediaLogic.side(BASE + "b.png", "image");
	private final Side jpg = CapeMediaLogic.side(BASE + "c.jpg?x=1", "image");
	private final Side gif = CapeMediaLogic.side(BASE + "d.gif", "image");

	@Test
	void onlyHttpsIsAccepted() {
		assertNull(CapeMediaLogic.side("http://x/a.png", "image"));
		assertNull(CapeMediaLogic.side("javascript:alert(1)", "image"));
		assertNull(CapeMediaLogic.side("https://x/a b.png", "image"));
		assertEquals(Kind.JPG, CapeMediaLogic.kind(jpg));
	}

	@Test
	void plainPngAtlasKeepsTheNormalRenderer() {
		Desc d = CapeMediaLogic.make("atlas", png, null);
		assertTrue(CapeMediaLogic.legacyAtlas(d));
		assertFalse(CapeMediaLogic.custom(d));
		assertSame(d.back(), d.front());
	}

	@Test
	void panelsAndMovingMediaAreCustom() {
		Desc v = CapeMediaLogic.make("panels", mp4, png);
		assertTrue(CapeMediaLogic.custom(v) && CapeMediaLogic.needsBrowser(v));
		Desc p = CapeMediaLogic.make("panels", jpg, png);
		assertTrue(CapeMediaLogic.custom(p));
		assertFalse(CapeMediaLogic.needsBrowser(p));
		// GIFs are decoded in Java since v102: no browser needed
		assertFalse(CapeMediaLogic.needsBrowser(CapeMediaLogic.make("atlas", gif, null)));
		assertTrue(CapeMediaLogic.gif(CapeMediaLogic.make("atlas", gif, null)));
		assertTrue(CapeMediaLogic.custom(CapeMediaLogic.make("atlas", jpg, null)));
		assertNull(CapeMediaLogic.make("atlas", null, null));
	}

	@Test
	void keysShareDecodersOnlyForTheSameCape() {
		assertNotEquals(CapeMediaLogic.make("panels", mp4, png).key(), CapeMediaLogic.make("panels", mp4, mp4).key());
		assertEquals(CapeMediaLogic.make("panels", mp4, null).key(), CapeMediaLogic.make("panels", mp4, mp4).key());
	}

	@Test
	void pageLaysOutTheAtlas() {
		String h = CapeMediaLogic.html(CapeMediaLogic.make("panels", mp4, null), 8);
		assertTrue(h.contains("left:8px;top:8px;width:80px;height:128px") && h.contains("left:96px;top:8px"));
		assertEquals(h.indexOf("<video"), h.lastIndexOf("<video")); // one decoder, the second side copies it
		String two = CapeMediaLogic.html(CapeMediaLogic.make("panels", mp4, CapeMediaLogic.side(BASE + "z.mp4", "video")), 8);
		assertTrue(two.contains("currentTime")); // same playback clock
		assertTrue(CapeMediaLogic.html(CapeMediaLogic.make("atlas", mp4, null), 8).contains("width:512px;height:256px"));
		String evil = CapeMediaLogic.html(CapeMediaLogic.make("atlas", CapeMediaLogic.side("https://x/a\"><script>1</script>.mp4", "video"), null), 8);
		assertFalse(evil.contains("\"><script>1"));
	}

	@Test
	void composedAtlasCropsWithoutStretching() throws Exception {
		BufferedImage red = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
		BufferedImage blue = new BufferedImage(100, 400, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 100; y++) {
			for (int x = 0; x < 400; x++) {
				red.setRGB(x, y, 0xFFFF0000);
			}
		}
		for (int y = 0; y < 400; y++) {
			for (int x = 0; x < 100; x++) {
				blue.setRGB(x, y, 0xFF0000FF);
			}
		}
		byte[] bytes = CapeMediaLogic.compose(CapeMediaLogic.make("panels", mp4, png), red, blue, 12);
		BufferedImage out = ImageIO.read(new ByteArrayInputStream(bytes));
		assertEquals(768, out.getWidth());
		assertEquals(384, out.getHeight());
		assertEquals(0xFF0000, out.getRGB(72, 108) & 0xFFFFFF);
		assertEquals(0x0000FF, out.getRGB(204, 108) & 0xFFFFFF);
		assertEquals(0x15171C, out.getRGB(9, 108) & 0xFFFFFF);
	}

	@Test
	void shopPictureOfUnknownShapeIsAutoAndNeverLegacy() {
		Desc d = CapeMediaLogic.auto(BASE + "cape.png");
		assertTrue(d.auto());
		assertFalse(CapeMediaLogic.legacyAtlas(d));
		assertTrue(CapeMediaLogic.custom(d));
		assertNull(CapeMediaLogic.auto("http://x/cape.png"));
	}

	@Test
	void realAtlasIsToldApartFromAFlatPicture() {
		BufferedImage atlas = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		assertTrue(CapeMediaLogic.looksLikeAtlas(atlas));
		assertFalse(CapeMediaLogic.looksLikeAtlas(new BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB)));
		BufferedImage opaque = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 1024; x++) {
			for (int y = 0; y < 512; y++) {
				opaque.setRGB(x, y, 0x336699);
			}
		}
		// a fully painted 2:1 cape is still an atlas (no transparent corner needed)
		assertTrue(CapeMediaLogic.looksLikeAtlas(opaque));
		assertFalse(CapeMediaLogic.looksLikeAtlas(new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB)));
	}

	@Test
	void faceBoxesScaleWithTheAtlasWidth() {
		assertArrayEquals(new int[]{1, 1, 10, 16}, CapeMediaLogic.face("back", 64));
		assertArrayEquals(new int[]{12, 1, 10, 16}, CapeMediaLogic.face("front", 64));
		assertArrayEquals(new int[]{22, 0, 24, 22}, CapeMediaLogic.face("elytra", 64));
		// 2048x1024: scale 32
		assertArrayEquals(new int[]{32, 32, 320, 512}, CapeMediaLogic.face("back", 2048));
		assertArrayEquals(new int[]{384, 32, 320, 512}, CapeMediaLogic.face("front", 2048));
		assertArrayEquals(new int[]{704, 0, 768, 704}, CapeMediaLogic.face("elytra", 2048));
		assertArrayEquals(new int[]{0, 32, 32, 512}, CapeMediaLogic.face("left", 2048));
	}

	@Test
	void hdAtlasIsRecognisedAndVideoIsToldByExtension() {
		assertTrue(CapeMediaLogic.looksLikeAtlas(new BufferedImage(2048, 1024, BufferedImage.TYPE_INT_ARGB)));
		Side mp4 = CapeMediaLogic.side("https://x.test/c.mp4?token=1", "image");
		Side webm = CapeMediaLogic.side("https://x.test/c.webm", null);
		Side png = CapeMediaLogic.side("https://x.test/c.png", "image");
		assertEquals(Kind.VIDEO, CapeMediaLogic.kind(mp4));
		assertEquals(Kind.VIDEO, CapeMediaLogic.kind(webm));
		assertEquals(Kind.PNG, CapeMediaLogic.kind(png));
		// video is never the legacy PNG renderer's business
		assertFalse(CapeMediaLogic.legacyAtlas(CapeMediaLogic.make("atlas", mp4, null)));
		assertTrue(CapeMediaLogic.atlasVideo(CapeMediaLogic.make("atlas", webm, null)));
		assertTrue(CapeMediaLogic.legacyAtlas(CapeMediaLogic.make("atlas", png, null)));
	}
}
