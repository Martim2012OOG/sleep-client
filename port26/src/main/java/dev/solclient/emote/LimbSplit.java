package dev.solclient.emote;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Gives the player (and armour) model real elbows and knees, like Emotecraft's Bendy-Lib: every arm and leg is made of two
 * boxes, the upper half and a child part "lower" hanging at the joint (with the same skin pixels, nothing looks different while
 * the limb is straight). Sleeves and pants are split the same way. The emotes rotate "lower" (see EmoteRig); everything else keeps
 * using the limb as one part. If anything about the model is not as expected, the vanilla model is left untouched.
 */
public final class LimbSplit {

	/** Emotes use the classic six parts (like R6): no elbows or knees. */
	public static final boolean R6 = true;

	private LimbSplit() {
	}

	/** One limb of two halves: returns the upper part (the lower one is its child "lower"). */
	private static PartDefinition limb(PartDefinition root, String name, int u, int v, boolean mirror, float x, float y, float z, int w, int h, int d,
			CubeDeformation dil, float ox, float oy, float oz) {
		float half = h / 2F;
		CubeListBuilder up = CubeListBuilder.create().texOffs(u, v);
		if (mirror) {
			up = up.mirror();
		}
		PartDefinition part = root.addOrReplaceChild(name, up.addBox(x, y, z, w, half, d, dil), PartPose.offset(ox, oy, oz));
		lower(part, u, v, mirror, x, y, z, w, h, d, dil);
		return part;
	}

	/** The lower half: its box starts at the joint, its skin pixels continue where the upper half ends. */
	private static void lower(PartDefinition parent, int u, int v, boolean mirror, float x, float y, float z, int w, int h, int d, CubeDeformation dil) {
		float half = h / 2F;
		CubeListBuilder lo = CubeListBuilder.create().texOffs(u, v + (int) half);
		if (mirror) {
			lo = lo.mirror();
		}
		// the box's own bottom face would show side pixels of the skin, so the real end of the limb (hand / foot, region v..v+d of the
		// skin) is a flat cap a hair below it
		lo = lo.addBox(x, 0F, z, w, half, d, dil).texOffs(u, v).addBox(x, half + 0.01F, z, w, 0F, d, dil);
		parent.addOrReplaceChild("lower", lo, PartPose.offset(0F, y + half, 0F));
	}

	/** A sleeve / pants layer: a child of the limb, split in two as well. */
	private static void cover(PartDefinition limb, String name, int u, int v, float x, float y, float z, int w, int h, int d, CubeDeformation dil) {
		PartDefinition up = limb.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h / 2F, d, dil), PartPose.ZERO);
		lower(up, u, v, false, x, y, z, w, h, d, dil);
	}

	private interface Job {
		void run(PartDefinition root);
	}

	/** Runs the change; when it fails or the result can't be built, puts the old limbs back. */
	private static void guarded(MeshDefinition md, Job job) {
		if (R6) {
			return; // classic six-part body: arms and legs stay in one piece
		}
		PartDefinition root = md.getRoot();
		String[] names = {"left_arm", "right_arm", "left_leg", "right_leg"};
		PartDefinition[] old = new PartDefinition[4];
		for (int i = 0; i < 4; i++) {
			try {
				old[i] = root.getChild(names[i]);
			} catch (RuntimeException e) {
				return; // not the model we know
			}
		}
		try {
			job.run(root);
			LayerDefinition.create(md, 64, 64).bakeRoot(); // would the game be able to use it?
		} catch (RuntimeException | Error e) {
			System.err.println("[Caos] emote joints: keeping the plain model: " + e);
			for (int i = 0; i < 4; i++) {
				root.addOrReplaceChild(names[i], old[i]);
			}
		}
	}

	/** The base biped model (armour, zombies...): the two arms and two legs. */
	public static void biped(MeshDefinition md, CubeDeformation d, float off) {
		guarded(md, root -> {
			limb(root, "right_arm", 40, 16, false, -3, -2, -2, 4, 12, 4, d, -5F, 2F + off, 0F);
			limb(root, "left_arm", 40, 16, true, -1, -2, -2, 4, 12, 4, d, 5F, 2F + off, 0F);
			limb(root, "right_leg", 0, 16, false, -2, 0, -2, 4, 12, 4, d, -1.9F, 12F + off, 0F);
			limb(root, "left_leg", 0, 16, true, -2, 0, -2, 4, 12, 4, d, 1.9F, 12F + off, 0F);
		});
	}

	/** The player model (wide or slim arms) with its sleeves and pants. */
	public static void player(MeshDefinition md, CubeDeformation d, boolean slim) {
		guarded(md, root -> {
			CubeDeformation c = d.extend(0.25F);
			int w = slim ? 3 : 4;
			PartDefinition la = limb(root, "left_arm", 32, 48, false, -1, -2, -2, w, 12, 4, d, 5F, 2F, 0F);
			cover(la, "left_sleeve", 48, 48, -1, -2, -2, w, 12, 4, c);
			PartDefinition ra = limb(root, "right_arm", 40, 16, false, slim ? -2 : -3, -2, -2, w, 12, 4, d, -5F, 2F, 0F);
			cover(ra, "right_sleeve", 40, 32, slim ? -2 : -3, -2, -2, w, 12, 4, c);
			PartDefinition ll = limb(root, "left_leg", 16, 48, false, -2, 0, -2, 4, 12, 4, d, 1.9F, 12F, 0F);
			cover(ll, "left_pants", 0, 48, -2, 0, -2, 4, 12, 4, c);
			PartDefinition rl = limb(root, "right_leg", 0, 16, false, -2, 0, -2, 4, 12, 4, d, -1.9F, 12F, 0F);
			cover(rl, "right_pants", 0, 32, -2, 0, -2, 4, 12, 4, c);
		});
	}
}
