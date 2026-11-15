package dev.solclient.social;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a custom nick before it is shown to other players: no swear words (pt / en / es, also written with numbers, symbols,
 * spaces or repeated letters), no phone numbers, no links, no e-mails, no social handles. Own list + normalisation (the
 * Java libraries found online are single-language word lists without this kind of disguise handling).
 */
public final class NickFilter {

	private static final String[] WORDS = {
			// English
			"fuck", "shit", "bitch", "cunt", "dick", "cock", "pussy", "asshole", "bastard", "whore", "slut", "nigg", "fag", "retard",
			"rape", "rapist", "nazi", "hitler", "porn", "sex", "anal", "penis", "vagina", "cum", "boob", "tits", "dildo", "pedo",
			"molest", "kys", "kill yourself", "terror", "isis", "kkk", "twat", "wank", "blowjob", "horny", "milf",
			// Portuguese
			"porra", "caralho", "puta", "foda", "fode", "merda", "cona", "buceta", "viado", "bicha", "cabrao", "cabra", "filhadaputa",
			"fdp", "pila", "pirila", "cacete", "arrombad", "otario", "paneleiro", "punheta", "boquete", "estupr", "pedofil", "vadia",
			"vagabund", "corno", "cuzao", "xereca", "piroca", "mamada", "tarado", "macaco", "preto de", "retardad", "mongol",
			// Spanish
			"mierda", "joder", "puto", "pendejo", "cabron", "coño", "culero", "verga", "chingad", "marica", "maricon", "gilipollas",
			"zorra", "polla", "follar", "violador", "pajero", "hijodeputa", "hdp", "concha", "boludo", "pelotudo", "mamon", "cagar",
			// short ones: only as the whole nick
	};

	private static final String[] WHOLE = {"cu", "cus", "fdp", "pnc", "wtf", "stfu", "gay", "nig", "sexo", "sexy", "tit", "ass", "fck", "fuk"};

	private static final Pattern URL = Pattern.compile("(https?|www|discord|\\.gg|\\.com|\\.net|\\.org|\\.tk|\\.xyz|\\.io|\\.me|\\.ly|\\.pt|\\.br|\\.es)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern HANDLE = Pattern.compile("(@|insta(gram)?|tiktok|snap(chat)?|whats(app)?|telegram|paypal|ip:|\\bgg/)", Pattern.CASE_INSENSITIVE);

	private NickFilter() {
	}

	/** Why this nick is not allowed (a short English reason), or null when it is fine. */
	public static String problem(String nick) {
		if (nick == null) {
			return null;
		}
		String raw = nick.strip();
		if (raw.isEmpty()) {
			return null;
		}
		if (URL.matcher(raw).find()) {
			return "links / web addresses";
		}
		if (HANDLE.matcher(raw).find()) {
			return "social media handles / e-mails";
		}
		// phone numbers: digits (also spelled as symbols), 6 or more in total, or a long run once separators are gone
		int digits = 0;
		for (int i = 0; i < raw.length(); i++) {
			if (Character.isDigit(raw.charAt(i))) {
				digits++;
			}
		}
		if (digits >= 6 || raw.replaceAll("[^0-9]", "").length() >= 6) {
			return "phone numbers";
		}
		String lowered = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String leet = unleet(lowered);
		String tight = leet.replaceAll("[^a-z]", "");
		String squeezed = tight.replaceAll("(.)\\1+", "$1");
		for (String w : WORDS) {
			String k = w.replace(" ", "");
			String ks = k.replaceAll("(.)\\1+", "$1");
			if (tight.contains(k) || squeezed.contains(ks)) {
				return "bad words";
			}
		}
		for (String w : WHOLE) {
			if (tight.equals(w) || squeezed.equals(w)) {
				return "bad words";
			}
		}
		// spelled digits: "seis nove ..." too long a run of number words
		if (tight.matches(".*(zero|one|two|three|four|five|six|seven|eight|nine|zeroone|um|dois|tres|quatro|cinco|seis|sete|oito|nove){5,}.*")) {
			return "phone numbers";
		}
		return null;
	}

	private static String unleet(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '0' -> b.append('o');
				case '1', '!', '|' -> b.append('i');
				case '3' -> b.append('e');
				case '4', '@' -> b.append('a');
				case '5', '$' -> b.append('s');
				case '7', '+' -> b.append('t');
				case '8' -> b.append('b');
				case '9' -> b.append('g');
				default -> b.append(c);
			}
		}
		return b.toString();
	}
}
