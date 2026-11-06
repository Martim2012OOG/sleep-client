package dev.solclient.verity;

/**
 * The language set in Verity's settings (Language): he answers in it, speaks it (Piper voice) and listens in it (offline model).
 * "Other" lets the player write any language: the brain still answers in it, the computer's own voice speaks it, and there are
 * no ears for it.
 */
final class VerityLang {

	/** label: shown in the settings; prompt: how the brain is told; piperDir/piperFile: voice on Hugging Face; ears: model index of VoiceTrigger. */
	record L(String label, String prompt, String piperDir, String piperFile, int ears) {
	}

	static final L[] ALL = {
			new L("Português (Portugal)", "European Portuguese (Portugal), not Brazilian", "pt/pt_PT/tugão/medium", "pt_PT-tugao-medium", 0),
			new L("Português (Brasil)", "Brazilian Portuguese", "pt/pt_BR/faber/medium", "pt_BR-faber-medium", 0),
			new L("English", "English", "en/en_US/ryan/medium", "en_US-ryan-medium", 1),
			new L("Español", "Spanish", "es/es_ES/davefx/medium", "es_ES-davefx-medium", 2),
			new L("Français", "French", "fr/fr_FR/siwis/medium", "fr_FR-siwis-medium", 3),
			new L("Deutsch", "German", "de/de_DE/thorsten/medium", "de_DE-thorsten-medium", 4),
			new L("Italiano", "Italian", "it/it_IT/paola/medium", "it_IT-paola-medium", 5),
	};

	private VerityLang() {
	}

	static String[] labels() {
		String[] out = new String[ALL.length + 1];
		for (int i = 0; i < ALL.length; i++) {
			out[i] = ALL[i].label();
		}
		out[ALL.length] = "Other";
		return out;
	}

	/** The chosen language, or null for "Other". */
	static L current() {
		VerityMod m = VerityMod.instance;
		int i = m == null ? 0 : m.language.idx();
		return i >= 0 && i < ALL.length ? ALL[i] : null;
	}

	/** The language the brain must answer in. */
	static String promptLanguage() {
		L l = current();
		if (l != null) {
			return l.prompt();
		}
		VerityMod m = VerityMod.instance;
		String o = m == null ? "" : m.otherLanguage.get().trim();
		return o.isEmpty() ? "English" : o;
	}

	/** Model index for the ears, or -1 when there is none for this language. */
	static int earsIndex() {
		L l = current();
		return l == null ? -1 : l.ears();
	}
}
