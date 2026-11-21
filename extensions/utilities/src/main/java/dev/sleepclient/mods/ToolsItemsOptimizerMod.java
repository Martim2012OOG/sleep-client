package dev.sleepclient.mods;

import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

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
	private net.minecraft.item.Item useItem;
	private int useCount;
	private Hand useHand = Hand.MAIN_HAND;

	public ToolsItemsOptimizerMod() {
		super("Tools And Items Optimizer", "tools_items_optimizer",
				"Instant visual feedback for pearls, wind charges, XP bottles, maces, totems and food. Visual only.", Category.VISUAL);
		instance = this;
	}

	private int k() {
		return intensity.i();
	}

	/** Right click with an item in hand (client side only). */
	public void onUse(PlayerEntity player, Hand hand) {
		if (mc.player == null || mc.world == null || player != mc.player) {
			return;
		}
		ItemStack s = player.getStackInHand(hand);
		if (s.isEmpty()) {
			return;
		}
		// the callback can fire once per hand: react only once per tick
		long now = mc.world.getTime();
		if (now == lastUseTick) {
			return;
		}
		if (player.getItemCooldownManager().getCooldownProgress(s, 0F) > 0F) {
			return; // still cooling down: the item will not be used
		}
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1F);
		double x = eye.x + look.x * 0.9, y = eye.y + look.y * 0.9 - 0.15, z = eye.z + look.z * 0.9;
		if (windCharge.on() && s.isOf(Items.WIND_CHARGE)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.CLOUD, x, y, z, 6 * k(), 0.18);
			Fx.burst(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2 * k(), 0.2);
		} else if (pearl.on() && s.isOf(Items.ENDER_PEARL)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.PORTAL, x, y, z, 10 * k(), 0.6);
		} else if (xpBottle.on() && s.isOf(Items.EXPERIENCE_BOTTLE)) {
			lastUseTick = now;
			Fx.burst(ParticleTypes.HAPPY_VILLAGER, x, y, z, 5 * k(), 0.3);
			Fx.burst(ParticleTypes.ENCHANT, x, y, z, 6 * k(), 0.5);
		}
	}

	/** You hit something: a mace smash shows its shockwave straight away. */
	public void onAttack(Entity e) {
		PlayerEntity p = mc.player;
		if (!mace.on() || p == null || !(e instanceof LivingEntity) || !p.getMainHandStack().isOf(Items.MACE)) {
			return;
		}
		if (p.fallDistance > 1.5F && !p.isOnGround()) {
			double x = e.getX(), y = e.getY() + 0.1, z = e.getZ();
			Fx.ring(ParticleTypes.CLOUD, x, y, z, 0.5, 12 * k(), 0.25);
			Fx.ring(ParticleTypes.CLOUD, x, y, z, 1.1, 8 * k(), 0.15);
			Fx.burst(ParticleTypes.EXPLOSION, x, y + e.getHeight() / 2, z, 1, 0.1);
		}
	}

	@Override
	public void tick() {
		PlayerEntity p = mc.player;
		if (p == null || mc.world == null) {
			lastTotems = -1;
			wasUsing = false;
			return;
		}

		// totem pop: one less totem in your hands right after taking damage
		int totems = 0;
		ItemStack m = p.getMainHandStack(), o = p.getOffHandStack();
		if (m.isOf(Items.TOTEM_OF_UNDYING)) {
			totems += m.getCount();
		}
		if (o.isOf(Items.TOTEM_OF_UNDYING)) {
			totems += o.getCount();
		}
		if (totem.on() && lastTotems > totems && p.hurtTime > 0 && p.isAlive()) {
			Fx.burst(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 25 * k(), 0.5);
		}
		lastTotems = totems;

		// food / potion finished: a small burst once the item is really consumed
		boolean using = p.isUsingItem();
		if (using) {
			ItemStack a = p.getActiveItem();
			useItem = a.getItem();
			useCount = a.getCount();
			useHand = p.getActiveHand();
		} else if (wasUsing && consumable.on() && useItem != null) {
			ItemStack now = p.getStackInHand(useHand);
			if (!now.isOf(useItem) || now.getCount() < useCount) {
				Vec3d eye = p.getEyePos();
				Vec3d look = p.getRotationVec(1F);
				Fx.burst(ParticleTypes.HAPPY_VILLAGER, eye.x + look.x * 0.5, eye.y - 0.2, eye.z + look.z * 0.5, 3 * k(), 0.15);
			}
		}
		wasUsing = using;
	}
}
