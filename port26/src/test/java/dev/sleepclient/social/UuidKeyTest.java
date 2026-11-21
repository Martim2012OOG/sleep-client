package dev.sleepclient.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The key format of /players/lookup: lower case, no hyphens. */
class UuidKeyTest {

	@Test
	void normalizesEveryWayAUuidCanBeWritten() {
		String want = "069a79f444e94726a5befca90e38aaf5";
		assertEquals(want, UuidKey.normalize("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
		assertEquals(want, UuidKey.normalize("069A79F4-44E9-4726-A5BE-FCA90E38AAF5"));
		assertEquals(want, UuidKey.normalize("  069a79f444e94726a5befca90e38aaf5 "));
		assertEquals(want, UuidKey.normalize(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
	}

	@Test
	void rejectsWhatIsNotAUuid() {
		assertNull(UuidKey.normalize((String) null));
		assertNull(UuidKey.normalize(""));
		assertNull(UuidKey.normalize("Notch"));
		assertNull(UuidKey.normalize("069a79f444e94726a5befca90e38aaf"));
		assertNull(UuidKey.normalize("069a79f444e94726a5befca90e38aafg"));
	}
}
