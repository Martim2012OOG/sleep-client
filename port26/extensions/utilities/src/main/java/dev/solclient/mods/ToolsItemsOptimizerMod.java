package dev.solclient.mods;

import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Tools And Items Optimizer: instant visual feedback for pearls, wind charges, XP bottles, maces, totems and
 * food / potions. VISUAL ONLY: it never sends anything to the server and never changes how an item works, so it
 * cannot give an advantage (the real effect still arrives from the server as usual).
 */
public class ToolsItemsOptimizerMod extends Mod {

	public static ToolsItemsOptimizerMod instance;

	private final Option.Header h1 = header("Visual only - nothing is sent to the server");
	private final Option.Bool windCharge = bool("Wind Charge", true);
	private final Option.Bool pearl = bool("Ender Pearl", true);
	private final Option.Bool xpBottle = bool("XP Bottle", true);
	private final Option.Bool mace = bool("Mace Smash", true);
	private final Option.Bool totem = bool("Totem Pop", true);
	private final Option.Bool consumable = bool("Eating And Drinking", true);
	private final Option.Num intensity = num("Intensity", 2, 1, 4, 1, "x");

	private long lastUseTick = -1;
	private int lastTotems = -1;
	private boolean wasUsing;
	private net.minecraft.world.item.Item useItem;
	private int useCount;
	private InteractionHand useHand = InteractionHand.MAIN_HAND;

	public ToolsItemsOptimizerMod() {
		super("Tools And Items Optimizer", "tools_items_optimizer",
				"Instant visual feedback for pearls, wind charges, XP bottles, maces, totems and food. Visual only.", Category.VISUAL);
		instance = this;
	}

	private int k() {
		return intensity.i();
	}

	/** Right click with an item in hand (client side only). */
	public void onUse(Player player, InteractionHand hand) {
		if (mc.player == null || mc.level == null || player != mc.player) {
			return;
		}
		ItemStack s = player.getItemInHand(hand);
		if (s.isEmpty()) {
			return;
		}
		// the callback can fire once per hand: react only once per tick
		long now = mc.level.getGameTime();
		if (now == lastUseTick) {
			return;
		}
		if (player.getCooldowns().getCooldownPercent(s, 0F) > 0F) {
			return; // still cooling down: the item will not be used
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1F);
		double x = eye.x + look.x * 0.9, y = eye.y + look.y * 0.9 - 0.15, z = eye.z + look.z * 0.9;
		if (windCharge.on() && s.is(Items.WIND_CHARGE)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.CLOUD, x, y, z, 6 * k(), 0.18);
			Fx.burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2 * k(), 0.2);
		} else if (pearl.on() && s.is(Items.ENDER_PEARL)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.PORTAL, x, y, z, 10 * k(), 0.6);
		} else if (xpBottle.on() && s.is(Items.EXPERIENCE_BOTTLE)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.HAPPY_VILLAGER, x, y, z, 5 * k(), 0.3);
			Fx.burst(ParticleTypes.ENCHANT, x, y, z, 6 * k(), 0.5);
		}
	}

	/** You hit something: a mace smash shows its shockwave straight away. */
	public void onAttack(Entity e) {
		Player p = mc.player;
		if (!mace.on() || p == null || !(e instanceof LivingEntity) || !p.getMainHandItem().is(Items.MACE)) {
			return;
		}
		if (p.fallDistance > 1.5F && !p.onGround()) {
			double x = e.getX(), y = e.getY() + 0.1, z = e.getZ();
			Fx.ring(ParticleTypes.CLOUD, x, y, z, 0.5, 12 * k(), 0.25);
			Fx.ring(ParticleTypes.CLOUD, x, y, z, 1.1, 8 * k(), 0.15);
			Fx.burst(ParticleTypes.EXPLOSION, x, y + e.getBbHeight() / 2, z, 1, 0.1);
		}
	}

	@Override
	public void tick() {
		Player p = mc.player;
		if (p == null || mc.level == null) {
			lastTotems = -1;
			wasUsing = false;
			return;
		}

		// totem pop: one less totem in your hands right after taking damage
		int totems = 0;
		ItemStack m = p.getMainHandItem(), o = p.getOffhandItem();
		if (m.is(Items.TOTEM_OF_UNDYING)) {
			totems += m.getCount();
		}
		if (o.is(Items.TOTEM_OF_UNDYING)) {
			totems += o.getCount();
		}
		if (totem.on() && lastTotems > totems && p.hurtTime > 0 && p.isAlive()) {
			Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 25 * k(), 0.5);
		}
		lastTotems = totems;

		// food / potion finished: a small burst once the item is really consumed
		boolean using = p.isUsingItem();
		if (using) {
			ItemStack a = p.getUseItem();
			useItem = a.getItem();
			useCount = a.getCount();
			useHand = p.getUsedItemHand();
		} else if (wasUsing && consumable.on() && useItem != null) {
			ItemStack now = p.getItemInHand(useHand);
			if (!now.is(useItem) || now.getCount() < useCount) {
				Vec3 eye = p.getEyePosition();
				Vec3 look = p.getViewVector(1F);
				Fx.burst(ParticleTypes.HAPPY_VILLAGER, eye.x + look.x * 0.5, eye.y - 0.2, eye.z + look.z * 0.5, 3 * k(), 0.15);
			}
		}
		wasUsing = using;
	}
}
