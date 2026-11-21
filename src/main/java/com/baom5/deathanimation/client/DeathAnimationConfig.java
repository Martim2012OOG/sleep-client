package com.baom5.deathanimation.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class DeathAnimationConfig {
   public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static final Path FILE = FabricLoader.getInstance()
      .getConfigDir()
      .resolve(ReleaseProfile.isPublic() ? "deathanimationplus-public.json" : "deathanimationplus.json");
   public static final int MIN_DURATION_SECONDS = 2;
   public static final int MAX_DURATION_SECONDS = 60;
   public static final int MIN_PERSISTENT_EFFECTS = 1;
   public static final int MAX_PERSISTENT_EFFECTS = 24;
   public static DeathAnimationConfig INSTANCE = load();
   public boolean enabled = true;
   public boolean deathDetectionDebug;
   public DeathVisibility deathVisibility = DeathVisibility.EVERYONE_NEARBY;
   public boolean randomMode;
   public boolean persistEffects;
   public boolean removePrevious = true;
   public boolean sounds = true;
   public boolean visualEffects = true;
   public boolean cinematicEffects;
   public boolean redDeathTint;
   public boolean physicsDebris = true;
   public boolean hideCorpseShadows = true;
   public float animationSpeed = 1.0F;
   public int durationSeconds = 4;
   public int maxPersistentEffects = 8;
   public int schemaVersion = 9;
   public List<String> favourites = new ArrayList<>();
   public List<String> enabledAnimations = new ArrayList<>(
      ReleaseProfile.animations().stream().filter(animation -> animation != DeathAnimationConfig.Animation.NONE).map(Enum::name).toList()
   );

   public static DeathAnimationConfig load() {
      try {
         Path input = FILE;
         if (ReleaseProfile.isPublic() && !Files.exists(input)) {
            input = FILE.resolveSibling("deathanimationplus.json");
         }

         if (Files.exists(input)) {
            try (Reader reader = Files.newBufferedReader(input)) {
               JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
               DeathAnimationConfig loaded = (DeathAnimationConfig)GSON.fromJson(json, DeathAnimationConfig.class);
               if (loaded != null) {
                  if (!json.has("schemaVersion")) {
                     loaded.schemaVersion = 0;
                  }

                  if (!json.has("enabledAnimations") || loaded.enabledAnimations == null) {
                     loaded.enabledAnimations = new ArrayList<>();
                  }

                  if (loaded.favourites == null) {
                     loaded.favourites = new ArrayList<>();
                  }

                  List<String> previousNames = new ArrayList<>(loaded.enabledAnimations);
                  List<String> cleanFavourites = SelectionMigration.favourites(loaded.favourites);
                  List<String> cleanNames = SelectionMigration.enabled(previousNames);
                  boolean cleaned = !cleanNames.equals(loaded.enabledAnimations) || !cleanFavourites.equals(loaded.favourites);
                  if (loaded.deathVisibility == null) {
                     loaded.deathVisibility = DeathVisibility.EVERYONE_NEARBY;
                     cleaned = true;
                  }

                  loaded.favourites = new ArrayList<>(cleanFavourites);
                  loaded.enabledAnimations = new ArrayList<>(cleanNames);
                  if (loaded.enabledAnimations.isEmpty() && !previousNames.isEmpty()) {
                     loaded.enabledAnimations.add(retainedFallback(previousNames.getFirst()).name());
                     cleaned = true;
                  }

                  if (loaded.schemaVersion < 5) {
                     loaded.animationSpeed = 1.0F;
                     loaded.physicsDebris = true;
                     loaded.hideCorpseShadows = true;
                     cleaned = true;
                  }

                  loaded.durationSeconds = Math.clamp(loaded.durationSeconds, 2, 60);
                  loaded.maxPersistentEffects = Math.clamp(loaded.maxPersistentEffects, 1, 24);
                  if (!Float.isFinite(loaded.animationSpeed)) {
                     loaded.animationSpeed = 1.0F;
                  }

                  loaded.animationSpeed = Math.clamp(loaded.animationSpeed, 0.5F, 2.0F);
                  if (cleaned || loaded.schemaVersion < 9 || !json.has("schemaVersion")) {
                     loaded.schemaVersion = 9;
                     loaded.save();
                  }

                  return loaded;
               }
            }
         }
      } catch (Exception exception) {
         System.err.println("[Death Animations +] Could not load config: " + exception.getMessage());
      }

      return new DeathAnimationConfig();
   }

   private static DeathAnimationConfig.Animation retainedFallback(String retiredName) {
      try (InputStream stream = DeathAnimationConfig.class.getResourceAsStream("/assets/deathanimation/animations/retired.json")) {
         if (stream != null) {
            JsonObject retired = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (retired.has(retiredName)) {
               DeathAnimationConfig.Animation fallback = DeathAnimationConfig.Animation.valueOf(retired.get(retiredName).getAsString());
               if (ReleaseProfile.allows(fallback)) {
                  return fallback;
               }
            }
         }
      } catch (Exception exception) {
         System.err.println("[Death Animations +] Could not resolve retired selection: " + retiredName);
      }

      return DeathAnimationConfig.Animation.RAGDOLL;
   }

   public void save() {
      try {
         Files.createDirectories(FILE.getParent());

         try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, writer);
         }
      } catch (Exception exception) {
         System.err.println("[Death Animations +] Could not save config: " + exception.getMessage());
      }
   }

   public static void resetToDefaults() {
      INSTANCE = new DeathAnimationConfig();
      INSTANCE.save();
   }

   public boolean isEnabled(DeathAnimationConfig.Animation animation) {
      return ReleaseProfile.allows(animation) && this.enabledAnimations.contains(animation.name());
   }

   public void toggleFavourite(DeathAnimationConfig.Animation animation) {
      if (ReleaseProfile.allows(animation)) {
         if (!this.favourites.remove(animation.name())) {
            this.favourites.add(animation.name());
         }

         this.save();
      }
   }

   public void useForRandom(List<DeathAnimationConfig.Animation> animations) {
      List<String> names = CatalogBrowser.randomPool(animations);
      if (!names.isEmpty()) {
         this.enabledAnimations = new ArrayList<>(names);
         this.randomMode = true;
         this.save();
      }
   }

   public void setEnabled(DeathAnimationConfig.Animation animation, boolean enabled) {
      if (ReleaseProfile.allows(animation)) {
         if (animation == DeathAnimationConfig.Animation.NONE && enabled) {
            this.enabledAnimations.clear();
            this.enabledAnimations.add(animation.name());
            this.save();
         } else {
            if (animation != DeathAnimationConfig.Animation.NONE) {
               this.enabledAnimations.remove(DeathAnimationConfig.Animation.NONE.name());
            }

            this.enabledAnimations.remove(animation.name());
            if (enabled) {
               this.enabledAnimations.add(animation.name());
            }

            this.save();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public enum Animation {
      RAGDOLL("Originals: Scripted ragdoll"),
      PHYSICS_RAGDOLL("Originals: Physics ragdoll"),
      MOMENTUM_RAGDOLL("Originals: Momentum ragdoll"),
      GRAVE("Originals: Grave spawning"),
      GHOST("Originals: Ghost"),
      BURN("Originals: Burn to death"),
      EXPLOSION("Originals: Whiteout explosion"),
      GORY_EXPLOSION("Originals: Gory explosion"),
      LIGHTNING("Originals: Lightning strike"),
      FREEZE("Originals: Freeze death"),
      OPPONENT("Originals: Turn into opponent"),
      ANVIL("Originals: Crushed by anvil"),
      GOLD("Originals: Turn into gold"),
      HEAVEN("Originals: Heaven V1"),
      AIR("Originals: Skyrocket"),
      VOID("Originals: Consumed by the void"),
      ENDER("Originals: Ender Split"),
      TOTEM("Originals: Totem Recall"),
      DUST("Originals: Dustfall"),
      BACKFLIP("Originals: Last Backflip"),
      CROSS_LEGGED("Originals: Final Meditation"),
      STRETCH_SNAP("Originals: Rubber Band"),
      BALLOON("Originals: Balloon Farewell"),
      TORNADO("Originals: Twister"),
      BAN_HAMMER("Originals: Ban Hammer"),
      WHIRLPOOL("Originals: Whirlpool"),
      FLOWER_BLOOM("Originals: Final Bloom"),
      STONE_PRESS("Originals: Between Stones"),
      CARD_TRICK("Originals: Card Trick"),
      STATIC("Originals: Signal Lost"),
      MUSIC_BOX("Originals: Music Box"),
      SCENE_PIPE_EXIT("Pipe Exit"),
      SCENE_SUBSTITUTION_LOG("Substitution Log"),
      BATCH01_PAC_CHOMP("PAC-MAN: Last Bite"),
      BATCH01_SOUL_BREAK("UNDERTALE: Broken SOUL"),
      BATCH01_KI_RECOIL("Dragon Ball: Goku's Kamehameha"),
      BATCH01_FREDDY_SHUTDOWN("FNAF: Power Out"),
      BATCH01_AIRLOCK_VERDICT("Among Us: Ejected"),
      BATCH01_DRONE_ELIMINATION("Fortnite: Elimination Drone"),
      BATCH01_WASTED_FRAME("GTA: WASTED"),
      BATCH01_BULLET_BILL("Super Mario: Bullet Bill"),
      BATCH01_CURSED_TAPE("The Ring: Cursed Tape"),
      BATCH01_DOODLE_ERASER("SpongeBob: Doodle Eraser"),
      BATCH01_BANK_SHOT("8 Ball Pool: Bank Shot"),
      BATCH01_BOWLING_SPLIT("Originals: Bowling Split"),
      SCENE_MARIO_STOMP("Super Mario: Head Stomp"),
      FULL_0057_SHADOW_CLONE_COMBO("Naruto: Shadow Clone Combo"),
      FULL_0058_RASENGAN("Naruto: Rasengan"),
      FULL_0059_UNLIMITED_VOID("Jujutsu Kaisen: Unlimited Void"),
      FULL_0060_GUM_GUM_BAZOOKA("One Piece: Gum-Gum Bazooka"),
      FULL_0061_GUM_GUM_GATLING("One Piece: Gum-Gum Gatling"),
      FULL_0062_STAR_PLATINUM_S_ORA_ORA_BARRAGE("JoJo: Star Platinum's Ora Ora Barrage"),
      FULL_0064_DIO_S_THE_WORLD_TIME_STOP("JoJo: DIO's The World Time Stop"),
      FULL_0065_DIO_S_KNIFE_VOLLEY("JoJo: DIO's Knife Volley"),
      FULL_0067_PIKACHU_THUNDERBOLT("Pokémon: Pikachu Thunderbolt"),
      FULL_0072_ACME_SPRING_GLOVE("Looney Tunes: ACME Spring Glove"),
      FULL_0073_VELOCIRAPTOR_POUNCE("Jurassic Park: Velociraptor Pounce"),
      FULL_0074_MJOLNIR_KNOCKBACK("The Avengers: Mjolnir Knockback"),
      FULL_0077_SHY_GUY_CHARGE("SCP-096: Shy Guy Charge"),
      FULL_0082_SPIRIT_BOMB("Dragon Ball: Spirit Bomb"),
      FULL_0083_VEGETA_S_FINAL_FLASH("Dragon Ball: Vegeta's Final Flash"),
      FULL_0084_RASENSHURIKEN("Naruto: Rasenshuriken"),
      FULL_0085_KAKASHI_S_LIGHTNING_BLADE("Naruto: Kakashi's Lightning Blade"),
      FULL_0087_ZORO_S_THREE_SWORD_ONIGIRI("One Piece: Zoro's Three-Sword Onigiri"),
      FULL_0089_MALEVOLENT_SHRINE("Jujutsu Kaisen: Malevolent Shrine"),
      FULL_0090_HOLLOW_PURPLE("Jujutsu Kaisen: Hollow Purple"),
      FULL_0091_TODO_S_BOOGIE_WOOGIE("Jujutsu Kaisen: Todo's Boogie Woogie"),
      FULL_0093_KILLUA_S_GODSPEED("Hunter x Hunter: Killua's Godspeed"),
      FULL_0094_ICHIGO_S_GETSUGA_TENSHO("Bleach: Ichigo's Getsuga Tensho"),
      FULL_0096_TANJIRO_S_HINOKAMI_KAGURA("Demon Slayer: Sun Breathing — Dance"),
      FULL_0097_ZENITSU_S_THUNDERCLAP_AND_FLASH("Demon Slayer: Zenitsu's Thunderclap and Flash"),
      FULL_0099_ODM_GEAR_SWING_STRIKE("Attack on Titan: Levi ODM Gear Swing Strike"),
      FULL_0100_ARMORED_TITAN_CHARGE("Attack on Titan: Armored Titan Charge"),
      FULL_0102_BAKUGO_S_GAUNTLET_BLAST("My Hero Academia: Bakugo's Gauntlet Blast"),
      FULL_0105_DENJI_S_STARTER_CORD("Chainsaw Man: Denji's Starter Cord"),
      FULL_0107_KILLER_QUEEN_S_FIRST_BOMB("JoJo: Killer Queen's First Bomb"),
      FULL_0113_SPIN_DASH("Sonic: Spin Dash"),
      FULL_0118_INHALE_AND_STAR_SPIT("Kirby: Inhale and Star Spit"),
      FULL_0122_WASP_NEST_SWARM("Animal Crossing: Wasp Nest Swarm"),
      FULL_0125_WELL_OF_BLOCKS("Tetris: Well of Blocks"),
      FULL_0126_CHARIZARD_FLAMETHROWER("Pokémon: Charizard Flamethrower"),
      FULL_0127_CLASSIC_OOF("Roblox: Classic Oof"),
      FULL_0128_HUGGY_WUGGY_VENT_GRAB("Poppy Playtime: Huggy Wuggy Vent Grab"),
      FULL_0130_RYU_HADOUKEN("Street Fighter: Ryu Hadouken"),
      FULL_0132_SCORPION_S_SPEAR("Mortal Kombat: Scorpion's Spear"),
      FULL_0134_THE_STORM_CLOSES_IN("Fortnite: The Storm Closes In"),
      FULL_0137_FOXY_SPRINT("Five Nights at Freddy's: Foxy Sprint Attack"),
      FULL_0142_THWOMP_CRUSH("Super Mario: Thwomp Crush"),
      FULL_0143_RING_SCATTER_HIT("Sonic: Ring Scatter Hit"),
      FULL_0150_TOPH_S_EARTH_COFFIN("Avatar: Toph's Earth Coffin"),
      FULL_0154_DALEK_EXTERMINATION_BEAM("Doctor Who: Dalek Extermination Beam"),
      FULL_0158_DEMOGORGON_GATE("Stranger Things: Demogorgon Gate"),
      FULL_0159_ELEVEN_S_TELEKINETIC_TOSS("Stranger Things: Eleven's Telekinetic Toss"),
      FULL_0162_BILL_CIPHER_DEAL("Gravity Falls: Bill Cipher Deal"),
      FULL_0166_RED_LIGHT_GREEN_LIGHT("Squid Game: Red Light, Green Light"),
      FULL_0175_PORTAL_GUN_MISHAP("Rick and Morty: Portal Gun Mishap"),
      FULL_0187_STAMPEDE_DODGE("The Lion King: Stampede Dodge"),
      FULL_0193_DARTH_VADER_FORCE_CHOKE("Star Wars: Darth Vader Force Choke"),
      FULL_0195_EMPEROR_S_FORCE_LIGHTNING("Star Wars: Emperor's Force Lightning"),
      FULL_0200_HULK_CLAP("The Avengers: Hulk Clap"),
      FULL_0201_THANOS_SNAP("The Avengers: Thanos Snap"),
      FULL_0202_T_REX_FOOTSTEP("Jurassic Park: T-Rex Footstep"),
      FULL_0204_BULLET_TIME_DODGE("The Matrix: Bullet-Time Dodge"),
      FULL_0206_CLOAKED_HUNTER_WRIST_BLADE("Predator: Cloaked Hunter Wrist-Blade"),
      FULL_0207_SHARK_FIN_PASS("Jaws: Shark Fin Pass"),
      FULL_0209_ROLLING_BOULDER("Indiana Jones: Rolling Boulder"),
      FULL_0213_AVADA_KEDAVRA("Harry Potter: Avada Kedavra"),
      FULL_0216_DRAGON_S_ROAR("Shrek: Dragon's Roar"),
      FULL_0223_HEROBRINE_HALLWAY_GLIMPSE("Creepypasta: Herobrine Hallway Glimpse"),
      FULL_0229_FREDDY_S_GLOVE_CLAW("A Nightmare on Elm Street: Freddy's Glove Claw"),
      FULL_0232_CHUCKY_TOY_BOX_LUNGE("Child's Play: Chucky Toy Box Lunge"),
      FULL_0240_NEVER_DIES("Technoblade: Never Dies"),
      FULL_0241_TRYING_NOT_TO_LAUGH("IShowSpeed: Trying Not to Laugh"),
      FULL_0243_ZEUS_S_THUNDERBOLT("Greek Myth: Zeus's Thunderbolt"),
      FULL_0250_PENANCE_STARE("Ghost Rider: Penance Stare"),
      FULL_0251_HEAT_VISION_CUT("Superman: Heat Vision Cut"),
      FULL_0255_CLAW_SLASH_SPARK("Wolverine: Claw Slash Spark"),
      FULL_0257_SYMBIOTE_ABSORPTION("Venom: Symbiote Absorption"),
      FULL_0259_VIBRANIUM_SHIELD_BLOCK("Captain America: Vibranium Shield Block"),
      FULL_0277_HEART_ATTACK("Death Note: Heart Attack"),
      FULL_0282_EDGERUNNERS_SANDEVISTAN_GLITCH("Cyberpunk: Edgerunners Sandevistan Glitch"),
      FULL_0283_VEGETA_S_FINAL_EXPLOSION("Dragon Ball: Vegeta's Final Explosion"),
      FULL_0285_DIRECT_SHOT_REBOUND("Blue Lock: Direct Shot Rebound"),
      FULL_0286_TITAN_HAND_SNATCH("Attack on Titan: Titan Hand Snatch"),
      FULL_0287_BICYCLE_KICK_GOAL("Football: Bicycle-Kick Goal"),
      FULL_0289_KNOCKOUT_COUNT("Boxing: Knockout Count"),
      FULL_0292_TOMBSTONE_PILEDRIVER("WWE: Tombstone Piledriver"),
      FULL_0295_SERVE_ACE("Tennis: Serve Ace"),
      FULL_0297_HOLE_IN_ONE("Golf: Hole-in-One"),
      FULL_0299_KNIGHT_FORK("Chess: Knight Fork"),
      FULL_0301_HOTEL_CRUSH("Monopoly: Hotel Crush"),
      FULL_0303_REVERSE_CARD("UNO: Reverse Card"),
      FULL_0306_FOUR_IN_A_ROW("Connect Four: Four-in-a-Row"),
      FULL_0307_SNAKE_SLIDE("Snakes and Ladders: Snake Slide"),
      FULL_0313_DEER_STALK("99 Nights in the Forest (Horror & Internet Horror): Deer Stalk"),
      FULL_0314_BOX_FIGHT_BUILD_TRAP("Fortnite: Box-Fight Build Trap"),
      FULL_0317_CHAIN_CHOMP_SNAP("Super Mario: Chain Chomp Snap"),
      FULL_0321_ZOMBIE_BRAIN_SNACK("Plants vs. Zombies: Zombie Brain Snack"),
      FULL_0325_DEMON_MONKEY_CATCH("Temple Run: Demon Monkey Catch"),
      FULL_0326_FRUIT_SLICE_SWISH("Fruit Ninja: Fruit-Slice Swish"),
      FULL_0327_LASER_CORRIDOR("Jetpack Joyride: Laser Corridor"),
      FULL_0329_MEGA_KNIGHT_DROP("Clash Royale: Mega Knight Drop"),
      FULL_0333_OM_NOM_S_CANDY_DROP("Cut the Rope: Om Nom's Candy Drop"),
      FULL_0336_SPRINGBOARD_LAUNCH("Doodle Jump: Springboard Launch"),
      FULL_0337_HORDE_TAKEOVER("Zombie Tsunami: Horde Takeover"),
      FULL_0338_TOWER_TOPPLE("Stack: Tower Topple"),
      FULL_0339_COLOR_BOMB_CASCADE("Candy Crush: Color Bomb Cascade"),
      FULL_0340_KAKYOIN_S_EMERALD_SPLASH("JoJo: Kakyoin's Emerald Splash"),
      FULL_0351_NANAMI_S_RATIO_STRIKE("Jujutsu Kaisen: Nanami's Ratio Strike"),
      FULL_0357_THUNDER_SPEAR_PIN("Attack on Titan: Thunder Spear Pin"),
      FULL_0360_GENOS_S_INCINERATION("One-Punch Man: Genos's Incineration"),
      FULL_0370_SPY_BACKSTAB("Team Fortress 2: Spy Backstab"),
      FULL_0375_KILLCAM_QUICKSCOPE("Call of Duty: Killcam Quickscope"),
      FULL_0391_HOMELANDER_HEAT_VISION("The Boys: Homelander Heat Vision"),
      FULL_0399_WALKER_HORDE("The Walking Dead: Walker Horde"),
      FULL_0402_SPEED_FORCE_BLUR("The Flash: Speed Force Blur"),
      FULL_0405_CRANE_KICK("Cobra Kai: Crane Kick"),
      FULL_0411_DEMOBAT_SWARM_DIVE("Stranger Things: Demobat Swarm Dive"),
      FULL_0413_SALT_CIRCLE_TRAP("Supernatural: Salt Circle Trap"),
      FULL_0414_MEGAZORD_STOMP("Power Rangers: Megazord Stomp"),
      FULL_0416_DOOFENSHMIRTZ_S_REVERSE_INATOR("Phineas and Ferb: Doofenshmirtz's Reverse-Inator"),
      FULL_0419_SCROOGE_S_MONEY_BIN_DIVE("DuckTales: Scrooge's Money Bin Dive"),
      FULL_0424_SHRINK_RAY_MALFUNCTION("Dexter's Laboratory: Shrink-Ray Malfunction"),
      FULL_0428_BEAST_BOY_RHINO_ROULETTE("Teen Titans: Beast Boy Rhino Roulette"),
      FULL_0435_RUBBER_HOSE_MALLET("The Mask: Rubber-Hose Mallet"),
      FULL_0442_STUDIO_EXIT("The Truman Show: Studio Exit"),
      FULL_0445_SAUCER_TRACTOR_BEAM("Independence Day: Saucer Tractor Beam"),
      FULL_0453_ATOMIC_BREATH_SWEEP("Godzilla: Atomic Breath Sweep"),
      FULL_0457_REPULSOR_LIFT_OFF("Iron Man: Repulsor Lift-Off"),
      FULL_0463_MINION_BANANA_STAMPEDE("Despicable Me: Minion Banana Stampede"),
      FULL_0473_PAINTED_EXIT_PRANK("Trollface: Painted Exit Prank"),
      FULL_0477_BANANA_DANCE("Peanut Butter Jelly Time: Banana Dance"),
      FULL_0487_PALLBEARER_CARRY_OFF("Coffin Dance: Pallbearer Carry-Off"),
      FULL_0488_WHAT_ARE_THOSE_SHOE_STRETCH("Vine: What Are Those? Shoe Stretch"),
      FULL_0492_MOONLIT_CHANGE("The Wolf Man: Moonlit Change"),
      FULL_0493_SARCOPHAGUS_WRAP("The Mummy: Sarcophagus Wrap"),
      FULL_0515_PANENKA_PENALTY_CHIP("Soccer: Panenka Penalty Chip"),
      FULL_0516_BUZZER_BEATER_RIM_ROLL("Basketball: Buzzer-Beater Rim Roll"),
      FULL_0540_DOUBLE_JUMP_SWEEP("Checkers: Double-Jump Sweep"),
      FULL_0549_LIVING_SKETCH("Pictionary: Living Sketch"),
      FULL_0553_UNSOLVED_TWIST("Rubik's Cube: Unsolved Twist"),
      FULL_0554_LOOP_THE_LOOP_LAUNCH("Hot Wheels: Loop-the-Loop Launch"),
      FULL_0555_MINIFIGURE_BRICK_BUILD("LEGO: Minifigure Brick Build"),
      FULL_0564_CHAIN_REACTION_CAGE("Mouse Trap: Chain-Reaction Cage"),
      FULL_0566_BASE_RAID("Steal a Brainrot: Base Raid"),
      FULL_0567_BOARDLOCK_COMBO("Block Blast: Boardlock Combo"),
      FULL_0570_BATTLE_BREAK("Friday Night Funkin': Blue-Ball Game Over"),
      FULL_0580_HEADBUTT_RAG_DOLL("Goat Simulator: Headbutt Rag-Doll"),
      FULL_0590_RED_LINE_CIRCLE_ELIMINATION("MrBeast: Red-Line Circle Elimination"),
      FULL_0593_FLUSH_PORTAL_CHASE("Skibidi Toilet: Flush Portal Chase"),
      FULL_0615_POSEIDON_S_TRIDENT_WAVE("Greek Myth: Poseidon's Trident Wave"),
      FULL_0621_MINOTAUR_S_LABYRINTH_CHARGE("Greek Myth: Minotaur's Labyrinth Charge"),
      FULL_0635_SUN_WUKONG_S_EXTENDING_STAFF("Chinese Myth: Sun Wukong's Extending Staff"),
      FULL_0676_COSMIC_BOARD_WAKE("Silver Surfer: Cosmic Board Wake"),
      FULL_0717_YORIKIRI_RING_OUT("Sumo: Yorikiri Ring-Out"),
      FULL_0719_NINE_DART_FINISH("Darts: Nine-Dart Finish"),
      FULL_0725_SKYHOOK("NBA (Kareem Abdul-Jabbar): Skyhook"),
      FULL_0744_MALENIA_S_WATERFOWL_DANCE("Elden Ring: Malenia's Waterfowl Dance"),
      FULL_0756_REAPER_LEVIATHAN_LUNGE("Subnautica (Horror & Internet Horror): Reaper Leviathan Lunge"),
      FULL_0785_SCHOOL_HALLWAY_CHASE("Roblox Piggy (Horror & Internet Horror): School Hallway Chase"),
      FULL_0786_CLICKER_CORNER_LUNGE("The Last of Us (Horror & Internet Horror): Clicker Corner Lunge"),
      FULL_0787_BOOMER_BILE_SPLASH("Left 4 Dead: Boomer Burst"),
      FULL_0792_CRANE_KICK_FINISH("The Karate Kid: Crane Kick Finish"),
      FULL_0793_WICKED_WITCH_WATER_MELT("The Wizard of Oz: Wicked Witch Water Melt"),
      FULL_0795_SANDWORM_SWALLOW("Beetlejuice: Sandworm Swallow"),
      FULL_0816_PYRAMID_HEAD_GREAT_KNIFE_DRAG("Silent Hill 2: Pyramid Head Great Knife Drag"),
      FULL_0865_LEVIATHAN_AXE_RECALL("God of War: Leviathan Axe Recall"),
      FULL_0868_ENERGY_SWORD_LUNGE("Halo: Energy Sword Lunge"),
      FULL_0872_SHORYUKEN("Street Fighter: Shoryuken"),
      FULL_0873_DEMOLITION("Rocket League: Demolition"),
      FULL_0880_MINE_REVEAL("Minesweeper: Mine Reveal"),
      FULL_0899_DOMINO_BRIDGE_COLLAPSE("Originals: Domino Bridge Collapse"),
      FULL_0905_CLAW_MACHINE_PRIZE_DROP("Originals: Claw Machine Prize Drop"),
      FULL_0920_MOLTEN_STEEL_FAREWELL("Terminator 2: Molten Steel Farewell"),
      FULL_0928_FACEHUGGER_LATCH("Alien (Horror & Internet Horror): Facehugger Latch"),
      FULL_0965_TUG_OF_WAR_DROP("Squid Game: Tug-of-War Drop"),
      FULL_0970_BIG_BALLS_COURSE("Wipeout: Big Balls Course"),
      FULL_0973_A_TRAIN_SPEED_BURST("The Boys: A-Train Speed Burst"),
      FULL_0998_P_E_K_K_A_BLADE_FINISH("Clash of Clans: P.E.K.K.A. Blade Finish"),
      FULL_1002_RAIDEN_S_TORPEDO("Mortal Kombat: Raiden's Torpedo"),
      FULL_1015_SONIC_RAINBOOM("My Little Pony: Sonic Rainboom"),
      FULL_1016_GEM_POOF("Steven Universe: Gem Poof"),
      FULL_1018_GHOSTLY_WAIL("Danny Phantom: Ghostly Wail"),
      FULL_1019_RAVEN_S_SOUL_SELF_SNARE("Teen Titans: Raven's Soul-Self Snare"),
      FULL_1023_GRIM_S_SCYTHE_SWEEP("The Grim Adventures of Billy & Mandy: Grim's Scythe Sweep"),
      FULL_1041_GUS_FRING_S_FACE_OFF("Breaking Bad: Gus Fring's Face-Off"),
      FULL_1042_BOX_CUTTER_THREAT("Breaking Bad: Box Cutter Threat"),
      FULL_1048_RUSSIAN_ROULETTE("Squid Game: Russian Roulette"),
      FULL_1051_NEGAN_S_LUCILLE_STRIKE("The Walking Dead: Negan's Lucille Strike"),
      FULL_1065_SUB_ZERO_ICE_FREEZE("Mortal Kombat: Sub-Zero Ice Freeze"),
      FULL_1075_CAPTAIN_FALCON_S_FALCON_PUNCH("Super Smash Bros.: Captain Falcon's Falcon Punch"),
      FULL_1077_GAARA_S_SAND_BURIAL("Naruto: Gaara's Sand Burial"),
      FULL_1078_PAIN_S_SHINRA_TENSEI("Naruto: Pain's Shinra Tensei"),
      FULL_1080_SHIGARAKI_S_DECAY("My Hero Academia: Shigaraki's Decay"),
      FULL_1083_NETERO_S_100_TYPE_GUANYIN_BODHISATTVA("Hunter x Hunter: Netero's 100-Type Guanyin Bodhisattva"),
      FULL_1085_ZERO_REQUIEM("Code Geass: Zero Requiem"),
      FULL_1086_LIGHT_S_FINAL_COLLAPSE("Death Note: Light's Final Collapse"),
      FULL_1089_EMPIRE_STATE_FALL("King Kong: Empire State Fall"),
      FULL_1092_SHARK_CAGE_BREACH("Jaws: Shark Cage Breach"),
      FULL_1093_SOUND_HUNTING_LUNGE("A Quiet Place: Sound-Hunting Lunge"),
      FULL_1094_SAMARA_S_TV_CRAWL("The Ring: Samara's TV Crawl"),
      FULL_1096_GHOST_PINCH("Pac-Man: Ghost Pinch"),
      FULL_1106_HEX_A_GONE_DROP("Fall Guys: Hex-A-Gone Drop"),
      FULL_1111_MIMIC_CHEST_GRAB("Elden Ring: Mimic Chest Grab"),
      FULL_1129_PYM_TECH_ARROW_PIN("Hawkeye: Pym-Tech Arrow Pin"),
      FULL_1130_WIDOW_S_BITE("Black Widow: Widow's Bite"),
      FULL_1137_SOUL_SELF_ENCLOSURE("Raven: Soul-Self Enclosure"),
      FULL_1171_SPRING_LOADED_SURPRISE("Jack-in-the-Box: Spring-Loaded Surprise"),
      FULL_1176_FOLDING_ROOM_TRAP("Barbie Dreamhouse: Folding Room Trap"),
      FULL_1180_BELL_RING_LAUNCH("High Striker: Bell-Ring Launch"),
      FULL_1188_STAMPEDE_BOARD_GAME_BREAKOUT("Jumanji: Stampede Board-Game Breakout"),
      FULL_1205_CENTER_LINE_SNAP("Tug of War: Center-Line Snap"),
      FULL_1211_TABLE_CRACK("Arm Wrestling: Table Crack"),
      FULL_1229_ACHILLES_HEEL_ARROW("Greek Myth: Achilles' Heel Arrow"),
      FULL_1250_CELL_ABSORPTION("Agar.io: Cell Absorption"),
      FULL_1271_EAGLE_TIMEOUT_SNATCH("Crossy Road: Eagle Timeout Snatch"),
      FULL_1274_POTATO_MINE_POP("Plants vs. Zombies: Potato Mine Pop"),
      FULL_1276_CHUN_LI_S_SPINNING_BIRD_KICK("Street Fighter: Chun-Li's Spinning Bird Kick"),
      FULL_1321_PAPER_AIRPLANE_FOLD("Originals: Paper Airplane Fold"),
      FULL_1322_PHOTOCOPIER_TONER_TRANSFER("Originals: Photocopier Mass Production"),
      FULL_1323_WASHING_MACHINE_SPIN_CYCLE("Originals: Washing Machine Spin Cycle"),
      FULL_1331_PAPER_SHREDDER_STRIP("Originals: Paper Shredder Strip"),
      FULL_1338_PLAYER_DEFLATE("Originals: Player Deflate"),
      FULL_1347_PARRY_RIPOSTE_POINT("Fencing: Parry-Riposte Point"),
      FULL_1348_BAILS_OFF_WICKET("Cricket: Bails-Off Wicket"),
      FULL_1355_EDGE_BALL_RALLY("Table Tennis: Edge-Ball Rally"),
      FULL_1374_CHEESE_TRIGGER_CHAIN("Mouse Trap: Cheese-Trigger Chain"),
      FULL_1378_FATAL_TYPO("Typewriter: Fatal Typo"),
      FULL_1398_MAHITO_S_IDLE_TRANSFIGURATION("Jujutsu Kaisen: Mahito's Idle Transfiguration"),
      FULL_1399_BYAKUYA_S_SENBONZAKURA("Bleach: Byakuya's Senbonzakura"),
      FULL_1471_PORTAL_GUN_WRONG_UNIVERSE("Rick and Morty: Portal Gun Wrong Universe"),
      FULL_1497_MRS_PUFF_BOATING_SCHOOL_CRASH("SpongeBob SquarePants: Mrs. Puff Boating School Crash"),
      FULL_1499_DIMENSIONAL_SCISSORS("Star vs. the Forces of Evil: Dimensional Scissors"),
      FULL_1501_BEAK_DRILL_DIVE("Woody Woodpecker: Beak-Drill Dive"),
      FULL_1502_SUNRISE_STONE_SLEEP("Gargoyles: Sunrise Stone Sleep"),
      FULL_1510_THE_LICH_S_EXTINCTION_WAVE("Adventure Time: The Lich's Extinction Wave"),
      FULL_1522_FLYING_DUTCHMAN_S_BOTTLE_CAPTURE("SpongeBob SquarePants: Flying Dutchman's Bottle Capture"),
      FULL_1523_SOKKA_S_BOOMERANG_RETURN("Avatar: Sokka's Boomerang Return"),
      FULL_1525_METALBENDING_CAGE("The Legend of Korra: Metalbending Cage"),
      FULL_1526_OMNI_MAN_TRAIN_WINDOW_RUSH("Invincible: Omni-Man Train Window Rush"),
      FULL_1527_TAZ_TORNADO_SPIN("Taz-Mania: Taz Tornado Spin"),
      FULL_1547_TRASH_CUBE_COMPACTOR("WALL-E: Trash Cube Compactor"),
      FULL_1548_CORE_MEMORY_DUMP("Inside Out: Core Memory Dump"),
      FULL_1561_ANGLERFISH_LURE_IN_THE_DARK("Finding Nemo: Anglerfish Lure in the Dark"),
      FULL_1562_MIRROR_DIMENSION_FOLD("Doctor Strange: Mirror Dimension Fold"),
      FULL_1574_SCRAT_S_ACORN_TECTONIC_CRACK("Ice Age: Scrat's Acorn Tectonic Crack"),
      FULL_1603_KINETIC_SUIT_RELEASE("Black Panther: Kinetic Suit Release"),
      FULL_1647_BICYCLE_KICK_GOAL("Soccer: Bicycle-Kick Goal"),
      FULL_1648_WALK_OFF_HOME_RUN("Baseball: Walk-Off Home Run"),
      FULL_1653_180_CHECKOUT("Darts: 180 Checkout"),
      FULL_1676_FINAL_CANDY_RAIN_CRACK("Piñata: Final Candy-Rain Crack"),
      FULL_1698_NOBARA_S_RESONANCE("Jujutsu Kaisen: Nobara's Resonance"),
      FULL_1701_GIGA_DRILL_BREAK("Gurren Lagann: Giga Drill Break"),
      FULL_1755_CLICKER_SOUND_LURE_POUNCE("The Last of Us: Clicker Sound-Lure Pounce"),
      FULL_1780_TILTED_LANCE_RING_OUT("Jousting: Tilted-Lance Ring-Out"),
      FULL_1784_BUNKER_FLAG_ELIMINATION("Paintball Speedball: Bunker-Flag Elimination"),
      FULL_1802_LAST_SWING("Newton's Cradle: Last Swing"),
      FULL_1821_OMEGA_BEAM_CURVING_HUNT("Darkseid: Omega Beam Curving Hunt"),
      FULL_1824_GLIDER_PUMPKIN_BOMB_DIVE("Green Goblin: Glider Pumpkin-Bomb Dive"),
      FULL_1827_LIVING_CLAY_MOLD("Clayface: Living Clay Mold"),
      FULL_2024_CAPTAIN_MARVEL_S_BOMB_SACRIFICE("Kingdom Come: Captain Marvel's Bomb Sacrifice"),
      PILOT_YAMCHA("Dragon Ball: Yamcha Death Pose"),
      PILOT_BLACK_FLASH("Jujutsu Kaisen: Black Flash"),
      PILOT_SERIOUS_PUNCH("One-Punch Man: Serious Punch"),
      PILOT_MAKIMA_BANG("Chainsaw Man: Bang"),
      PILOT_GEOMETRY_SPIKE("Geometry Dash: Spike Death"),
      PILOT_SUBWAY_TRAIN("Subway Surfers: Train Collision"),
      PILOT_ANGRY_BIRD("Angry Birds: Slingshot Launch"),
      PILOT_FLAPPY_PIPE("Flappy Bird: Pipe Collision"),
      PILOT_CROSSY_TRAFFIC("Crossy Road: Traffic Hop"),
      PILOT_RAKE_CHAIN("The Simpsons: Rake Chain"),
      PILOT_YOUNGHEE("Squid Game: Young-hee's Red Light"),
      PILOT_ACME_TUNNEL("Looney Tunes: Painted Tunnel"),
      PILOT_COYOTE_DELAY("Looney Tunes: Delayed Canyon Fall"),
      PILOT_PIANO_DROP("Tom and Jerry: Piano Drop"),
      PILOT_SPIDERVERSE("Spider-Verse: Dimensional Glitch"),
      PILOT_JOKER_GAS("Batman: Joker Laughing Gas"),
      PILOT_PENNYWISE("IT: Pennywise Red Balloon"),
      PILOT_FRANKENSTEIN_TABLE("Frankenstein: Lightning Table"),
      PILOT_NYAN_CAT("Nyan Cat: Rainbow Flight"),
      PILOT_CURSOR_CLOSE("Animator vs. Animation: Cursor Delete"),
      PILOT_ICARUS("Icarus: Wax-Wing Fall"),
      PILOT_DEADPOOL_PANEL("Deadpool: Comic Panel Cut"),
      PILOT_SHELL_GAME("Originals: Shell Game Vanish"),
      PILOT_NESTED_DOLLS("Originals: Nested Doll Close"),
      PILOT_CARPET_ROLL("Originals: Carpet Roll-Up"),
      PILOT_CALENDAR("Originals: Calendar Page Age-Out"),
      PILOT_RKO("WWE: Randy Orton RKO"),
      PILOT_HOME_RUN("Baseball: Home-Run Launch"),
      PILOT_PEOPLES_ELBOW("WWE: The Rock People's Elbow"),
      PILOT_SLAM_DUNK("Basketball: Victim Slam Dunk"),
      PILOT_UNO_DRAW_FOUR("UNO: Wild Draw Four"),
      PILOT_MONOPOLY_JAIL("Monopoly: Go to Jail"),
      PILOT_JENGA("Jenga: Tower Collapse"),
      PILOT_WHACK_MOLE("Whack-a-Mole: Last Hole"),
      PILOT_MUSICAL_CHAIRS("Musical Chairs: Last Seat"),
      CURATED_WATER_SURFACE_SLASH("Demon Slayer: Water Surface Slash"),
      CURATED_NINTH_FORM_RENGOKU("Demon Slayer: Ninth Form — Rengoku"),
      CURATED_JAJANKEN_ROCK("Hunter x Hunter: Jajanken — Rock"),
      CURATED_JAJANKEN_SCISSORS("Hunter x Hunter: Jajanken — Scissors"),
      CURATED_JAJANKEN_PAPER("Hunter x Hunter: Jajanken — Paper"),
      CURATED_KILLUA_THUNDERBOLT("Hunter x Hunter: Thunderbolt — Narukami"),
      CURATED_KILLUA_WHIRLWIND("Hunter x Hunter: Whirlwind"),
      CURATED_KILLUA_LIGHTNING_PALM("Hunter x Hunter: Lightning Palm"),
      CURATED_KILLUA_RHYTHM_ECHO("Hunter x Hunter: Rhythm Echo"),
      CURATED_KILLUA_HEART_SNATCH("Hunter x Hunter: Heart Snatch"),
      CURATED_NETERO_FIRST_HAND("Hunter x Hunter: First Hand"),
      CURATED_NETERO_ZERO_HAND("Hunter x Hunter: Zero Hand"),
      CURATED_ALCHEMY_DISINTEGRATION("Fullmetal Alchemist: Body Disintegration — Alchemy Disintegration"),
      CURATED_POKEBALL_CAPTURE("Pokémon: Poké Ball Capture"),
      CURATED_PVZ_PEASHOOTER("Plants vs. Zombies 1: Peashooter Pea Barrage"),
      CURATED_PVZ_CHOMPER("Plants vs. Zombies 1: Chomper Devour"),
      CURATED_PVZ_CHERRY_BOMB("Plants vs. Zombies 1: Cherry Bomb Blast"),
      CURATED_PVZ_SQUASH("Plants vs. Zombies 1: Squash Drop"),
      CURATED_PVZ_WALLNUT("Plants vs. Zombies 1: Wall-nut Bowling"),
      CURATED_PVZ_DOOM_SHROOM("Plants vs. Zombies 1: Doom-shroom Blast"),
      CURATED_PVZ_JALAPENO("Plants vs. Zombies 1: Jalapeno Lane Burn"),
      CURATED_PVZ_GARGANTUAR("Plants vs. Zombies 1: Gargantuar Smash"),
      CURATED_PVZ_LAWNMOWER("Plants vs. Zombies 1: Lawn Mower Runover"),
      CURATED_MGR_RAIDEN_PUNCH_BARRAGE("Metal Gear Rising: Raiden vs. Armstrong Punch Barrage"),
      CURATED_BENDY_INK_TRANSFORMATION("Bendy: Ink Transformation"),
      CURATED_ASSASSIN_HIDDEN_BLADE("Assassin's Creed: Hidden-Blade Kill"),
      CURATED_POWERPUFF_TRIO("The Powerpuff Girls: Trio Beatdown"),
      CURATED_BACKROOMS_MONSTER_ATTACK("The Backrooms: Monster Attack"),
      CURATED_GRANNY_BAT_ATTACK("Granny: Bat Attack"),
      CURATED_GHOSTFACE_ATTACK("Scream: Ghostface Knife Attack"),
      CURATED_SLENDER_REACH("Creepypasta: Slender Man Reach"),
      CURATED_JEFF_LUNGE("Creepypasta: Jeff the Killer Lunge"),
      CURATED_RESIDENT_EVIL_LASER_HALLWAY("Resident Evil: Laser Grid Hallway"),
      BATCH01_IMPOSTOR_KILL("Among Us: Impostor Kill"),
      NONE("No death animation");

      public final String label;

      Animation(String label) {
         this.label = label;
      }
   }
}
