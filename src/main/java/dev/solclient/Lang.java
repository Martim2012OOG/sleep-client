package dev.solclient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client language: Settings > Language. English is the source; Portuguese (Portugal) comes from
 * assets/sol_client/lang/pt_pt.tsv (one "English<TAB>Português" per line). Every menu text goes through
 * {@link Gfx#ui(String)}, which calls {@link #t(String)}, so the whole menu changes at once.
 */
public final class Lang {

	private static Map<String, String> pt;
	private static Map<String, String> ptUpper;
	private static final Map<String, String> CACHE = new HashMap<>();
	private static final Pattern LAYER = Pattern.compile("^Layer (\\d) (.+)$");
	private static final Pattern TRAIL_NUM = Pattern.compile("^(.*?)(\\s*[:\\d%.,x×\\-+/ ]+)$");

	private Lang() {
	}

	/** Settings > Language. English and Portuguese (Portugal) are written by hand, the others translate themselves. */
	public static final String[] NAMES = {"English", "Portugu\u00eas", "Portugu\u00eas (Brasil)", "Espa\u00f1ol", "Fran\u00e7ais", "Deutsch",
			"Italiano", "Polski", "Nederlands", "T\u00fcrk\u00e7e"};
	public static final String[] CODES = {"en", "pt", "pt-BR", "es", "fr", "de", "it", "pl", "nl", "tr"};
	/** Minecraft's own language for each one (Settings > "Minecraft in this language too"). */
	public static final String[] MC_CODES = {"en_us", "pt_pt", "pt_br", "es_es", "fr_fr", "de_de", "it_it", "pl_pl", "nl_nl", "tr_tr"};
	/** textures/gui/flag/<name>.png */
	public static final String[] FLAGS = {"gb", "pt", "br", "es", "fr", "de", "it", "pl", "nl", "tr"};

	/** Bumped when automatic translations arrive (text widths are measured again). */
	public static volatile int version;

	public static int idx() {
		var s = SolClient.settings;
		int i = s == null || s.language == null ? 0 : s.language.idx();
		return i < 0 || i >= CODES.length ? 0 : i;
	}

	public static String code() {
		return CODES[idx()];
	}

	/** True when the client is in Portuguese (Portugal). */
	public static boolean pt() {
		return idx() == 1;
	}

	/** New automatic translations are in: drop the cached results. */
	static void changed() {
		version++;
	}

	private static void load() {
		pt = new HashMap<>();
		ptUpper = new HashMap<>();
		try (InputStream in = Lang.class.getResourceAsStream("/assets/sol_client/lang/pt_pt.tsv")) {
			if (in == null) {
				return;
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				String en = line.substring(0, tab), p = line.substring(tab + 1);
				pt.put(en, p);
				ptUpper.putIfAbsent(en.toUpperCase(Locale.ROOT), p.toUpperCase(Locale.ROOT));
			}
		} catch (Exception e) {
			System.err.println("[Sleep] language file: " + e);
		}
	}

	/** Extra "English<TAB>Português" lines from a Sleep extension (its own tsv file). */
	public static synchronized void addFrom(InputStream in) {
		if (in == null) {
			return;
		}
		if (pt == null) {
			load();
		}
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				String en = line.substring(0, tab), p = line.substring(tab + 1);
				pt.putIfAbsent(en, p);
				ptUpper.putIfAbsent(en.toUpperCase(Locale.ROOT), p.toUpperCase(Locale.ROOT));
			}
			CACHE.clear();
		} catch (Exception e) {
			System.err.println("[Sleep] extension language file: " + e);
		}
	}

	/** en, or pt when the client is in Portuguese (for texts built from pieces); other languages translate en. */
	public static String tr(String en, String ptText) {
		int i = idx();
		return i == 1 ? ptText : i == 0 ? en : t(en);
	}

	/** The text in the client language (unchanged when there is no translation). */
	public static String t(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		int li = idx();
		if (li == 0) {
			return s;
		}
		if (li != 1) {
			return AutoTranslate.get(CODES[li], s);
		}
		if (pt == null) {
			load();
		}
		String c = CACHE.get(s);
		if (c != null) {
			return c;
		}
		String out = find(s);
		if (CACHE.size() > 8000) {
			CACHE.clear();
		}
		CACHE.put(s, out);
		return out;
	}

	private static String find(String s) {
		String v = pt.get(s);
		if (v != null) {
			return v;
		}
		v = ptUpper.get(s);
		if (v != null) {
			return v;
		}
		// keep leading / trailing spaces
		String trimmed = s.strip();
		if (!trimmed.equals(s) && !trimmed.isEmpty()) {
			String tv = pt.get(trimmed);
			if (tv == null) {
				tv = ptUpper.get(trimmed);
			}
			if (tv != null) {
				int a = s.indexOf(trimmed);
				return s.substring(0, a) + tv + s.substring(a + trimmed.length());
			}
		}
		// "Layer 2 fall speed" -> "Camada 2 velocidade de queda"
		Matcher m = LAYER.matcher(s);
		if (m.matches()) {
			String rest = pt.getOrDefault(m.group(2), m.group(2));
			return "Camada " + m.group(1) + " " + rest;
		}
		// "Text: 12" / "Something 50%": translate the words, keep the number
		Matcher n = TRAIL_NUM.matcher(s);
		if (n.matches() && !n.group(1).isEmpty()) {
			String head = pt.get(n.group(1).strip());
			if (head != null) {
				return head + n.group(2);
			}
		}
		// "Label: value" -> translate the label
		int colon = s.indexOf(": ");
		if (colon > 0) {
			String head = pt.get(s.substring(0, colon));
			if (head == null) {
				head = pt.get(s.substring(0, colon + 1));
				if (head != null) {
					return head + " " + s.substring(colon + 2);
				}
			} else {
				return head + s.substring(colon);
			}
		}
		return s;
	}
}
