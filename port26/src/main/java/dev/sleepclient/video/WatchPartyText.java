package dev.sleepclient.video;

import dev.sleepclient.Lang;
import dev.sleepclient.mods.WatchPartyMod;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Watch Party texts in the language chosen in the mod's settings (Auto follows the client language). */
public final class WatchPartyText {

	private static final Map<String, String> PT = Map.ofEntries(
			Map.entry("Turn on the Watch Party mod first (Right Shift > mods). The first time it downloads the video engine (~100 MB).",
					"Liga primeiro o mod Watch Party (Right Shift > mods). À primeira vez descarrega o motor de vídeo (~100 MB)."),
			Map.entry("That is not a YouTube link.", "Isso não é um link do YouTube."),
			Map.entry("Playing on your screen. Friends with Sleep + Watch Party see it too.", "A tocar no teu ecrã. Os amigos com Sleep + Watch Party também veem."),
			Map.entry("Cancelled.", "Cancelado."),
			Map.entry("Only the owner of this screen can pause it.", "Só o dono deste ecrã o pode pausar."),
			Map.entry("Aim at a block.", "Aponta para um bloco."),
			Map.entry("Screens go on walls: aim at the side of a block.", "Os ecrãs vão em paredes: aponta para o lado de um bloco."),
			Map.entry("All corners must be on the same wall (same side of the blocks).", "Todos os cantos têm de estar na mesma parede (do mesmo lado dos blocos)."),
			Map.entry("Too big: the most is 32 x 32 blocks.", "Grande demais: o máximo é 32 x 32 blocos."),
			Map.entry("Too small.", "Pequeno demais."),
			Map.entry("You have no screen.", "Não tens nenhum ecrã."),
			Map.entry("Use seconds or mm:ss, e.g. /kubo video seek 1:30", "Usa segundos ou mm:ss, por exemplo /kubo video seek 1:30"),
			Map.entry("Screen removed.", "Ecrã removido."),
			Map.entry("Watch Party", "Watch Party"),
			Map.entry("YouTube link", "Link do YouTube"),
			Map.entry("Paste a YouTube link...", "Cola um link do YouTube..."),
			Map.entry("Play", "Tocar"),
			Map.entry("Pause", "Pausar"),
			Map.entry("Resume", "Continuar"),
			Map.entry("Go to time", "Ir para o tempo"),
			Map.entry("Go to time (e.g. 1:30 or 90)", "Ir para o tempo (ex.: 1:30 ou 90)"),
			Map.entry("Go", "Ir"),
			Map.entry("Restart", "Recomeçar"),
			Map.entry("Redraw Corners", "Redesenhar cantos"),
			Map.entry("Remove Screen", "Remover ecrã"),
			Map.entry("Done", "Feito"),
			Map.entry("Loading video length...", "A carregar a duração do vídeo..."),
			Map.entry("No video yet", "Ainda sem vídeo"),
			Map.entry("Video engine missing (rebuild Sleep with MCEF included)", "Falta o motor de vídeo (refaz o Sleep com o MCEF incluído)"),
			Map.entry("Press K again: panel to change video, pause, skip, redraw or remove", "Carrega outra vez: painel para mudar o vídeo, pausar, avançar, redesenhar ou remover"),
			Map.entry("Video engine missing", "Falta o motor de vídeo"),
			Map.entry("Live", "Live"),
			Map.entry("Wait a Second While We Skip The Ads", "Espera um segundo enquanto saltamos os anúncios"),
			Map.entry("Opening the live on your screen... the first time it gets the tools it needs, this can take a minute.",
					"A abrir a live no teu ecrã... à primeira vez vai buscar as ferramentas de que precisa, pode demorar um minuto."),
			Map.entry("Getting ffmpeg (one time)...", "A obter o ffmpeg (só à primeira vez)..."),
			Map.entry("ffmpeg could not be found or downloaded.", "Não foi possível encontrar nem descarregar o ffmpeg."),
			Map.entry("ffmpeg could not start.", "O ffmpeg não conseguiu arrancar."),
			Map.entry("The live stopped or could not be read. Paste the link again to retry.", "A live parou ou não foi possível lê-la. Cola o link outra vez para tentar de novo."),
			Map.entry("Could not get yt-dlp (needed for lives). Install it yourself (brew install yt-dlp / winget install yt-dlp) and try again.",
					"Não foi possível obter o yt-dlp (precisa dele para as lives). Instala-o tu (brew install yt-dlp / winget install yt-dlp) e tenta outra vez."));

	private static final Pattern CORNER = Pattern.compile("Corner (\\d+)/4 marked\\.");
	private static final Pattern READY = Pattern.compile("Screen ready \\((\\d+) x (\\d+)\\)\\. Paste a YouTube link\\.");
	private static final Pattern AIM = Pattern.compile("Aim at the 4 corner blocks of the screen on a wall and press (.+) on each one\\.");
	private static final Pattern NOW = Pattern.compile("Now: (.*)");
	private static final Pattern ENGINE = Pattern.compile("Video engine: (.*)");

	private WatchPartyText() {
	}

	/** True when the texts should be in Portuguese. */
	private static boolean pt() {
		int i = WatchPartyMod.instance == null ? 0 : WatchPartyMod.instance.languageIdx();
		return i == 2 || i == 0 && Lang.tr("en", "pt").equals("pt");
	}

	public static String t(String en) {
		if (en == null || !pt()) {
			return en;
		}
		String s = PT.get(en);
		if (s != null) {
			return s;
		}
		Matcher m = CORNER.matcher(en);
		if (m.matches()) {
			return "Canto " + m.group(1) + "/4 marcado.";
		}
		m = READY.matcher(en);
		if (m.matches()) {
			return "Ecrã pronto (" + m.group(1) + " x " + m.group(2) + "). Cola um link do YouTube.";
		}
		m = AIM.matcher(en);
		if (m.matches()) {
			return "Aponta para os 4 blocos dos cantos do ecrã numa parede e carrega em " + m.group(1) + " em cada um.";
		}
		m = NOW.matcher(en);
		if (m.matches()) {
			return "A ver: " + m.group(1);
		}
		m = ENGINE.matcher(en);
		if (m.matches()) {
			return "Motor de vídeo: " + m.group(1);
		}
		if (en.startsWith("Could not open this live: ")) {
			return "Não foi possível abrir esta live: " + en.substring("Could not open this live: ".length());
		}
		if (en.startsWith("Could not run yt-dlp: ")) {
			return "Não foi possível executar o yt-dlp: " + en.substring("Could not run yt-dlp: ".length());
		}
		if (en.startsWith("Press ") && en.contains(": aim at the 4 corner blocks")) {
			return en.replace("Press ", "Carrega em ").replace(": aim at the 4 corner blocks on a wall (press ", ": aponta para os 4 blocos dos cantos numa parede (carrega em ").replace(" on each)", " em cada um)");
		}
		if (en.startsWith("Press ") && en.contains(" again: panel")) {
			return en.replace("Press ", "Carrega em ").replace(" again: panel to change video, pause, skip, redraw or remove", " outra vez: painel para mudar o vídeo, pausar, avançar, redesenhar ou remover");
		}
		return en;
	}
}
