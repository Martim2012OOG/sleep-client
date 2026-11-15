package dev.solclient.fightcam;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Vosk on macOS without org.vosk.LibVosk. LibVosk binds every function of the library at once, and the Mac library that
 * comes with Vosk 0.3.45 is older than its Java part: it has no vosk_recognizer_set_grm, so the whole engine failed with
 * "symbol not found" (and could not be retried without restarting Minecraft). Here only the functions we use are looked
 * up, one by one, when they are called.
 */
final class VoskMac {

	interface Api extends Library {
		void vosk_set_log_level(int level);

		Pointer vosk_model_new(String path);

		void vosk_model_free(Pointer model);

		Pointer vosk_recognizer_new(Pointer model, float sampleRate);

		int vosk_recognizer_accept_waveform(Pointer rec, byte[] data, int length);

		String vosk_recognizer_result(Pointer rec);

		String vosk_recognizer_partial_result(Pointer rec);

		void vosk_recognizer_reset(Pointer rec);

		void vosk_recognizer_free(Pointer rec);
	}

	private static Api api;

	private VoskMac() {
	}

	/** Loads the library at this path (once). */
	static synchronized Api api(String libPath) {
		if (api == null) {
			api = Native.load(libPath, Api.class);
		}
		return api;
	}
}
