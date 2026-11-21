package com.baom5.deathanimation.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget.PressAction;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public class DeathAnimationsScreen extends Screen {
   private static final int DESIGN_WIDTH = 854;
   private static final int DESIGN_HEIGHT = 480;
   private static final int PANEL_WIDTH = 716;
   private static final int PANEL_HEIGHT = 420;
   private static final int MUTED = -7164235;
   private static final int TEXT = -1509900;
   private static final int TEAL = -12396847;
   private static final int EMBER = -24508;
   private final Screen parent;
   private int tab;
   private int animationPage;
   private float uiScale = 1.0F;
   private double uiOffsetX;
   private double uiOffsetY;
   private final List<DeathAnimationsScreen.Card> visibleCards = new ArrayList<>();
   private final List<DeathAnimationsScreen.SettingSlider> settingSliders = new ArrayList<>();
   private final List<DeathAnimationsScreen.FavouriteButton> favouriteButtons = new ArrayList<>();
   private String hoveredCard;
   private DeathAnimationConfig.Animation randomPreview;
   private String waitingForKeybind;
   private String searchQuery = "";
   private TextFieldWidget searchField;
   private int drawer;
   private final Set<String> categories = new LinkedHashSet<>();
   private String sourceFilter = "";
   private boolean favouritesOnly;
   private boolean enabledOnly;
   private CatalogBrowser.Sort sort = CatalogBrowser.Sort.CATALOG;
   private int sourcePage;
   private boolean filterSources;
   private List<DeathAnimationConfig.Animation> cachedMatches;

   public DeathAnimationsScreen() {
      this(null);
   }

   public DeathAnimationsScreen(Screen parent) {
      super(net.minecraft.text.Text.literal("Death Animations +"));
      this.parent = parent;
   }

   protected void init() {
      this.flushSliderChanges();
      this.settingSliders.clear();
      this.cachedMatches = null;
      this.uiScale = Math.min(this.width / 854.0F, this.height / 480.0F);
      this.uiOffsetX = (this.width - 854.0F * this.uiScale) / 2.0;
      this.uiOffsetY = (this.height - 480.0F * this.uiScale) / 2.0;
      this.clearChildren();
      this.visibleCards.clear();
      this.favouriteButtons.clear();
      int left = 69;
      int top = 30;
      int tabY = top + 60;
      this.addDrawableChild(this.styled("ANIMATIONS", () -> this.switchTab(0), left + 22, tabY, 156, 30, -12396847, () -> this.tab == 0, null, null));
      this.addDrawableChild(this.styled("PERSISTENCE", () -> this.switchTab(1), left + 190, tabY, 156, 30, -9713409, () -> this.tab == 1, null, null));
      this.addDrawableChild(this.styled("EFFECTS", () -> this.switchTab(2), left + 358, tabY, 156, 30, -26040, () -> this.tab == 2, null, null));
      this.addDrawableChild(this.styled("KEYBINDS", () -> this.switchTab(3), left + 526, tabY, 156, 30, -3765249, () -> this.tab == 3, null, null));
      this.addDrawableChild(
         this.styled("⚙", () -> this.switchTab(2), left + 716 - 48, top + 16, 28, 28, -7231320, () -> this.tab == 1 || this.tab == 2, null, null)
      );
      if (this.tab == 0) {
         this.buildAnimationCards(left, top);
      }

      if (this.tab == 1) {
         this.buildPersistenceControls(left, top);
      }

      if (this.tab == 2) {
         this.buildEffectControls(left, top);
      }

      if (this.tab == 3) {
         this.buildKeybindControls(left, top);
      }

      if (this.tab == 1 || this.tab == 2) {
         DeathAnimationsScreen.StyledButton reset = this.styled(
            "RESET ALL SETTINGS", this::resetAllSettings, left + 24, top + 420 - 44, 194, 30, -33925, () -> false, null, null
         );
         reset.setTooltip(Tooltip.of(net.minecraft.text.Text.literal("Restore all mod options, animation selections and favourites to their defaults.")));
         this.addDrawableChild(reset);
      }

      this.addDrawableChild(this.styled("DONE", this::close, left + 716 - 116, top + 420 - 44, 92, 30, 0, () -> false, null, null));
   }

   private DeathAnimationsScreen.StyledButton styled(
      String label,
      Runnable action,
      int x,
      int y,
      int w,
      int h,
      int accent,
      DeathAnimationsScreen.FlagGetter selected,
      DeathAnimationsScreen.FlagGetter on,
      DeathAnimationsScreen.FlagSetter setter
   ) {
      return new DeathAnimationsScreen.StyledButton(x, y, w, h, net.minecraft.text.Text.literal(label), b -> action.run(), accent, selected, on, setter);
   }

   private void switchTab(int destination) {
      this.clearHover();
      this.waitingForKeybind = null;
      this.tab = destination;
      this.clearAndInit();
   }

   private void buildAnimationCards(int left, int top) {
      List<DeathAnimationConfig.Animation> animations = this.matchingAnimations();
      this.addDrawableChild(
         this.styled(
            this.drawer == 1 ? "< CATALOG" : "FILTERS",
            () -> this.openDrawer(this.drawer == 1 ? 0 : 1),
            left + 22,
            top + 98,
            104,
            22,
            -12396847,
            () -> this.drawer == 1,
            null,
            null
         )
      );

      this.addDrawableChild(this.styled(switch (this.sort) {
         case CATALOG -> "ORDER: DEFAULT";
         case NAME -> "ORDER: A-Z";
         case NEWEST -> "ORDER: NEWEST";
      }, () -> {
         this.sort = CatalogBrowser.Sort.values()[(this.sort.ordinal() + 1) % CatalogBrowser.Sort.values().length];
         this.resetBrowse();
      }, left + 134, top + 98, 132, 22, -12396847, () -> false, null, null));
      if (this.drawer == 1) {
         this.buildFilters(left, top);
      } else {
         int itemCount = animations.size() + 1;
         int pageCount = (itemCount + 5) / 6;
         this.animationPage = Math.clamp(this.animationPage, 0, pageCount - 1);
         int first = this.animationPage * 6;

         for (int offset = 0; offset < 6 && first + offset < itemCount; offset++) {
            int item = first + offset;
            boolean random = item == 0;
            DeathAnimationConfig.Animation animation = random ? null : animations.get(item - 1);
            int column = offset % 3;
            int row = offset / 3;
            int x = left + 22 + column * 224;
            int y = top + 132 + row * 98;
            int accent = random ? -24508 : this.accent(animation);
            DeathAnimationsScreen.Card card = new DeathAnimationsScreen.Card(random ? "random" : animation.name(), x, y, 210, 86, animation, random, accent);
            this.visibleCards.add(card);
            if (!random) {
               DeathAnimationsScreen.FavouriteButton favourite = new DeathAnimationsScreen.FavouriteButton(x + 186, y + 6, animation);
               this.favouriteButtons.add(favourite);
               this.addDrawableChild(favourite);
            }

            this.addDrawableChild(
               this.styled(
                  this.onOff(random ? DeathAnimationConfig.INSTANCE.randomMode : DeathAnimationConfig.INSTANCE.isEnabled(animation)),
                  () -> {
                     if (random) {
                        DeathAnimationConfig.INSTANCE.randomMode = !DeathAnimationConfig.INSTANCE.randomMode;
                     } else {
                        DeathAnimationConfig.INSTANCE.setEnabled(animation, !DeathAnimationConfig.INSTANCE.isEnabled(animation));
                        this.clearHover();
                        this.clearAndInit();
                     }

                     DeathAnimationConfig.INSTANCE.save();
                  },
                  x + 150,
                  y + 54,
                  52,
                  24,
                  accent,
                  () -> random && DeathAnimationConfig.INSTANCE.randomMode,
                  () -> random ? DeathAnimationConfig.INSTANCE.randomMode : DeathAnimationConfig.INSTANCE.isEnabled(animation),
                  null
               )
            );
            this.addDrawableChild(this.styled("VIEW", () -> {
               DeathAnimationConfig.Animation choice = random ? this.randomPreview : animation;
               if (random && choice == null) {
                  List<DeathAnimationConfig.Animation> enabled = ReleaseProfile.animations().stream().filter(DeathAnimationConfig.INSTANCE::isEnabled).toList();
                  choice = enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
               }

               if (choice != null) {
                  this.close();
                  MinecraftClient.getInstance().setScreen(null);
                  DeathEffectController.playPreview(choice);
               }
            }, x + 80, y + 54, 62, 24, accent, () -> false, null, null));
         }

         int pageY = top + 338;
         this.searchField = new TextFieldWidget(this.textRenderer, left + 274, pageY, 300, 28, net.minecraft.text.Text.literal("Search death animations"));
         this.searchField.setMaxLength(48);
         this.searchField.setPlaceholder(net.minecraft.text.Text.literal("Search " + ReleaseProfile.animations().size() + " styles..."));
         this.searchField.setText(this.searchQuery);
         this.searchField.setChangedListener(value -> {
            if (!value.equals(this.searchQuery)) {
               this.searchQuery = value;
               this.animationPage = 0;
               this.clearHover();
               this.clearAndInit();
               this.setFocused(this.searchField);
            }
         });
         this.addDrawableChild(this.searchField);
         this.addDrawableChild(this.styled("CLEAR ALL", this::clearFilters, left + 586, pageY, 106, 28, -12396847, () -> false, null, null));
         this.addDrawableChild(this.styled("<", () -> {
            if (this.animationPage > 0) {
               this.animationPage--;
               this.clearHover();
               this.clearAndInit();
            }
         }, left + 24, pageY, 34, 28, -12396847, () -> false, null, null));
         TextFieldWidget jump = new TextFieldWidget(this.textRenderer, left + 66, pageY, 48, 28, net.minecraft.text.Text.literal("Page number"));
         jump.setMaxLength(5);
         jump.setText(Integer.toString(this.animationPage + 1));
         this.addDrawableChild(jump);
         this.addDrawableChild(this.styled("/ " + pageCount + " GO", () -> {
            try {
               this.animationPage = Math.clamp(Integer.parseInt(jump.getText()) - 1, 0, pageCount - 1);
            } catch (NumberFormatException var4x) {
            }

            this.clearHover();
            this.clearAndInit();
         }, left + 120, pageY, 78, 28, -12396847, () -> false, null, null));
         this.addDrawableChild(this.styled(">", () -> {
            if (this.animationPage + 1 < pageCount) {
               this.animationPage++;
               this.clearHover();
               this.clearAndInit();
            }
         }, left + 206, pageY, 34, 28, -12396847, () -> false, null, null));
         List<DeathAnimationConfig.Animation> pool = animations.stream().filter(a -> a != DeathAnimationConfig.Animation.NONE).toList();
         DeathAnimationsScreen.StyledButton apply = this.styled("USE THESE " + pool.size() + " FOR RANDOM", () -> {
            DeathAnimationConfig.INSTANCE.useForRandom(pool);
            this.clearHover();
            this.clearAndInit();
         }, left + 24, top + 376, 254, 30, -24508, () -> false, null, null);
         apply.active = !pool.isEmpty();
         this.addDrawableChild(apply);
      }
   }

   private List<DeathAnimationConfig.Animation> matchingAnimations() {
      if (this.cachedMatches != null) {
         return this.cachedMatches;
      }

      DeathAnimationConfig config = DeathAnimationConfig.INSTANCE;
      this.cachedMatches = CatalogBrowser.matching(
         this.searchQuery,
         this.categories,
         this.sourceFilter,
         this.favouritesOnly,
         this.enabledOnly,
         this.sort,
         new HashSet<>(config.favourites),
         new HashSet<>(config.enabledAnimations)
      );
      return this.cachedMatches;
   }

   private void resetBrowse() {
      this.animationPage = 0;
      this.clearHover();
      this.clearAndInit();
   }

   private void clearFilters() {
      this.searchQuery = "";
      this.sourceFilter = "";
      this.categories.clear();
      this.favouritesOnly = this.enabledOnly = false;
      this.sourcePage = 0;
      this.resetBrowse();
   }

   private void openDrawer(int next) {
      this.clearHover();
      this.drawer = next;
      this.clearAndInit();
   }

   private String selectedFilter() {
      if (!this.sourceFilter.isBlank()) {
         return this.sourceFilter;
      } else if (!this.categories.isEmpty()) {
         return this.categories.iterator().next();
      } else if (this.favouritesOnly) {
         return "Favourites";
      } else {
         return this.enabledOnly ? "Enabled" : "All animations";
      }
   }

   private void chooseFilter(String category, String source, boolean favourites, boolean enabled) {
      this.categories.clear();
      if (category != null) {
         this.categories.add(category);
      }

      this.sourceFilter = source == null ? "" : source;
      this.favouritesOnly = favourites;
      this.enabledOnly = enabled;
      this.searchQuery = "";
      this.drawer = 0;
      this.animationPage = 0;
      this.clearHover();
      this.clearAndInit();
   }

   private void buildFilters(int left, int top) {
      this.addDrawableChild(this.styled("CATEGORIES", () -> {
         this.filterSources = false;
         this.sourcePage = 0;
         this.clearAndInit();
      }, left + 24, top + 134, 210, 24, -12396847, () -> !this.filterSources, null, null));
      this.addDrawableChild(this.styled("FRANCHISES", () -> {
         this.filterSources = true;
         this.sourcePage = 0;
         this.clearAndInit();
      }, left + 248, top + 134, 210, 24, -12396847, () -> this.filterSources, null, null));
      this.addDrawableChild(
         this.styled("ALL ANIMATIONS", () -> this.chooseFilter(null, null, false, false), left + 472, top + 134, 220, 24, -24508, () -> false, null, null)
      );
      List<String> choices = this.filterSources ? CatalogBrowser.sources() : CatalogBrowser.CATEGORIES;
      int pageSize = this.filterSources ? 12 : 15;
      int pages = Math.max(1, (choices.size() + pageSize - 1) / pageSize);
      this.sourcePage = Math.clamp(this.sourcePage, 0, pages - 1);

      for (int n = 0; n < pageSize && this.sourcePage * pageSize + n < choices.size(); n++) {
         String value = choices.get(this.sourcePage * pageSize + n);
         List<DeathAnimationConfig.Animation> matches = CatalogBrowser.matching(
            "", this.filterSources ? Set.of() : Set.of(value), this.filterSources ? value : "", false, false, CatalogBrowser.Sort.CATALOG, Set.of(), Set.of()
         );
         String label = this.textRenderer.trimToWidth(value, 164) + " (" + matches.size() + ")";
         this.addDrawableChild(
            this.styled(
               label,
               () -> this.chooseFilter(this.filterSources ? null : value, this.filterSources ? value : null, false, false),
               left + 24 + n % 3 * 224,
               top + 168 + n / 3 * 34,
               210,
               29,
               -12396847,
               () -> this.filterSources ? this.sourceFilter.equals(value) : this.categories.contains(value),
               null,
               null
            )
         );
      }

      if (pages > 1) {
         this.addDrawableChild(this.styled("<", () -> {
            this.sourcePage = (this.sourcePage + pages - 1) % pages;
            this.clearAndInit();
         }, left + 24, top + 310, 40, 24, -12396847, () -> false, null, null));
         this.addDrawableChild(this.styled(this.sourcePage + 1 + " / " + pages, () -> {}, left + 72, top + 310, 90, 24, -12396847, () -> false, null, null));
         this.addDrawableChild(this.styled(">", () -> {
            this.sourcePage = (this.sourcePage + 1) % pages;
            this.clearAndInit();
         }, left + 170, top + 310, 40, 24, -12396847, () -> false, null, null));
      }

      this.addDrawableChild(
         this.styled(
            "FAVOURITES", () -> this.chooseFilter(null, null, true, false), left + 24, top + 340, 210, 30, -24508, () -> this.favouritesOnly, null, null
         )
      );
      this.addDrawableChild(
         this.styled("ENABLED", () -> this.chooseFilter(null, null, false, true), left + 248, top + 340, 210, 30, -12396847, () -> this.enabledOnly, null, null)
      );
      this.addDrawableChild(this.styled("BACK", () -> this.openDrawer(0), left + 472, top + 340, 220, 30, -12396847, () -> false, null, null));
   }

   private String onOff(boolean enabled) {
      return enabled ? "ON" : "OFF";
   }

   private void buildPersistenceControls(int left, int top) {
      DeathAnimationConfig c = DeathAnimationConfig.INSTANCE;
      this.addSetting(
         left + 24,
         top + 136,
         322,
         "Keep effects after death",
         "Let visual effects remain in the world",
         () -> c.persistEffects,
         v -> c.persistEffects = v,
         -12396847
      );
      this.addSetting(
         left + 370,
         top + 136,
         322,
         "Remove previous effect",
         "Clear older effects when a new one starts",
         () -> c.removePrevious,
         v -> c.removePrevious = v,
         -9713409
      );
      DeathAnimationsScreen.StyledButton onlyKills = this.styled(
         DeathVisibility.MY_KILLS.label,
         () -> this.setDeathVisibility(DeathVisibility.MY_KILLS),
         left + 286,
         top + 222,
         196,
         28,
         -12396847,
         () -> DeathVisibility.orDefault(c.deathVisibility) == DeathVisibility.MY_KILLS,
         null,
         null
      );
      onlyKills.setTooltip(
         Tooltip.of(
            net.minecraft.text.Text.literal("Animate only players whose deaths are attributed to you. Your own deaths and unknown killers are skipped. Previews still work.")
         )
      );
      this.addDrawableChild(onlyKills);
      DeathAnimationsScreen.StyledButton everyone = this.styled(
         DeathVisibility.EVERYONE_NEARBY.label,
         () -> this.setDeathVisibility(DeathVisibility.EVERYONE_NEARBY),
         left + 492,
         top + 222,
         188,
         28,
         -24508,
         () -> DeathVisibility.orDefault(c.deathVisibility) == DeathVisibility.EVERYONE_NEARBY,
         null,
         null
      );
      everyone.setTooltip(
         Tooltip.of(net.minecraft.text.Text.literal("Animate all detected deaths nearby, including your own. More simultaneous animations may cause lag or FPS drops."))
      );
      this.addDrawableChild(everyone);
      this.addSettingSlider(left + 24, top + 306, "EFFECT DURATION", " sec", 2, 60, () -> c.durationSeconds, value -> c.durationSeconds = value, -24508);
      this.addSettingSlider(
         left + 370, top + 306, "MAX EFFECTS", " effects", 1, 24, () -> c.maxPersistentEffects, value -> c.maxPersistentEffects = value, -7496705
      );
   }

   private void setDeathVisibility(DeathVisibility visibility) {
      DeathAnimationConfig.INSTANCE.deathVisibility = visibility;
      DeathAnimationConfig.INSTANCE.save();
      this.clearAndInit();
   }

   private void buildEffectControls(int left, int top) {
      DeathAnimationConfig c = DeathAnimationConfig.INSTANCE;
      this.addSetting(left + 24, top + 132, 322, "Death animations", "Enable or disable all styles", () -> c.enabled, v -> c.enabled = v, -12396847);
      this.addSetting(
         left + 370, top + 132, 322, "Visual effects", "Particles and decorative trails", () -> c.visualEffects, v -> c.visualEffects = v, -8734465
      );
      this.addSetting(left + 24, top + 190, 322, "Animation sounds", "Play local sound effects", () -> c.sounds, v -> c.sounds = v, -24508);
      this.addSetting(
         left + 370, top + 190, 322, "Cinematic effects", "More trails and small details", () -> c.cinematicEffects, v -> c.cinematicEffects = v, -3765249
      );
      this.addSetting(left + 24, top + 248, 322, "Red death tint", "Red flash with any death style", () -> c.redDeathTint, v -> c.redDeathTint = v, -42650);
      this.addSetting(
         left + 370,
         top + 248,
         322,
         "Hide corpse shadows",
         "Remove the circle under dead bodies",
         () -> c.hideCorpseShadows,
         v -> c.hideCorpseShadows = v,
         -6901830
      );
      this.addSetting(
         left + 24, top + 306, 322, "Debris physics", "Collision, gravity, and quiet rest", () -> c.physicsDebris, v -> c.physicsDebris = v, -8275713
      );
      this.addDrawableChild(this.styled(String.format(Locale.ROOT, "%.2fx", c.animationSpeed), () -> {
         float[] speeds = new float[]{0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 2.0F};
         int index = 0;

         while (index < speeds.length && speeds[index] <= c.animationSpeed + 0.01F) {
            index++;
         }

         c.animationSpeed = speeds[index % speeds.length];
         c.save();
         this.clearAndInit();
      }, left + 370 + 246, top + 306 + 19, 62, 28, -24508, () -> false, null, null));
   }

   private void buildKeybindControls(int left, int top) {
      this.addDrawableChild(
         this.styled(
            this.keyButtonLabel("menu"), () -> this.beginKeybindCapture("menu"), left + 24 + 322 - 96, top + 169, 82, 30, -12396847, () -> false, null, null
         )
      );
      this.addDrawableChild(
         this.styled(
            this.keyButtonLabel("preview"),
            () -> this.beginKeybindCapture("preview"),
            left + 370 + 322 - 96,
            top + 169,
            82,
            30,
            -24508,
            () -> false,
            null,
            null
         )
      );
      DeathAnimationsScreen.StyledButton reset = this.styled("RESET ALL KEYBINDS", () -> {
         this.waitingForKeybind = null;
         DeathanimationClient.resetKeybinds();
         this.clearAndInit();
      }, left + 24, top + 310, 220, 30, -3765249, () -> false, null, null);
      reset.setTooltip(Tooltip.of(net.minecraft.text.Text.literal("Restore Open Menu to O and Preview to P.")));
      this.addDrawableChild(reset);
   }

   private void resetAllSettings() {
      this.clearHover();
      this.waitingForKeybind = null;
      DeathAnimationConfig.resetToDefaults();
      this.searchQuery = "";
      this.sourceFilter = "";
      this.categories.clear();
      this.favouritesOnly = false;
      this.enabledOnly = false;
      this.animationPage = 0;
      this.sourcePage = 0;
      this.drawer = 0;
      this.sort = CatalogBrowser.Sort.CATALOG;
      this.clearAndInit();
   }

   private void addSettingSlider(int x, int y, String label, String unit, int minimum, int maximum, IntSupplier getter, IntConsumer setter, int accent) {
      DeathAnimationsScreen.SettingSlider slider = new DeathAnimationsScreen.SettingSlider(x, y, label, unit, minimum, maximum, getter, setter, accent);
      this.settingSliders.add(slider);
      this.addDrawableChild(slider);
   }

   private void flushSliderChanges() {
      for (DeathAnimationsScreen.SettingSlider slider : this.settingSliders) {
         slider.saveIfChanged();
      }
   }

   private String keyButtonLabel(String keybind) {
      return keybind.equals(this.waitingForKeybind)
         ? "PRESS KEY"
         : (keybind.equals("menu") ? DeathanimationClient.OPEN_MENU : DeathanimationClient.PREVIEW).getBoundKeyLocalizedText().getString();
   }

   private void beginKeybindCapture(String keybind) {
      this.waitingForKeybind = keybind;
      this.clearAndInit();
   }

   private void addSetting(
      int x, int y, int w, String title, String description, DeathAnimationsScreen.FlagGetter getter, DeathAnimationsScreen.FlagSetter setter, int accent
   ) {
      this.addDrawableChild(this.styled(this.onOff(getter.get()), () -> {
         setter.set(!getter.get());
         DeathAnimationConfig.INSTANCE.save();
         this.clearAndInit();
      }, x + w - 76, y + 19, 62, 28, accent, () -> false, getter, setter));
   }

   private void clearHover() {
      this.hoveredCard = null;
      this.randomPreview = null;
      DeathEffectController.setHoverPreview(null);
   }

   private void updateHover(double mouseX, double mouseY) {
      if (this.tab != 0) {
         this.clearHover();
      } else if (this.drawer == 1) {
         this.clearHover();
      } else {
         DeathAnimationsScreen.Card current = null;

         for (DeathAnimationsScreen.Card card : this.visibleCards) {
            if (mouseX >= card.x && mouseX < card.x + card.width && mouseY >= card.y && mouseY < card.y + card.height) {
               current = card;
               break;
            }
         }

         String key = current == null ? null : current.key;
         if (!Objects.equals(key, this.hoveredCard)) {
            this.hoveredCard = key;
            if (current == null) {
               this.randomPreview = null;
               DeathEffectController.setHoverPreview(null);
            } else if (current.random) {
               List<DeathAnimationConfig.Animation> enabled = ReleaseProfile.animations().stream().filter(DeathAnimationConfig.INSTANCE::isEnabled).toList();
               this.randomPreview = enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
               DeathEffectController.setHoverPreview(this.randomPreview);
            } else {
               this.randomPreview = null;
               DeathEffectController.setHoverPreview(current.animation);
            }
         }
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1207563245);
      double virtualMouseX = (mouseX - this.uiOffsetX) / this.uiScale;
      double virtualMouseY = (mouseY - this.uiOffsetY) / this.uiScale;
      this.updateHover(virtualMouseX, virtualMouseY);
      Matrix3x2fStack pose = context.getMatrices();
      pose.pushMatrix();
      pose.translate((float)this.uiOffsetX, (float)this.uiOffsetY);
      pose.scale(this.uiScale, this.uiScale);
      int left = 69;
      int top = 30;
      this.drawRounded(context, left - 4, top + 5, 724, 424, 10, 1711276032);
      this.gradient(context, left, top, 716, 420, -15191236, -15983579);
      this.outline(context, left, top, 716, 420, -11371131);
      this.gradient(context, left + 1, top + 1, 714, 3, -10690346, -23990);
      context.drawItem(new ItemStack(Items.FIRE_CHARGE), left + 236, top + 16);
      context.drawCenteredTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal("DEATH ANIMATIONS +"), 437, top + 18, -1509900);
      context.drawCenteredTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal("CLIENT SIDE  •  STYLE YOUR FINAL MOMENT"), 427, top + 39, -7164235);
      String tip = this.tab == 0
         ? "HOVER TO PREVIEW     |     VIEW PLAYS A SCENE IN FRONT OF YOU"
         : (
            this.tab == 1
               ? "TUNE HOW LONG CLIENT EFFECTS STAY AROUND"
               : (
                  this.tab == 2
                     ? "PICK THE LOOK AND SOUND OF EACH DEATH"
                     : (
                        this.waitingForKeybind == null
                           ? "CLICK A BIND TO CHANGE IT     |     ESC CANCELS"
                           : "PRESS A KEY FOR THE SELECTED ACTION     |     ESC CANCELS"
                     )
               )
         );
      if (this.tab != 0) {
         context.drawCenteredTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal(tip), 427, top + 101, -4664621);
      } else {
         context.drawTextWithShadow(
            this.textRenderer,
            this.textRenderer
               .trimToWidth(
                  this.drawer == 1 ? "Choose one to see its animations" : this.selectedFilter() + "  |  " + this.matchingAnimations().size() + " results", 382
               ),
            left + 300,
            top + 105,
            -7164235
         );
      }

      context.fill(left + 22, top + 120, left + 716 - 22, top + 121, -13282982);
      if (this.tab == 0) {
         if (this.drawer == 0) {
            for (DeathAnimationsScreen.Card card : this.visibleCards) {
               this.drawCard(context, card);
            }
         }

         if (this.drawer == 0 && this.matchingAnimations().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No matching styles. Try clearing filters.", 427, top + 260, -7164235);
         }

         DeathAnimationsScreen.Card previewCard = this.visibleCards.stream().filter(card -> card.key.equals(this.hoveredCard)).findFirst().orElse(null);
         pose.popMatrix();
         if (previewCard != null && DeathEffectController.previewAvatar() != null) {
            int x1 = this.screenX(previewCard.x + 8);
            int y1 = this.screenY(previewCard.y + 9);
            int x2 = this.screenX(previewCard.x + 68);
            int y2 = this.screenY(previewCard.y + 75);
            float modelScale = Math.max(3.0F, DeathSceneRenderer.previewZoom() * this.uiScale);
            context.enableScissor(x1, y1, x2, y2);
            DeathSceneRenderer.previewBody(context, modelScale, x1, y1, x2, y2);
            DeathSceneRenderer.previewProps(context, modelScale, x1, y1, x2, y2);
            PreviewEffects.draw(context, DeathEffectController.hoverScene(), x1, y1, x2, y2);
            context.disableScissor();
         }

         pose.pushMatrix();
         pose.translate((float)this.uiOffsetX, (float)this.uiOffsetY);
         pose.scale(this.uiScale, this.uiScale);
      } else {
         this.drawSettingsRows(context, left, top);
      }

      if (this.tab == 3) {
         this.drawKeybindRows(context, left, top);
      }

      String footer = this.tab == 0
         ? (this.drawer == 1 ? "PICK A CATEGORY OR FRANCHISE TO OPEN ITS ANIMATIONS" : "STAR TO FAVOURITE   |   HOVER PREVIEWS; VIEW PLAYS IN WORLD")
         : "SAVED ON THIS CLIENT";
      DeathScene hoverScene = DeathEffectController.hoverScene();
      if (this.tab == 0 && hoverScene != null && AnimationCatalog.contains(hoverScene.animation)) {
         AnimationCatalog.Recipe recipe = AnimationCatalog.recipe(hoverScene.animation);
         if (recipe.reference() != null && !recipe.reference().isBlank()) {
            footer = hoverScene.animation.label + "  |  " + recipe.reference();
         } else if (recipe.collection() != null) {
            footer = hoverScene.animation.label + "  |  " + recipe.collection();
         }

         CatalogBrowser.Entry metadata = CatalogBrowser.entry(hoverScene.animation);
         if (metadata != null && "pending-world-review".equals(metadata.reviewStatus())) {
            footer = "REVIEW CANDIDATE  |  " + footer;
         }
      }

      int footerX = this.tab != 1 && this.tab != 2 ? 24 : 232;
      int footerWidth = this.tab == 0 ? 660 : (this.tab == 3 ? 540 : 344);
      if (this.tab != 0 || this.drawer == 0) {
         context.drawTextWithShadow(
            this.textRenderer,
            this.textRenderer.trimToWidth(footer, footerWidth),
            left + footerX,
            top + (this.tab == 0 ? (this.drawer == 0 ? 325 : 129) : 386),
            -7164235
         );
      }

      super.render(context, (int)virtualMouseX, (int)virtualMouseY, deltaTicks);
      this.drawGearIcon(context, left + 716 - 48, top + 16);
      pose.popMatrix();
      this.drawFavouriteTooltip(context, mouseX, mouseY);
   }

   private void drawFavouriteTooltip(DrawContext graphics, int mouseX, int mouseY) {
      DeathAnimationsScreen.FavouriteButton hovered = this.favouriteButtons.stream().filter(button -> button.isSelected()).findFirst().orElse(null);
      if (hovered != null) {
         String label = "Favourite " + hovered.animation.label;
         int maxTextWidth = Math.max(1, Math.min(240, this.width - 16));
         List<OrderedText> lines = this.textRenderer.wrapLines(net.minecraft.text.Text.literal(label), maxTextWidth);
         int textWidth = lines.stream().mapToInt(this.textRenderer::getWidth).max().orElse(0);
         int boxWidth = textWidth + 12;
         int boxHeight = lines.size() * (9 + 1) + 10;
         int x = mouseX + 12;
         int y = mouseY + 12;
         if (x + boxWidth > this.width - 4) {
            x = mouseX - boxWidth - 12;
         }

         if (y + boxHeight > this.height - 4) {
            y = mouseY - boxHeight - 12;
         }

         x = Math.clamp(x, 4, Math.max(4, this.width - boxWidth - 4));
         y = Math.clamp(y, 4, Math.max(4, this.height - boxHeight - 4));
         this.drawRounded(graphics, x, y, boxWidth, boxHeight, 4, -267386864);
         this.outline(graphics, x, y, boxWidth, boxHeight, -10869907);

         for (int line = 0; line < lines.size(); line++) {
            graphics.drawTextWithShadow(this.textRenderer, lines.get(line), x + 6, y + 5 + line * (9 + 1), -461569);
         }
      }
   }

   private void drawCard(DrawContext graphics, DeathAnimationsScreen.Card card) {
      boolean hovered = card.key.equals(this.hoveredCard);
      this.drawRounded(graphics, card.x, card.y, card.width, card.height, 6, -16247780);
      this.gradient(graphics, card.x + 1, card.y + 1, card.width - 2, card.height - 2, hovered ? -14665405 : -15259342, -15589078);
      this.outline(graphics, card.x, card.y, card.width, card.height, hovered ? card.accent : -13809840);
      this.gradient(graphics, card.x + 1, card.y + 1, card.width - 2, 2, card.accent, this.shade(card.accent, 0.42F));
      this.drawRounded(graphics, card.x + 7, card.y + 8, 62, 68, 4, -16181989);
      this.gradient(graphics, card.x + 8, card.y + 9, 60, 66, -15258830, -15918302);
      if (!card.key.equals(this.hoveredCard) || MinecraftClient.getInstance().player == null) {
         graphics.drawItem(new ItemStack(card.random ? Items.FIRE_CHARGE : this.icon(card.animation)), card.x + 25, card.y + 31);
      }

      String title = card.random ? "Random" : card.animation.label;
      List<OrderedText> titleLines = this.textRenderer.wrapLines(net.minecraft.text.Text.literal(title), 88);
      int titleCount = Math.min(2, titleLines.size());

      for (int line = 0; line < titleCount; line++) {
         graphics.drawTextWithShadow(this.textRenderer, titleLines.get(line), card.x + 80, card.y + 10 + line * 10, -1509900);
      }

      List<OrderedText> description = this.textRenderer
         .wrapLines(net.minecraft.text.Text.literal(card.random ? "Pick from enabled styles" : this.cardDescription(card.animation)), 124);

      for (int i = 0; i < Math.min(titleCount > 1 ? 1 : 2, description.size()); i++) {
         graphics.drawTextWithShadow(this.textRenderer, description.get(i), card.x + 80, card.y + (titleCount > 1 ? 40 : 28) + i * 10, -7164235);
      }
   }

   private void drawSettingsRows(DrawContext graphics, int left, int top) {
      if (this.tab == 1) {
         this.drawSettingCard(graphics, left + 24, top + 136, 322, "Keep effects after death", "Leave visual effects in the world", -12396847);
         this.drawSettingCard(graphics, left + 370, top + 136, 322, "Remove previous effect", "Clear older effects when a new one starts", -9713409);
         this.drawRounded(graphics, left + 24, top + 212, 668, 80, 5, -16115938);
         this.gradient(graphics, left + 25, top + 213, 666, 78, -14994371, -15522258);
         graphics.drawTextWithShadow(this.textRenderer, "Animate deaths", left + 40, top + 232, -1509900);
         boolean everyone = DeathVisibility.orDefault(DeathAnimationConfig.INSTANCE.deathVisibility) == DeathVisibility.EVERYONE_NEARBY;
         graphics.drawTextWithShadow(
            this.textRenderer,
            everyone ? "May cause lag when many nearby players die." : "Only your kills. Unknown killers and your own deaths are skipped.",
            left + 40,
            top + 270,
            everyone ? -19364 : -7164235
         );
      } else if (this.tab == 2) {
         this.drawSettingCard(graphics, left + 24, top + 132, 322, "Death animations", "Enable or disable all styles", -12396847);
         this.drawSettingCard(graphics, left + 370, top + 132, 322, "Visual effects", "Particles and decorative trails", -8734465);
         this.drawSettingCard(graphics, left + 24, top + 190, 322, "Animation sounds", "Play local sound effects", -24508);
         this.drawSettingCard(graphics, left + 370, top + 190, 322, "Cinematic effects", "More trails and small details", -3765249);
         this.drawSettingCard(graphics, left + 24, top + 248, 322, "Red death tint", "Red flash with any death style", -42650);
         this.drawSettingCard(graphics, left + 370, top + 248, 322, "Hide corpse shadows", "Remove the circle under dead bodies", -6901830);
         this.drawSettingCard(graphics, left + 24, top + 306, 322, "Debris physics", "Collision, gravity, and quiet rest", -8275713);
         this.drawSettingCard(graphics, left + 370, top + 306, 322, "Playback speed", "Same timing in previews and worlds", -24508);
      }
   }

   private void drawKeybindRows(DrawContext graphics, int left, int top) {
      this.drawKeybindCard(graphics, left + 24, top + 148, "Open Death Animations +", "Open this client menu", -12396847);
      this.drawKeybindCard(graphics, left + 370, top + 148, "Preview animations", "Play the local showcase", -24508);
      graphics.drawItem(new ItemStack(Items.COMPASS), left + 34, top + 167);
      graphics.drawItem(new ItemStack(Items.FIREWORK_STAR), left + 380, top + 167);
      graphics.drawTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal("Change these controls to any keyboard key."), left + 24, top + 252, -7164235);
      graphics.drawTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal("The preview runs locally and does not damage your player."), left + 24, top + 271, -7164235);
   }

   private void drawKeybindCard(DrawContext graphics, int x, int y, String title, String description, int accent) {
      this.drawRounded(graphics, x, y, 322, 66, 5, -16115938);
      this.gradient(graphics, x + 1, y + 1, 320, 64, -14994371, -15522258);
      graphics.fill(x + 1, y + 1, x + 3, y + 65, accent);
      graphics.drawTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal(title), x + 42, y + 16, -1509900);
      graphics.drawTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal(description), x + 42, y + 38, -7164235);
   }

   private void drawGearIcon(DrawContext graphics, int x, int y) {
      int c = -2036501;
      graphics.fill(x + 11, y + 4, x + 17, y + 8, c);
      graphics.fill(x + 11, y + 20, x + 17, y + 24, c);
      graphics.fill(x + 4, y + 11, x + 8, y + 17, c);
      graphics.fill(x + 20, y + 11, x + 24, y + 17, c);
      graphics.fill(x + 7, y + 7, x + 11, y + 10, c);
      graphics.fill(x + 17, y + 7, x + 21, y + 10, c);
      graphics.fill(x + 7, y + 18, x + 11, y + 21, c);
      graphics.fill(x + 17, y + 18, x + 21, y + 21, c);
      graphics.fill(x + 8, y + 8, x + 20, y + 20, c);
      graphics.fill(x + 11, y + 11, x + 17, y + 17, -15257286);
   }

   private void drawSettingCard(DrawContext graphics, int x, int y, int w, String title, String desc, int accent) {
      int height = this.tab == 2 ? 56 : 66;
      this.drawRounded(graphics, x, y, w, height, 5, -16115938);
      this.gradient(graphics, x + 1, y + 1, w - 2, height - 2, -14994371, -15522258);
      graphics.fill(x + 1, y + 1, x + 3, y + height - 1, accent);
      graphics.drawTextWithShadow(this.textRenderer, net.minecraft.text.Text.literal(title), x + 16, y + 16, -1509900);
      graphics.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(desc, w - 106), x + 16, y + 38, -7164235);
   }

   private Item icon(DeathAnimationConfig.Animation animation) {
      if (AnimationCatalog.contains(animation)) {
         return AnimationCatalog.icon(animation);
      }

      return switch (animation) {
         case RAGDOLL, PHYSICS_RAGDOLL, MOMENTUM_RAGDOLL -> Items.LEATHER_CHESTPLATE;
         case GRAVE -> Items.STONE_BRICKS;
         case GHOST -> Items.SOUL_LANTERN;
         case BURN -> Items.FIRE_CHARGE;
         case EXPLOSION, GORY_EXPLOSION -> Items.FIREWORK_STAR;
         case LIGHTNING -> Items.LIGHTNING_ROD;
         case FREEZE -> Items.PACKED_ICE;
         case OPPONENT -> Items.PLAYER_HEAD;
         case ANVIL -> Items.ANVIL;
         case GOLD -> Items.GOLD_BLOCK;
         case HEAVEN -> Items.END_ROD;
         case AIR -> Items.FEATHER;
         case VOID -> Items.ENDER_PEARL;
         case ENDER -> Items.ENDER_EYE;
         case TOTEM -> Items.TOTEM_OF_UNDYING;
         case DUST -> Items.SAND;
         case NONE -> Items.BARRIER;
         default -> Items.NETHER_STAR;
      };
   }

   private int accent(DeathAnimationConfig.Animation animation) {
      if (AnimationCatalog.contains(animation)) {
         return AnimationCatalog.recipe(animation).accent();
      }

      return switch (animation) {
         case RAGDOLL, PHYSICS_RAGDOLL, MOMENTUM_RAGDOLL -> -8275713;
         case GRAVE -> -4741377;
         case GHOST -> -9247022;
         case BURN, EXPLOSION -> -26040;
         case GORY_EXPLOSION -> -39302;
         case LIGHTNING -> -144;
         case FREEZE -> -9053697;
         case OPPONENT -> -8668417;
         case ANVIL -> -4601648;
         case GOLD -> -10916;
         case HEAVEN -> -3677;
         case AIR -> -4985601;
         case VOID -> -4748545;
         case ENDER -> -3831041;
         case TOTEM -> -10133;
         case DUST -> -1784942;
         case NONE -> -7364440;
         default -> -12396847;
      };
   }

   private String cardDescription(DeathAnimationConfig.Animation animation) {
      if (AnimationCatalog.contains(animation)) {
         return AnimationCatalog.recipe(animation).description();
      }

      return switch (animation) {
         case RAGDOLL -> "Quick scripted collapse";
         case PHYSICS_RAGDOLL -> "Connected joints and terrain physics";
         case MOMENTUM_RAGDOLL -> "Carry movement into the fall";
         case GRAVE -> "A headstone rises from the ground";
         case GHOST -> "Spirit rises from the body";
         case BURN -> "Char, then fall to pieces";
         case EXPLOSION -> "Turn white, swell, then detonate";
         case GORY_EXPLOSION -> "A chaotic burst";
         case LIGHTNING -> "Struck, then collapse";
         case FREEZE -> "Freeze into the ground";
         case OPPONENT -> "Transform into your opponent";
         case ANVIL -> "Squashed flat under an anvil";
         case GOLD -> "Become a gold statue";
         case HEAVEN -> "Rise peacefully upward";
         case AIR -> "Launch high into the sky";
         case VOID -> "Pulled down and consumed";
         case ENDER -> "Phase apart into the End";
         case TOTEM -> "Recall into a golden totem";
         case DUST -> "Crumble into drifting sand";
         case NONE -> "Skip the fall and tilt";
         default -> "Hover to preview";
      };
   }

   private void drawRounded(DrawContext g, int x, int y, int w, int h, int r, int color) {
      g.fill(x + r, y, x + w - r, y + h, color);
      g.fill(x, y + r, x + w, y + h - r, color);

      for (int i = 1; i < r; i++) {
         int inset = r - i;
         g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
         g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
      }
   }

   private void outline(DrawContext g, int x, int y, int w, int h, int color) {
      g.fill(x + 2, y, x + w - 2, y + 1, color);
      g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
      g.fill(x, y + 2, x + 1, y + h - 2, color);
      g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
      g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
      g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
   }

   private void gradient(DrawContext g, int x, int y, int w, int h, int top, int bottom) {
      if (h > 0 && w > 0) {
         int bands = Math.min(h, 20);

         for (int band = 0; band < bands; band++) {
            int y1 = y + band * h / bands;
            int y2 = y + (band + 1) * h / bands;
            float t = bands == 1 ? 0.0F : (float)band / (bands - 1);
            g.fill(x, y1, x + w, y2, this.blend(top, bottom, t));
         }
      }
   }

   private int blend(int a, int b, float t) {
      int aa = a >>> 24 & 0xFF;
      int ar = a >>> 16 & 0xFF;
      int ag = a >>> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24 & 0xFF;
      int br = b >>> 16 & 0xFF;
      int bg = b >>> 8 & 0xFF;
      int bb = b & 0xFF;
      return this.mix(aa, ba, t) << 24 | this.mix(ar, br, t) << 16 | this.mix(ag, bg, t) << 8 | this.mix(ab, bb, t);
   }

   private int mix(int a, int b, float t) {
      return Math.round(a + (b - a) * t);
   }

   private int shade(int color, float amount) {
      return this.blend(color, -16116451, amount);
   }

   private int screenX(int designX) {
      return (int)Math.round(this.uiOffsetX + designX * this.uiScale);
   }

   private int screenY(int designY) {
      return (int)Math.round(this.uiOffsetY + designY * this.uiScale);
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      DeathScene scene = DeathEffectController.hoverScene();
      if (this.tab == 0 && scene != null && scene.shellGame() != null && scene.shellGame().awaitingGuess()) {
         DeathAnimationsScreen.Card card = this.visibleCards.stream().filter(candidate -> candidate.key.equals(this.hoveredCard)).findFirst().orElse(null);
         if (card != null && click.buttonInfo().button() == 0) {
            double x = this.toUiX(click.x());
            double y = this.toUiY(click.y());
            if (x >= card.x + 8 && x < card.x + 68 && y >= card.y + 9 && y < card.y + 75) {
               int slot = Math.clamp((int)((x - (card.x + 8)) / 20.0), 0, 2);
               if (DeathEffectController.chooseShellGamePreview(slot)) {
                  return true;
               }
            }
         }
      }

      return super.mouseClicked(this.toUiEvent(click), doubled);
   }

   public boolean mouseReleased(Click click) {
      return super.mouseReleased(this.toUiEvent(click));
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      return super.mouseDragged(this.toUiEvent(click), offsetX / this.uiScale, offsetY / this.uiScale);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return super.mouseScrolled(this.toUiX(mouseX), this.toUiY(mouseY), horizontalAmount, verticalAmount);
   }

   private double toUiX(double x) {
      return (x - this.uiOffsetX) / this.uiScale;
   }

   private double toUiY(double y) {
      return (y - this.uiOffsetY) / this.uiScale;
   }

   private Click toUiEvent(Click e) {
      return new Click(this.toUiX(e.x()), this.toUiY(e.y()), e.buttonInfo());
   }

   public boolean shouldPause() {
      return false;
   }

   public void removed() {
      this.flushSliderChanges();
      this.clearHover();
      super.removed();
   }

   public void close() {
      this.flushSliderChanges();
      this.clearHover();
      this.waitingForKeybind = null;
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   public boolean keyPressed(KeyInput input) {
      if (this.waitingForKeybind == null) {
         if (input.key() == 256 && this.tab == 0 && this.drawer != 0) {
            this.openDrawer(0);
            return true;
         } else {
            return super.keyPressed(input);
         }
      } else if (input.key() == 256) {
         this.waitingForKeybind = null;
         this.clearAndInit();
         return true;
      } else {
         KeyBinding mapping = this.waitingForKeybind.equals("menu") ? DeathanimationClient.OPEN_MENU : DeathanimationClient.PREVIEW;
         mapping.setBoundKey(Type.KEYSYM.createFromCode(input.key()));
         KeyBinding.updateKeysByCode();
         MinecraftClient.getInstance().options.write();
         this.waitingForKeybind = null;
         this.clearAndInit();
         return true;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Card(String key, int x, int y, int width, int height, DeathAnimationConfig.Animation animation, boolean random, int accent) {
   }

   @Environment(EnvType.CLIENT)
   private final class FavouriteButton extends ButtonWidget {
      private final DeathAnimationConfig.Animation animation;
      private static final String[] STAR = new String[]{
         ".....#.....", "....###....", "....###....", "###########", ".#########.", "..#######..", "..#######..", ".####.####.", ".###...###.", "##.......##"
      };

      FavouriteButton(int x, int y, DeathAnimationConfig.Animation animation) {
         super(x, y, 18, 18, net.minecraft.text.Text.literal("Favourite " + animation.label), button -> {
            DeathAnimationConfig.INSTANCE.toggleFavourite(animation);
            DeathAnimationsScreen.this.resetBrowse();
         }, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
         this.animation = animation;
      }

      protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         boolean saved = DeathAnimationConfig.INSTANCE.favourites.contains(this.animation.name());
         int border = saved ? -10916 : (this.isSelected() ? -662605 : -6639433);
         int fill = saved ? -14275 : -15390670;

         for (int row = 0; row < STAR.length; row++) {
            for (int col = 0; col < STAR[row].length(); col++) {
               if (STAR[row].charAt(col) == '#') {
                  boolean edge = row == 0
                     || row == STAR.length - 1
                     || col == 0
                     || col == 10
                     || STAR[row - 1].charAt(col) != '#'
                     || STAR[row + 1].charAt(col) != '#'
                     || STAR[row].charAt(col - 1) != '#'
                     || STAR[row].charAt(col + 1) != '#';
                  context.fill(this.getX() + 3 + col, this.getY() + 4 + row, this.getX() + 4 + col, this.getY() + 5 + row, edge ? border : fill);
               }
            }
         }
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   private interface FlagGetter {
      boolean get();
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   private interface FlagSetter {
      void set(boolean var1);
   }

   @Environment(EnvType.CLIENT)
   private final class SettingSlider extends SliderWidget {
      private final String label;
      private final String unit;
      private final int minimum;
      private final int maximum;
      private final int accent;
      private final IntSupplier getter;
      private final IntConsumer setter;
      private boolean dirty;

      SettingSlider(int x, int y, String label, String unit, int minimum, int maximum, IntSupplier getter, IntConsumer setter, int accent) {
         super(x, y, 322, 52, net.minecraft.text.Text.empty(), (double)(Math.clamp(getter.getAsInt(), minimum, maximum) - minimum) / (maximum - minimum));
         this.label = label;
         this.unit = unit;
         this.minimum = minimum;
         this.maximum = maximum;
         this.getter = getter;
         this.setter = setter;
         this.accent = accent;
         this.updateMessage();
         this.setTooltip(Tooltip.of(net.minecraft.text.Text.literal("Drag or use Left/Right. " + minimum + "-" + maximum + unit + ".")));
      }

      private int selected() {
         return this.minimum + (int)Math.round(this.value * (this.maximum - this.minimum));
      }

      protected void updateMessage() {
         this.setMessage(net.minecraft.text.Text.literal(this.label + ": " + this.selected() + this.unit));
      }

      protected void applyValue() {
         int selected = this.selected();
         this.value = (double)(selected - this.minimum) / (this.maximum - this.minimum);
         if (this.getter.getAsInt() != selected) {
            this.setter.accept(selected);
            this.dirty = true;
         }
      }

      void saveIfChanged() {
         if (this.dirty) {
            DeathAnimationConfig.INSTANCE.save();
            this.dirty = false;
         }
      }

      public boolean mouseReleased(Click click) {
         boolean handled = super.mouseReleased(click);
         this.saveIfChanged();
         return handled;
      }

      public void setFocused(boolean focused) {
         if (!focused) {
            this.saveIfChanged();
         }

         super.setFocused(focused);
      }

      public boolean keyPressed(KeyInput input) {
         if (this.active && (input.key() == 263 || input.key() == 262 || input.key() == 268 || input.key() == 269)) {
            int next = input.key() == 268 ? this.minimum : (input.key() == 269 ? this.maximum : this.selected() + (input.key() == 263 ? -1 : 1));
            this.setValue((double)(Math.clamp(next, this.minimum, this.maximum) - this.minimum) / (this.maximum - this.minimum));
            this.saveIfChanged();
            return true;
         } else {
            return super.keyPressed(input);
         }
      }

      public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         int x = this.getX();
         int y = this.getY();
         int w = this.getWidth();
         boolean focused = this.isSelected();
         DeathAnimationsScreen.this.gradient(context, x, y, w, this.height, focused ? -14073012 : -14929093, -15654102);
         DeathAnimationsScreen.this.outline(context, x, y, w, this.height, focused ? this.accent : -13282468);
         context.drawTextWithShadow(DeathAnimationsScreen.this.textRenderer, net.minecraft.text.Text.literal(this.label), x + 12, y + 9, -1509900);
         String reading = this.selected() + this.unit;
         context.drawTextWithShadow(
            DeathAnimationsScreen.this.textRenderer,
            net.minecraft.text.Text.literal(reading),
            x + w - 12 - DeathAnimationsScreen.this.textRenderer.getWidth(reading),
            y + 9,
            this.accent
         );
         int thumbX = x + (int)Math.round(this.value * (w - 8));
         DeathAnimationsScreen.this.drawRounded(context, x + 4, y + 30, w - 8, 4, 2, -16247523);
         if (thumbX > x) {
            context.fill(x + 4, y + 30, thumbX + 4, y + 34, this.accent);
         }

         DeathAnimationsScreen.this.drawRounded(context, thumbX, y + 26, 8, 12, 2, focused ? -1509900 : this.accent);
         context.drawTextWithShadow(DeathAnimationsScreen.this.textRenderer, Integer.toString(this.minimum), x + 6, y + 41, -7164235);
         String end = Integer.toString(this.maximum);
         context.drawTextWithShadow(
            DeathAnimationsScreen.this.textRenderer, end, x + w - 6 - DeathAnimationsScreen.this.textRenderer.getWidth(end), y + 41, -7164235
         );
      }
   }

   @Environment(EnvType.CLIENT)
   private final class StyledButton extends ButtonWidget {
      private final int accent;
      private final DeathAnimationsScreen.FlagGetter selected;
      private final DeathAnimationsScreen.FlagGetter on;
      private final DeathAnimationsScreen.FlagSetter setter;

      private StyledButton(
         int x,
         int y,
         int width,
         int height,
         net.minecraft.text.Text message,
         PressAction action,
         int accent,
         DeathAnimationsScreen.FlagGetter selected,
         DeathAnimationsScreen.FlagGetter on,
         DeathAnimationsScreen.FlagSetter setter
      ) {
         super(x, y, width, height, message, action, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
         this.accent = accent;
         this.selected = selected;
         this.on = on;
         this.setter = setter;
      }

      protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         boolean hover = this.isSelected();
         int x = this.getX();
         int y = this.getY();
         int w = this.width;
         int h = this.height;
         if (!this.active) {
            DeathAnimationsScreen.this.gradient(context, x, y, w, h, -15457237, -15721437);
            DeathAnimationsScreen.this.outline(context, x, y, w, h, -14271676);
            context.drawCenteredTextWithShadow(DeathAnimationsScreen.this.textRenderer, this.getMessage(), x + w / 2, y + (h - 8) / 2, -10191225);
         } else {
            boolean isToggle = this.on != null;
            boolean enabled = isToggle && this.on.get();
            if (this.selected != null && this.selected.get()) {
               this.accentColor(context, x, y, w, h, this.accent);
            } else {
               DeathAnimationsScreen.this.gradient(context, x, y, w, h, hover ? -14073012 : -14929093, -15654102);
               DeathAnimationsScreen.this.outline(context, x, y, w, h, hover ? this.accent : -13282468);
               if (hover) {
                  context.fill(x + 2, y + 2, x + w - 2, y + 3, this.accent);
               }
            }

            if (isToggle) {
               DeathAnimationsScreen.this.drawRounded(context, x + 4, y + 4, w - 8, h - 8, 4, enabled ? -15322310 : -15720667);
               if (enabled) {
                  DeathAnimationsScreen.this.gradient(context, x + 6, y + 5, w - 12, h - 10, DeathAnimationsScreen.this.shade(this.accent, 0.18F), -15190725);
               }

               context.drawCenteredTextWithShadow(
                  DeathAnimationsScreen.this.textRenderer,
                  net.minecraft.text.Text.literal(DeathAnimationsScreen.this.onOff(enabled)),
                  x + w / 2,
                  y + (h - 8) / 2,
                  enabled ? -1509900 : -7164235
               );
            } else {
               context.drawCenteredTextWithShadow(
                  DeathAnimationsScreen.this.textRenderer,
                  (net.minecraft.text.Text)(isToggle ? net.minecraft.text.Text.literal(DeathAnimationsScreen.this.onOff(enabled)) : this.getMessage()),
                  x + w / 2,
                  y + (h - 8) / 2,
                  -1509900
               );
            }
         }
      }

      private void accentColor(DrawContext g, int x, int y, int w, int h, int color) {
         DeathAnimationsScreen.this.gradient(g, x, y, w, h, DeathAnimationsScreen.this.shade(color, 0.26F), -15256260);
         DeathAnimationsScreen.this.outline(g, x, y, w, h, color);
         g.fill(x + 4, y + 2, x + w - 4, y + 3, color);
      }
   }
}
