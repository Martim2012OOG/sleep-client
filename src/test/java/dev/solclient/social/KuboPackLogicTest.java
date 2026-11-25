package dev.solclient.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solclient.social.KuboPackLogic.Link;
import dev.solclient.social.KuboPackLogic.Pack;
import dev.solclient.social.KuboPackLogic.State;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure logic of the Caos resource packs (no Minecraft needed). What needs the game is listed in PACKS-TESTING.md. */
class KuboPackLogicTest {

	// SHA-1 of the ASCII text "abc"
	private static final String ABC_SHA1 = "a9993e364706816aba3e25717850c26c9cd0d89d";

	private static Pack pack(String id, String version, String sha1) {
		return new Pack(id, "Pack " + id, "desc", version, "1.21.11", List.of("pvp"), "owner", "credits", "", "", "", "", "", "",
				"https://example.com/p.zip", sha1, 3, 0);
	}

	@Test
	void fileNameFollowsTheContract() {
		assertEquals("kubo-faithful-1.2.0.zip", KuboPackLogic.fileName(pack("faithful", "1.2.0", ABC_SHA1)));
		assertEquals("file/kubo-faithful-1.2.0.zip", KuboPackLogic.managerId("kubo-faithful-1.2.0.zip"));
	}

	@Test
	void fileNameCanNeverEscapeTheFolder() {
		String n = KuboPackLogic.fileName(pack("../../evil", "1/0\\x", ABC_SHA1));
		assertFalse(n.contains("/"));
		assertFalse(n.contains("\\"));
		assertTrue(n.startsWith("kubo-") && n.endsWith(".zip"));
		assertEquals("kubo-x-x.zip", KuboPackLogic.fileName(pack("", "", ABC_SHA1)));
	}

	@Test
	void onlyHttpsAndRealSha1AreUsable() {
		assertTrue(KuboPackLogic.usable(pack("a", "1", ABC_SHA1)));
		assertFalse(KuboPackLogic.usable(pack("a", "1", "xyz")));
		assertFalse(KuboPackLogic.usable(pack("a", "1", ABC_SHA1.substring(1))));
		Pack http = new Pack("a", "A", "", "1", "", List.of(), "", "", "", "", "", "", "", "", "http://example.com/p.zip", ABC_SHA1, 0, 0);
		assertFalse(KuboPackLogic.usable(http));
		assertEquals(ABC_SHA1, KuboPackLogic.normSha1("  " + ABC_SHA1.toUpperCase() + " "));
		assertNull(KuboPackLogic.normSha1(null));
	}

	@Test
	void sha1MatchesAKnownValue(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("a.zip");
		Files.write(f, "abc".getBytes(StandardCharsets.US_ASCII));
		assertEquals(ABC_SHA1, KuboPackLogic.sha1Hex(f));
	}

	@Test
	void installStates(@TempDir Path dir) throws Exception {
		Pack v1 = pack("p", "1.0", ABC_SHA1);
		assertEquals(State.NOT_INSTALLED, KuboPackLogic.state(v1, dir, null));

		Files.write(dir.resolve(KuboPackLogic.fileName(v1)), "abc".getBytes(StandardCharsets.US_ASCII));
		assertEquals(State.INSTALLED, KuboPackLogic.state(v1, dir, KuboPackLogic.fileName(v1)));

		// the site now has version 2: the v1 file we recorded is still there -> update
		Pack v2 = pack("p", "2.0", "0000000000000000000000000000000000000000");
		assertEquals(State.UPDATE, KuboPackLogic.state(v2, dir, KuboPackLogic.fileName(v1)));
		// ... but with no record of an older file there is nothing to update
		assertEquals(State.NOT_INSTALLED, KuboPackLogic.state(v2, dir, null));
		// the recorded file was deleted by hand -> install again, not update
		Files.delete(dir.resolve(KuboPackLogic.fileName(v1)));
		assertEquals(State.NOT_INSTALLED, KuboPackLogic.state(v2, dir, KuboPackLogic.fileName(v1)));
	}

	@Test
	void sameVersionWithAnotherHashIsAnUpdate(@TempDir Path dir) throws Exception {
		Pack p = pack("p", "1.0", "0000000000000000000000000000000000000000");
		Files.write(dir.resolve(KuboPackLogic.fileName(p)), "abc".getBytes(StandardCharsets.US_ASCII));
		assertEquals(State.UPDATE, KuboPackLogic.state(p, dir, null));
	}

	@Test
	void aPackWithSimilarIdIsNotMistakenForAnUpdate(@TempDir Path dir) throws Exception {
		Files.write(dir.resolve("kubo-faithful-32x-1.0.zip"), "abc".getBytes(StandardCharsets.US_ASCII));
		assertEquals(State.NOT_INSTALLED, KuboPackLogic.state(pack("faithful", "2.0", ABC_SHA1), dir, null));
	}

	@Test
	void recordedFileNameCannotPointOutsideTheFolder(@TempDir Path dir) {
		assertEquals(State.NOT_INSTALLED, KuboPackLogic.state(pack("p", "1", ABC_SHA1), dir, "../secret.zip"));
	}

	@Test
	void urlsOpenAndBareAddressesGetHttps() {
		Link a = KuboPackLogic.link("discord", "https://discord.gg/abc");
		assertTrue(a.opens());
		assertEquals("https://discord.gg/abc", a.url());
		assertEquals("https://discord.gg/abc", KuboPackLogic.link("discord", "discord.gg/abc").url());
		assertEquals("https://youtube.com/@kubo", KuboPackLogic.link("youtube", "youtube.com/@kubo").url());
		assertEquals("https://kubo.example/pt", KuboPackLogic.link("website", "kubo.example/pt").url());
	}

	@Test
	void discordUserNamesAreCopiedNotOpened() {
		Link u = KuboPackLogic.link("discord", "cool.dev");
		assertFalse(u.opens());
		assertEquals("cool.dev", u.copy());
		Link at = KuboPackLogic.link("discord", "@kubo");
		assertFalse(at.opens());
		assertEquals("@kubo", at.copy());
		assertFalse(KuboPackLogic.link("twitter", "@kubo").opens());
		assertFalse(KuboPackLogic.link("discord", "Caos Fan#1234").opens());
	}

	@Test
	void dangerousSchemesNeverOpen() {
		assertFalse(KuboPackLogic.link("website", "javascript:alert(1)").opens());
		assertFalse(KuboPackLogic.link("website", "file:///etc/passwd").opens());
		assertFalse(KuboPackLogic.link("website", "ftp://x.example/a").opens());
		assertNull(KuboPackLogic.link("website", "   "));
		assertNull(KuboPackLogic.link("website", null));
	}
}
