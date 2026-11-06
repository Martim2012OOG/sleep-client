package dev.solclient.verity;

import dev.solclient.Lang;
import dev.solclient.fightcam.VoiceTrigger;

/**
 * Verity's ears, 100% on this computer: the same offline speech recognition (Vosk) the Clip Events voice trigger uses, with the
 * Portuguese or the English model. The Install button downloads the small model once (about 40 MB).
 */
final class Ears {

	private static volatile String status = "";
	private static volatile boolean busy;

	private Ears() {
	}

	/** Model of the language chosen in Verity's settings, -1 when there is none for it. */
	private static int lang() {
		return VerityLang.earsIndex();
	}

	static boolean ready() {
		return lang() >= 0 && VoiceTrigger.modelInstalled(lang());
	}

	static String status() {
		if (busy) {
			String s = VoiceTrigger.status;
			return s == null || s.isEmpty() ? Lang.tr("Installing...", "A instalar...") : s;
		}
		if (!status.isEmpty()) {
			return status;
		}
		if (lang() < 0) {
			return Lang.tr("No ears for this language yet: use the chat", "Ainda sem ouvidos para esta língua: usa o chat");
		}
		return ready() ? Lang.tr("Ready", "Pronto") : Lang.tr("Not installed: press Install (about 40 MB)", "Não instalado: carrega em Instalar (cerca de 40 MB)");
	}

	static void install() {
		if (busy) {
			return;
		}
		int l = lang();
		if (l < 0) {
			status = Lang.tr("No ears for this language yet: use the chat", "Ainda sem ouvidos para esta língua: usa o chat");
			return;
		}
		busy = true;
		status = "";
		Thread t = new Thread(() -> {
			try {
				if (!VoiceTrigger.modelInstalled(l)) {
					VoiceTrigger.installModelForVerity(l);
				}
				status = "";
			} catch (Exception e) {
				status = Lang.tr("Failed: ", "Falhou: ") + e.getMessage();
			} finally {
				busy = false;
			}
		}, "kubo-verity-ears-install");
		t.setDaemon(true);
		t.start();
	}

	/** What was said (16-bit mono samples at any rate), or "". Blocks: call it off the game thread. */
	static String transcribe(short[] pcm, int n, int rate) throws Exception {
		if (lang() < 0) {
			return "";
		}
		return VoiceTrigger.transcribe(pcm, n, rate, lang());
	}
}
