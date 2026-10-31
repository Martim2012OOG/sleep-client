package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import dev.solclient.voice.persona.Persona;
import dev.solclient.voice.persona.PersonaHook;
import dev.solclient.voice.persona.PersonaScreen;

/**
 * Personas: pick a character and everything you say through any voice chat sounds like them. It is not tied to one voice mod: the
 * microphone itself is reshaped (see {@link PersonaHook}), so Simple Voice Chat, Plasmo Voice and this client's voice chat all get it.
 */
public class PersonasMod extends Mod {

	public static PersonasMod instance;

	public final Option.Text character = text("Character", "me", 40, "").showIf(() -> false);
	public final Option.Num pitch = num("Fine Pitch", 0, -12, 12, 0.5F, " st");
	public final Option.Num amount = num("Effect Strength", 100, 0, 150, 5, "%");
	public final Option.Num volume = num("Loudness", 0, -12, 12, 1, " dB");
	public final Option.Key openKey = key("Characters Menu Key", 75);
	@SuppressWarnings("unused")
	private final Option.Action open = action("Characters", "Choose", () -> mc.setScreen(new PersonaScreen()), () -> current().name);

	private boolean armed = true;

	public PersonasMod() {
		super("Personas", "personas", "Pick a character and talk as them in any voice chat: deep hacker, squeaky sponge, robot, giant and more. Add your own too.", Category.UTILITY);
		instance = this;
	}

	public Persona current() {
		return Persona.find(character.get());
	}

	public void pick(Persona p) {
		character.set(p.id);
	}

	@Override
	protected void onDisable() {
		PersonaHook.active = null;
	}

	@Override
	public void frame() {
		Persona p = current();
		PersonaHook.pitchAdd = pitch.f();
		PersonaHook.amount = amount.f() / 100F;
		PersonaHook.gainDb = volume.f();
		PersonaHook.active = p.neutral() && pitch.f() == 0 && volume.f() == 0 ? null : p;
		boolean down = Keys.down(openKey.code());
		if (!down) {
			armed = true;
		} else if (armed && mc.currentScreen == null && mc.player != null) {
			armed = false;
			mc.setScreen(new PersonaScreen());
		}
	}
}
