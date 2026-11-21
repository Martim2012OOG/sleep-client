# Prompt para pedir a qualquer IA uma extensão nova do Kubo

**Como usar:**
1. Copia tudo a partir da linha `=== PROMPT ===` até ao fim.
2. Cola no ChatGPT, Claude, Gemini ou outra IA.
3. Na última linha ("A extensão que eu quero"), escreve a tua ideia.

A IA devolve:
- a pasta `extensions/<nome>` completa;
- a linha para o `settings.gradle`;
- os ícones;
- as traduções PT;
- a entrada para o catálogo do Lovable;
- os passos para publicar.

**Depois de a IA responder:**
1. Põe a pasta em `solfabric/extensions/<nome>`.
2. Acrescenta a linha ao `settings.gradle`.
3. Corre `./gradlew :extensions:<nome>:build`. O jar fica em `extensions/<nome>/build/libs/`.
4. No Lovable, em /admin/extensions, carrega em **Nova extensão**, preenche os campos com o que a IA deu, carrega o jar e o ícone e marca **Publicada**.

Se o build der erro, cola o erro de volta na mesma conversa com a IA.

=== PROMPT ===

You are writing a new **Kubo extension**. Kubo Client is a Fabric 1.21.11 client mod (yarn mappings, Java 21). An extension is a small, separate Fabric jar that the player downloads in game from **Right Shift → Mods → Extensions**. The client then installs it live, without a restart, when the jar has no mixins.

Follow every rule below exactly. Your code is compiled against the real Kubo source, so only use the API listed here.

## 1. What you must output

Give **every file in full**. No "…", no "rest stays the same".

1. The folder tree of `extensions/<name>/`.
   - `<name>` is lowercase with underscores, e.g. `kill_effects`.
2. `extensions/<name>/build.gradle`: the template in §3, with only the name changed.
3. `extensions/<name>/src/main/resources/fabric.mod.json`: the template in §3.
4. The Java sources:
   - in package `dev.kuboext.<name>`;
   - one entrypoint class `<CamelName>Extension implements dev.sleepclient.ext.KuboExtension`;
   - one class per mod.
5. Translations:
   - Path: `extensions/<name>/src/main/resources/assets/kubo_ext_<name>/pt_pt.tsv`.
   - One line per string: the English text, a TAB, then the Portuguese (Portugal, not Brazil) text.
   - Cover every user-visible string: mod name, description, every option name, choice labels, button labels, HUD text.
6. Menu icons, one per mod. For each icon, give:
   - the PNG at `src/main/resources/assets/sleep_client/textures/gui/icon/<modId>.png`, 64×64, a **white glyph on a transparent background**, simple and thick, like Font Awesome solid;
   - a `.mcmeta` next to it containing exactly `{"texture":{"blur":true}}`.

   If you can't output a binary PNG, give a short Python (Pillow) script that draws it into the right path.
7. Store icon:
   - 256×256 PNG, also as a Python script if needed;
   - a dark rounded square, `#16181D`, with corner radius about 12% of the size;
   - the white glyph centred;
   - no gradients, no text.
8. The line to add to `settings.gradle`: `include 'extensions:<name>'`.
9. The **catalog entry** for the Lovable store, as JSON:

```json
{ "id": "<name with dashes>", "modId": "kubo_ext_<name>", "name": "<Display Name>",
  "description": "<1–2 sentences, English>", "descriptionPt": "<1–2 frases, PT-PT>",
  "version": "1.0.0", "mods": ["<Mod 1>", "<Mod 2>"], "tags": ["<search>", "<words>"],
  "author": "Kubo", "icon": "<name with dashes>.png", "jarFile": "kubo-ext-<name with dashes>-1.0.0.jar" }
```

10. A short "**What's untested**" list. Be honest: you can't run Minecraft.

## 2. Hard rules

- **Client only.** `"environment": "client"`. Nothing may run on a server.
- **Only use the public Kubo API** in §4, plus Minecraft, yarn and Fabric API classes.
  - Never use other `dev.sleepclient.*` classes. Their internals change between versions.
  - The core never knows about your extension, so don't ask for core changes.
- **Prefer no mixins.**
  - A jar without mixins installs and uninstalls live.
  - Use KuboApi hooks, Fabric API events (`ClientTickEvents`, `HudRenderCallback`, `ClientReceiveMessageEvents`, `WorldRenderEvents`, `ClientPlayConnectionEvents`, …) and `Mod.tick()` / `Mod.frame()` first.
  - Only if something truly can't be done otherwise, add mixins:
    - their own package `dev.kuboext.<name>.mixin`;
    - their own `kubo_ext_<name>.mixins.json`, listed in fabric.mod.json under `"mixins"`;
    - a note that this extension needs a game restart.
- **Inventory: the server is always the boss.**
  - Never cancel, delay or change inventory packets.
  - Never move items, call `setStack`, or click slots from background code.
- **No cheats** that break server rules: no aim assist, reach, auto-clicker, x-ray or fly. Visual and QoL mods only.
- **Yarn names for 1.21.11.** Double-check every Minecraft method and class name.
  - Use `DrawContext` for drawing.
  - `MinecraftClient.getInstance()`, or the `mc` field inside a Mod.
  - Text with `c.drawText(mc.textRenderer, string, x, y, argbColour, shadow)`.
  - Colours are ARGB ints with full alpha, e.g. `0xFFFFFFFF`, never `0xFFFFFF`.
- **No heavy work on the render thread.** Network or disk goes on a background thread. Results are handed back with `mc.execute(...)`.
- **Clean `unload()`.**
  - Fabric events can't be unregistered, so guard every callback with a `static volatile boolean active` that `unload()` sets to `false`.
  - Close threads, sockets and textures there too.
- **UI style.** Any custom screen or HUD element must look like the rest of Kubo:
  - dark slate panels, `0xE0161A20`, with a 1 px border `0xFF2A2F38`;
  - corner radius 3 px at most;
  - white text;
  - one accent colour, no blue buttons;
  - no gradients, no centred hero titles, no three identical cards, no "Recommended" badges, no sliding animations;
  - text never goes outside its button.

  Prefer **options** (§4) over custom screens: the Kubo settings page draws them for you.

## 3. Templates (copy exactly, change only `<name>` and the class)

`extensions/<name>/build.gradle`
```gradle
plugins {
	id 'fabric-loom'
}

version = '1.0.0'
group = 'dev.kuboext'

base {
	archivesName = 'kubo-ext-<name-with-dashes>'
}

dependencies {
	minecraft "com.mojang:minecraft:${rootProject.minecraft_version}"
	mappings "net.fabricmc:yarn:${rootProject.yarn_mappings}:v2"
	modImplementation "net.fabricmc:fabric-loader:${rootProject.loader_version}"
	modImplementation "net.fabricmc.fabric-api:fabric-api:${rootProject.fabric_version}"
	// the Kubo client itself (only to compile against: the player already has it)
	compileOnly project(path: ':', configuration: 'namedElements')
}

processResources {
	inputs.property "version", project.version
	filesMatching("fabric.mod.json") {
		expand "version": inputs.properties.version
	}
}

tasks.withType(JavaCompile).configureEach {
	it.options.release = 21
	it.options.encoding = 'UTF-8'
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}
```

`extensions/<name>/src/main/resources/fabric.mod.json`
```json
{
  "schemaVersion": 1,
  "id": "kubo_ext_<name>",
  "version": "${version}",
  "name": "Kubo – <Display Name>",
  "description": "<one line>",
  "authors": ["Kubo"],
  "license": "GPL-3.0-only",
  "environment": "client",
  "entrypoints": { "kubo": ["dev.kuboext.<name>.<CamelName>Extension"] },
  "depends": { "sleep_client": "*", "minecraft": "~1.21.11", "fabricloader": ">=0.18.1" },
  "custom": { "kubo:extension": true }
}
```

Use the `kubo` entrypoint only. **Don't** add `client` or `main` entrypoints: the Kubo loader calls `register` itself.

## 4. The Kubo API (everything you may use)

### `dev.sleepclient.ext.KuboExtension`
```java
public interface KuboExtension {
	void register(KuboApi api);   // at start-up, before settings load (or right after a live install)
	default void unload() {}      // uninstalled while the game runs: switch your hooks off (mods are removed for you)
}
```

### `dev.sleepclient.ext.KuboApi`, received in `register`
```java
void addMod(Mod mod);                         // adds a mod to the Right Shift menu (settings saved automatically)
void addIcon(String modId);                   // registers assets/sleep_client/textures/gui/icon/<modId>.png from your jar
void addTranslations(InputStream tsv);        // "English<TAB>Português" lines
void command(String title, String sub, String iconId, Runnable run);  // entry in the Kubo command palette

// events
void onAttack(Consumer<Entity> c);            // you hit an entity
void onHud(Consumer<DrawContext> c);          // HUD, every frame, before HUD mods
void onHudLast(Consumer<DrawContext> c);      // HUD, every frame, after everything
void onF3Hud(Consumer<DrawContext> c);        // drawn while F3 is open
void onStatsChanged(Runnable r);              // statistics screen data arrived
void onEntityStatus(BiConsumer<LivingEntity, Byte> c);  // entity status packet (e.g. 35 = totem pop)
void onLeftClick(Runnable r);                 // left mouse click in game

// changers (return the input unchanged when you don't want to change it)
void nameDecorator(BiFunction<Entity, Text, Text> f);   // name tags above heads
void tabDecorator(BiFunction<UUID, Text, Text> f);      // names in the Tab list
void hudFilter(Predicate<Mod> p);                       // return false to hide a HUD mod right now
void entityScale(Function<LivingEntity, Float> f);      // return null for "no change"
void totemAnimation(Predicate<ItemStack> p);            // return false to skip the totem pop animation
void hideToasts(BooleanSupplier s);                     // true = hide vanilla toasts
void replacesF3(BooleanSupplier s);                     // true = your mod draws its own F3 instead
void screenFilter(UnaryOperator<Screen> f);             // swap a screen before it opens (return it unchanged otherwise)
```

### `dev.sleepclient.mods.Mod` (extend it for a normal mod)
```java
protected Mod(String name, String id, String description, Category category); // Category.HUD / UTILITY / VISUAL
public final String id, name, description;
public boolean enabled;
protected final MinecraftClient mc;

protected void onEnable();   protected void onDisable();
public void tick();          // every client tick, only while enabled
public void frame();         // every rendered frame, only while enabled
public void init();          // after config load (calls onEnable if enabled; call super.init())

// options: declare them as fields; they are saved and drawn in the settings page automatically, in this order
protected Option.Header header(String title);
protected Option.Bool   bool(String name, boolean def);                                   // .on()
protected Option.Num    num(String name, float def, float min, float max, float step, String suffix); // .f() .i()
protected Option.Col    colour(String name, int argb);                                    // .c()
protected Option.Choice choice(String name, int def, String... names);                    // .idx() .label()
protected Option.Choice tabs(String name, int def, String... names);                      // same, drawn as tabs
protected Option.Text   text(String name, String def, int max, String hint);              // .get()
protected Option.Key    key(String name, int glfwKeyCode);                                // .code()
protected Option.Action action(String name, String button, Runnable run, Supplier<String> status);
// any option: .showIf(() -> otherOption.on()) shows it only when that is true
```
- Option names are the setting keys. Never rename them after release.

### `dev.sleepclient.mods.HudMod` (extends Mod, for something drawn on screen and movable in the HUD editor)
```java
protected HudMod(String name, String id, String description, float defaultX, float defaultY); // 0..1 of the screen
public abstract int width();      // in unscaled pixels
public abstract int height();
protected abstract void renderHud(DrawContext c, boolean edit);  // draw at (0,0); already translated and scaled
// edit == true while the HUD editor is open: draw placeholder content so it can be placed
public final Option.Num scale;    // already added for you
```

### `dev.sleepclient.gui.Notify` (Kubo-style notification, bottom-left, with sound)
```java
Notify.show(Notify.Kind.INFO, "Title", "Second line");   // Kind: INFO, DONE, ERROR, DOWNLOAD, KUBO, FRIEND, MSG, CLIP
```

### `dev.sleepclient.Lang`
```java
Lang.tr("English", "Português")   // returns the right one for the player's language
```

## 5. Reference example (structure to follow)

```java
package dev.kuboext.kill_effects;

import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;

public class KillEffectsExtension implements KuboExtension {
	static volatile boolean active;

	@Override
	public void register(KuboApi api) {
		active = true;
		var tsv = KillEffectsExtension.class.getResourceAsStream("/assets/kubo_ext_kill_effects/pt_pt.tsv");
		if (tsv != null) api.addTranslations(tsv);
		api.addIcon("kill_counter");
		KillCounterMod mod = new KillCounterMod();
		api.addMod(mod);
		api.onAttack(e -> { if (active && mod.enabled) mod.hit(e); });
	}

	@Override
	public void unload() {
		active = false;
	}
}
```
```java
package dev.kuboext.kill_effects;

import dev.sleepclient.mods.HudMod;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class KillCounterMod extends HudMod {
	private final Option.Col textColour = colour("Text Colour", 0xFFFFFFFF);
	private final Option.Bool background = bool("Background", true);
	private int kills;
	private int lastTarget = -1;

	public KillCounterMod() {
		super("Kill Counter", "kill_counter", "Counts the players you kill this session.", 0.01F, 0.42F);
	}

	void hit(Entity e) {
		lastTarget = e.getId();
	}

	@Override
	public void tick() {
		if (mc.world == null || lastTarget == -1) return;
		Entity e = mc.world.getEntityById(lastTarget);
		if (e instanceof LivingEntity le && le.isDead()) {
			kills++;
			lastTarget = -1;
		}
	}

	@Override public int width() { return 70; }
	@Override public int height() { return 16; }

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		String s = "Kills: " + (edit ? 3 : kills);
		if (background.on()) c.fill(0, 0, width(), height(), 0x64000000);
		c.drawText(mc.textRenderer, s, width() / 2 - mc.textRenderer.getWidth(s) / 2, 4, textColour.c(), true);
	}
}
```
`pt_pt.tsv`:
```
Kill Counter	Contador de Kills
Counts the players you kill this session.	Conta os jogadores que matas nesta sessão.
Text Colour	Cor do Texto
Background	Fundo
Kills: 	Kills: 
```

## 6. Before you answer, check

- [ ] Every class compiles against the API above: no invented methods, and yarn 1.21.11 names.
- [ ] `fabric.mod.json` id is `kubo_ext_<name>`, and the entrypoint points to the real class.
- [ ] Every mod id has an icon PNG and a `.mcmeta`, plus `api.addIcon(id)` in `register`.
- [ ] Every visible English string has a PT-PT line in the tsv.
- [ ] `unload()` switches everything off.
- [ ] The catalog JSON `jarFile` equals `kubo-ext-<name-with-dashes>-1.0.0.jar`, which is what Gradle builds.
- [ ] No mixins, or a clear note that a restart is needed.

## A extensão que eu quero

<ESCREVE AQUI A TUA IDEIA, por exemplo: "Uma extensão 'Kill Effects' com 3 mods: efeito de partículas quando matas alguém, som personalizado ao matar e um contador de kills na HUD.">
