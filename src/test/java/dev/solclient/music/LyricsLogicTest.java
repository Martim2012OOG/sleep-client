package dev.solclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LyricsLogicTest {

	private static final String LRC = "[ar:Someone]\n[00:10.50] Hello darkness my old friend\n[00:14.00]\n[00:20.00] I've come to talk\n";

	@Test
	void parsesLinesAndSkipsTags() {
		List<Lyrics.Line> l = Lyrics.parse(LRC);
		assertEquals(3, l.size());
		assertEquals(10_500, l.get(0).start());
		assertTrue(l.get(1).blank());
		assertEquals("I've come to talk", l.get(2).text());
	}

	@Test
	void chunksAreInOrderAndInsideTheLine() {
		List<Lyrics.Chunk> c = Lyrics.chunks("Hello darkness my old friend", 10_000, 14_000);
		assertTrue(c.size() >= 3);
		long prev = 10_000;
		for (Lyrics.Chunk ch : c) {
			assertTrue(ch.start() >= prev);
			assertTrue(ch.end() > ch.start());
			prev = ch.start();
		}
		assertTrue(c.get(c.size() - 1).end() <= 14_000 + 100);
	}

	@Test
	void indexAtFindsTheLineBeingSung() {
		List<Lyrics.Line> l = Lyrics.parse(LRC);
		assertEquals(-1, Lyrics.indexAt(l, 5_000));
		assertEquals(0, Lyrics.indexAt(l, 12_000));
		assertEquals(2, Lyrics.indexAt(l, 25_000));
	}

	@Test
	void cleanRemovesVersionNoise() {
		assertEquals("Song", Lyrics.clean("Song (feat. Someone)"));
		assertEquals("Song", Lyrics.clean("Song - Remastered 2011"));
	}

	@Test
	void flatSheetProjectsAsIs() {
		float[] p = new float[6];
		Persp.project(40F, -12F, 0F, 0F, 0F, 200F, p);
		assertEquals(40F, p[0], 0.01F);
		assertEquals(-12F, p[1], 0.01F);
		assertEquals(1F, Persp.scale(p), 0.001F);
	}

	@Test
	void tiltedSheetHasPerspective() {
		float[] far = new float[6], near = new float[6];
		float yaw = (float) Math.toRadians(20);
		Persp.project(100F, 0F, yaw, 0F, 0F, 200F, far); // right side goes away
		Persp.project(-100F, 0F, yaw, 0F, 0F, 200F, near);
		assertTrue(Persp.scale(far) < 1F);
		assertTrue(Persp.scale(near) > 1F);
		float pitch = (float) Math.toRadians(25);
		float[] top = new float[6], bottom = new float[6];
		Persp.project(0F, -40F, 0F, pitch, 0F, 200F, top); // leaning back: the top goes away
		Persp.project(0F, 40F, 0F, pitch, 0F, 200F, bottom);
		assertTrue(Persp.scale(top) < Persp.scale(bottom));
	}

	@Test
	void projectionStaysFiniteForExtremeAngles() {
		float[] p = new float[6];
		Persp.project(900F, 900F, 1.5F, 1.5F, 3F, 140F, p);
		for (float f : p) {
			assertTrue(Float.isFinite(f));
		}
	}

	@Test
	void sameTitleOtherArtistIsNotTheSong() {
		// "Hello" by Adele must not get the lyrics of "Hello" by Lionel Richie (or any other famous "Hello")
		assertTrue(Lyrics.score("Hello", "Adele", 295_000, "Hello", "Lionel Richie", 295) < 0);
		assertTrue(Lyrics.score("Hello", "Adele", 295_000, "Hello", "Adele", 295) > 0);
	}

	@Test
	void extraWordsInTheTitleAreAnotherSong() {
		assertTrue(Lyrics.score("Hello", "Someone", 200_000, "Hello Goodbye", "Someone", 200) < 0);
		assertTrue(Lyrics.score("Believer", "Imagine Dragons", 204_000, "Believer (Official Audio)", "Imagine Dragons", 204) > 0);
	}

	@Test
	void accentsFeaturesAndSeveralArtistsStillMatch() {
		assertTrue(Lyrics.score("Halo", "Beyonc\u00e9", 261_000, "Halo", "Beyonce", 261) > 0);
		assertTrue(Lyrics.score("Rock & Roll", "Xutos & Pontap\u00e9s", 0, "Rock and Roll", "Xutos e Pontap\u00e9s", 0) > 0);
		assertTrue(Lyrics.score("Song", "Artist, Other", 200_000, "Song", "Artist", 200) > 0);
		assertTrue(Lyrics.score("Song", "Tyler, The Creator", 200_000, "Song", "Tyler, The Creator", 200) > 0);
	}

	@Test
	void anotherVersionOfTheSongIsRejectedByLength() {
		assertTrue(Lyrics.score("Song", "A", 200_000, "Song", "A", 330) < 0);
		assertTrue(Lyrics.score("Song", "A", 200_000, "Song", "A", 201) > Lyrics.score("Song", "A", 200_000, "Song", "A", 207));
	}
}
