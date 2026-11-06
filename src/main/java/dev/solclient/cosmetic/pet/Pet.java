package dev.solclient.cosmetic.pet;

import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

/**
 * One pet that follows its owner around like a tamed mob. Client-side only: every client runs the same simple brain,
 * so everyone sees the pet do roughly the same thing (not frame-exact, nothing is sent over the network).
 * <p>
 * Brain: follows you, sits when you crouch or stand still, sleeps when you are away, wanders off to sniff dropped
 * items, growls at monsters, plays with other pets, chases its tail, gets the zoomies, jumps when you jump, comes to
 * help when you get hurt, loves being petted (right click) and fed (right click holding food). Flying pets circle
 * around you and small ones land on your shoulder. It teleports back to you when it gets lost.
 */
public final class Pet {

	enum Mode {
		FOLLOW, SIT, SLEEP, HAPPY, BOW, TAIL_CHASE, ZOOMIES, SNIFF, ALERT, PLAY, PERCH
	}

	final PetModel model;
	final int ownerId;
	final boolean mine;
	private final Random rnd;

	// ---- world state (blocks); prev* = last tick, for smooth drawing between ticks
	double x, y, z, prevX, prevY, prevZ;
	double vx, vy, vz;
	float yaw, prevYaw;
	double groundY;
	boolean onGround, inWater, perched;

	// ---- animation state
	float walkPhase, prevWalkPhase, walk, prevWalk;
	float sit, prevSit, lie, prevLie, bow, prevBow;
	float squash, prevSquash, stretch, prevStretch;
	float flapPhase, prevFlapPhase, fold, prevFold;
	float bank, prevBank, tilt, prevTilt;
	float wagPhase, prevWagPhase, wagSpeed = 0.3F;
	float spin, prevSpin;
	float wave, prevWave, scratch, prevScratch;
	float headYaw, prevHeadYaw, headPitch, prevHeadPitch, headRoll, prevHeadRoll;
	int age;

	// ---- brain
	Mode mode = Mode.FOLLOW;
	int modeTicks;
	private int lastAlertSound = -10_000;
	private int ownerStill, nextIdle = 80, ambientIn = 600, scanIn, hopCooldown, stuck, stepSound;
	private double tx, ty, tz; // where the brain wants to go
	private Entity interest; // what it looks at / walks to (item, monster, other pet's owner)
	private Pet friend;
	private double lookX, lookY, lookZ;
	private boolean hasLook;
	private float orbit;
	private double ownerLastX, ownerLastY, ownerLastZ;
	// natural wandering: a point it is exploring (and how long), and slow personal drifts so it never keeps the same spot
	private double wanderX, wanderY, wanderZ;
	private int wanderUntil, pauseUntil;
	private final float seedA, seedB;
	private boolean ownerWasOnGround = true;
	private int ownerHurtSeen;

	Pet(PetModel model, PlayerEntity owner, boolean mine) {
		this.model = model;
		this.ownerId = owner.getId();
		this.mine = mine;
		this.rnd = new Random(owner.getUuid().getLeastSignificantBits() ^ model.id.hashCode());
		this.orbit = rnd.nextFloat() * 6.283F;
		this.seedA = rnd.nextFloat() * 6.283F;
		this.seedB = rnd.nextFloat() * 6.283F;
		placeNear(owner, false);
	}

	boolean flies() {
		return model.kind == PetModel.Kind.FLY || model.kind == PetModel.Kind.FLOAT;
	}

	/** Small flyers can sit on your shoulder (bee, bat...). */
	boolean canPerch() {
		return model.kind == PetModel.Kind.FLY && model.lengthBlocks() <= 0.95F && model.heightBlocks() <= 0.6F;
	}

	// ================================================================ tick
	void tick(PlayerEntity owner, ClientWorld world, List<Pet> others) {
		prevX = x;
		prevY = y;
		prevZ = z;
		prevYaw = yaw;
		prevWalkPhase = walkPhase;
		prevWalk = walk;
		prevSit = sit;
		prevLie = lie;
		prevBow = bow;
		prevSquash = squash;
		prevStretch = stretch;
		prevFlapPhase = flapPhase;
		prevFold = fold;
		prevBank = bank;
		prevTilt = tilt;
		prevWagPhase = wagPhase;
		prevSpin = spin;
		prevWave = wave;
		prevScratch = scratch;
		prevHeadYaw = headYaw;
		prevHeadPitch = headPitch;
		prevHeadRoll = headRoll;
		age++;
		modeTicks++;

		watchOwner(owner, world);
		double dOwner = Math.sqrt(sq(x - owner.getX(), (y - owner.getY()) * 0.5, z - owner.getZ()));
		if (dOwner > (flies() ? 26 : 18) || Math.abs(y - owner.getY()) > 10 || stuck > 70) {
			placeNear(owner, true);
			return;
		}
		if (--scanIn <= 0) {
			scanIn = 15 + rnd.nextInt(10);
			scan(owner, world, others);
		}
		think(owner, dOwner);
		if (flies()) {
			fly(owner, world);
		} else {
			move(owner, world, dOwner);
		}
		animate(owner, world);
		sounds(world);
	}

	/** Notices what you do: standing still, crouching, jumping, getting hurt. */
	private void watchOwner(PlayerEntity owner, ClientWorld world) {
		// from the position (other players have no velocity on this client)
		double ms = sq(owner.getX() - ownerLastX, 0, owner.getZ() - ownerLastZ);
		boolean moved = ms > 0.0004 || Math.abs(owner.getY() - ownerLastY) > 0.05;
		ownerStill = moved ? 0 : ownerStill + 1;
		if (mode == Mode.SLEEP && moved) {
			setMode(Mode.FOLLOW); // wakes up when you move
		}
		// you jumped: ground pets that are close jump with you
		boolean og = owner.isOnGround();
		if (ownerWasOnGround && !og && owner.getY() > ownerLastY + 0.05 && !flies() && onGround
				&& sq(x - owner.getX(), 0, z - owner.getZ()) < 16 && mode != Mode.SLEEP) {
			vy = model.kind == PetModel.Kind.HOP ? 0.42 : 0.4;
		}
		ownerWasOnGround = og;
		ownerLastX = owner.getX();
		ownerLastY = owner.getY();
		ownerLastZ = owner.getZ();
		// you got hit: it rushes to you, looks around angrily
		if (owner.hurtTime > 0 && owner.hurtTime != ownerHurtSeen && owner.hurtTime >= 8) {
			ownerHurtSeen = owner.hurtTime;
			if (mode == Mode.SLEEP || mode == Mode.SIT || mode == Mode.PERCH) {
				setMode(Mode.FOLLOW);
			}
			if (onGround && !flies()) {
				vy = 0.45;
			}
			wagSpeed = 0.1F;
			sound(world, model.soundAmbient, 1.15F, 0.9F);
		}
		if (owner.hurtTime == 0) {
			ownerHurtSeen = 0;
		}
	}

	/** Every second or so: looks for dropped items, monsters and other pets close by. */
	private void scan(PlayerEntity owner, ClientWorld world, List<Pet> others) {
		if (mode == Mode.HAPPY || mode == Mode.SLEEP || mode == Mode.PERCH) {
			return;
		}
		Box around = owner.getBoundingBox().expand(9, 4, 9);
		// monsters: growl at the closest one
		HostileEntity mob = null;
		double best = 1e9;
		for (HostileEntity h : world.getEntitiesByClass(HostileEntity.class, around, e -> e.isAlive() && !e.isInvisible())) {
			double d = h.squaredDistanceTo(x, y, z);
			if (d < best) {
				best = d;
				mob = h;
			}
		}
		if (mob != null && best < 100) {
			if (mode != Mode.ALERT) {
				setMode(Mode.ALERT);
			}
			interest = mob;
			return;
		}
		if (mode == Mode.ALERT) {
			setMode(Mode.FOLLOW);
		}
		if (mode != Mode.FOLLOW || ownerStill < 10) {
			return;
		}
		// another pet close by and both are bored: go and play
		for (Pet o : others) {
			if (o != this && o.mode != Mode.SLEEP && sq(o.x - x, o.y - y, o.z - z) < 36 && rnd.nextInt(4) == 0) {
				friend = o;
				setMode(Mode.PLAY);
				return;
			}
		}
		// something dropped on the ground: go sniff it
		if (!flies() && rnd.nextInt(3) == 0) {
			for (ItemEntity it : world.getEntitiesByClass(ItemEntity.class, owner.getBoundingBox().expand(6, 2, 6), Entity::isAlive)) {
				interest = it;
				setMode(Mode.SNIFF);
				return;
			}
		}
	}

	private void setMode(Mode m) {
		if (mode == Mode.PERCH && m != Mode.PERCH) {
			perched = false;
			vy = 0.25; // takes off from your shoulder
		}
		mode = m;
		modeTicks = 0;
		if (m != Mode.SNIFF && m != Mode.ALERT) {
			interest = null;
		}
		if (m != Mode.PLAY) {
			friend = null;
		}
	}

	/** Picks what to do next and where to go (tx, ty, tz). */
	private void think(PlayerEntity owner, double dOwner) {
		float oy = owner.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double fx = -Math.sin(oy), fz = Math.cos(oy); // where you look
		double rx = -Math.cos(oy), rz = -Math.sin(oy); // your right
		// default: it trots after you in a spot that keeps drifting (never the same place twice); when you stop it goes exploring
		if (ownerStill < 25 && !flies()) {
			double a = Math.sin(age * 0.011 + seedA) * 1.5 + ((ownerId & 1) == 0 ? 0.6 : -0.6);
			double dd = 1.7 + 1.0 * Math.sin(age * 0.017 + seedB) + 0.5 * Math.sin(age * 0.041 + seedA);
			tx = owner.getX() - fx * dd * Math.cos(a) + rx * dd * Math.sin(a);
			tz = owner.getZ() - fz * dd * Math.cos(a) + rz * dd * Math.sin(a);
		} else if (!flies()) {
			if (age >= wanderUntil || sq(wanderX - x, 0, wanderZ - z) < 0.35) {
				if (age < pauseUntil) { // reached it: sniffs about for a moment
					wanderX = x;
					wanderZ = z;
				} else {
					double ang = rnd.nextDouble() * Math.PI * 2, r = 1.8 + rnd.nextDouble() * 3.8;
					wanderX = owner.getX() + Math.cos(ang) * r;
					wanderZ = owner.getZ() + Math.sin(ang) * r;
					wanderUntil = age + 70 + rnd.nextInt(150);
					pauseUntil = age + (rnd.nextInt(3) == 0 ? 0 : 30 + rnd.nextInt(90));
					if (rnd.nextInt(5) == 0) {
						lookX = wanderX;
						lookY = y + 0.3;
						lookZ = wanderZ; // looks at where it is going
					}
				}
			}
			tx = wanderX;
			tz = wanderZ;
		} else {
			double side = (ownerId & 1) == 0 ? 1.1 : -1.1;
			tx = owner.getX() + rx * side - fx * 1.3;
			tz = owner.getZ() + rz * side - fz * 1.3;
		}
		ty = owner.getY();
		hasLook = false;
		boolean near = dOwner < 3.2;
		switch (mode) {
			case HAPPY -> {
				tx = x;
				tz = z;
				look(owner.getX(), owner.getEyeY(), owner.getZ());
				if (modeTicks > 45) {
					setMode(Mode.FOLLOW);
				}
			}
			case SLEEP -> {
				tx = x;
				tz = z;
			}
			case SIT -> {
				tx = x;
				tz = z;
				look(owner.getX(), owner.getEyeY(), owner.getZ());
				if (!owner.isSneaking() && ownerStill < 60 || dOwner > 4.5) {
					setMode(Mode.FOLLOW);
				} else if (ownerStill > 900 && !flies()) {
					setMode(Mode.SLEEP); // you went away from the keyboard: nap time
				}
			}
			case BOW, TAIL_CHASE -> {
				tx = x;
				tz = z;
				if (modeTicks > (mode == Mode.BOW ? 35 : 45)) {
					setMode(Mode.FOLLOW);
				}
			}
			case ZOOMIES -> {
				// a few fast laps around you
				float a = modeTicks * 0.16F + orbit;
				tx = owner.getX() + Math.cos(a) * 2.6;
				tz = owner.getZ() + Math.sin(a) * 2.6;
				if (modeTicks > 80) {
					setMode(Mode.FOLLOW);
				}
			}
			case SNIFF -> {
				if (interest == null || !interest.isAlive() || modeTicks > 120 || interest.squaredDistanceTo(owner) > 64) {
					setMode(Mode.FOLLOW);
				} else {
					tx = interest.getX();
					tz = interest.getZ();
					look(interest.getX(), interest.getY(), interest.getZ());
				}
			}
			case ALERT -> {
				if (interest == null || !interest.isAlive()) {
					setMode(Mode.FOLLOW);
				} else {
					// stays between you and the monster
					double mx = interest.getX() - owner.getX(), mz = interest.getZ() - owner.getZ();
					double l = Math.max(0.01, Math.sqrt(mx * mx + mz * mz));
					tx = owner.getX() + mx / l * 1.6;
					tz = owner.getZ() + mz / l * 1.6;
					look(interest.getX(), interest.getEyeY(), interest.getZ());
				}
			}
			case PLAY -> {
				if (friend == null || modeTicks > 100 || sq(friend.x - x, friend.y - y, friend.z - z) > 100) {
					setMode(Mode.FOLLOW);
				} else {
					float a = modeTicks * 0.12F + (ownerId & 3);
					tx = friend.x + Math.cos(a) * 1.3;
					tz = friend.z + Math.sin(a) * 1.3;
					look(friend.x, friend.y + friend.model.heightBlocks() * 0.7, friend.z);
				}
			}
			case PERCH -> {
				if (ownerStill == 0 || owner.isSprinting() || !owner.isOnGround()) {
					setMode(Mode.FOLLOW);
				}
			}
			default -> {
				// FOLLOW: idle tricks when you stand still next to it
				if (near && ownerStill > 30) {
					if (owner.isSneaking() && !flies()) {
						setMode(Mode.SIT);
					} else if (canPerch() && ownerStill > 100) {
						setMode(Mode.PERCH);
					} else if (ownerStill > 160 && !flies() && rnd.nextInt(60) == 0) {
						setMode(Mode.SIT);
					} else if (--nextIdle <= 0) {
						nextIdle = 90 + rnd.nextInt(160);
						idleTrick(owner);
					}
				} else if (owner.isSneaking() && near && !flies()) {
					setMode(Mode.SIT);
				}
				if (!hasLook && near && walk < 0.3F) {
					if (age % 70 < 40) {
						look(owner.getX(), owner.getEyeY(), owner.getZ());
					} else {
						look(lookX, lookY, lookZ); // keeps looking where the last idle glance went
					}
				}
			}
		}
	}

	private void idleTrick(PlayerEntity owner) {
		int r = rnd.nextInt(10);
		boolean legs = model.hasLegs() && !flies();
		if (r < 3) {
			// look at something around
			lookX = x + rnd.nextGaussian() * 4;
			lookY = y + rnd.nextFloat() * 2;
			lookZ = z + rnd.nextGaussian() * 4;
			headRoll = (rnd.nextFloat() - 0.5F) * 0.6F; // curious head tilt
		} else if (r < 5 && legs && model.kind == PetModel.Kind.WALK) {
			setMode(Mode.BOW);
		} else if (r < 6 && legs) {
			setMode(Mode.TAIL_CHASE);
		} else if (r < 7 && !flies()) {
			setMode(Mode.ZOOMIES);
		} else if (r < 8 && model.kind == PetModel.Kind.WALK) {
			scratch = 1F;
		} else if (flies()) {
			bank = 360F; // a little loop in the air
		} else {
			// wanders a couple of blocks away and sniffs the ground
			lookX = x + rnd.nextGaussian() * 2;
			lookY = y;
			lookZ = z + rnd.nextGaussian() * 2;
		}
	}

	private void look(double lx, double ly, double lz) {
		lookX = lx;
		lookY = ly;
		lookZ = lz;
		hasLook = true;
	}

	// ================================================================ movement: ground pets
	private void move(PlayerEntity owner, ClientWorld world, double dOwner) {
		double dx = tx - x, dz = tz - z;
		double d = Math.sqrt(dx * dx + dz * dz);
		boolean hop = model.kind == PetModel.Kind.HOP;
		double max = model.kind == PetModel.Kind.WADDLE ? 0.24 : hop ? 0.3 : 0.32;
		if (owner.isSprinting() || dOwner > 7 || mode == Mode.ZOOMIES) {
			max *= 1.45;
		}
		double want = 0;
		double stop = mode == Mode.FOLLOW ? 0.9 : mode == Mode.SNIFF ? 0.35 : 0.25;
		if (d > stop && mode != Mode.SIT && mode != Mode.SLEEP && mode != Mode.BOW && mode != Mode.TAIL_CHASE && mode != Mode.HAPPY) {
			want = Math.min(max, (d - stop * 0.6) * 0.16 + 0.04);
		}
		double wx = d > 1e-4 ? dx / d * want : 0, wz = d > 1e-4 ? dz / d * want : 0;
		if (hop) {
			// moves only while hopping; stands still on the ground in between
			if (onGround) {
				vx *= 0.3;
				vz *= 0.3;
				if (want > 0.02 && --hopCooldown <= 0) {
					vy = 0.34 + Math.min(0.08, d * 0.01);
					vx = wx * 1.25;
					vz = wz * 1.25;
					hopCooldown = 4 + rnd.nextInt(5);
					stretch = 1F;
				}
			}
		} else {
			double grip = onGround ? 0.32 : inWater ? 0.15 : 0.06;
			vx += (wx - vx) * grip;
			vz += (wz - vz) * grip;
		}
		if (inWater) {
			// swims: floats with its head out of the water
			double submerged = fluidTop(world, x, y, z) - y;
			vy = vy * 0.6 + (submerged > model.heightBlocks() * 0.45 ? 0.06 : -0.02);
		} else {
			vy = (vy - 0.08) * 0.98;
		}

		// horizontal move with simple block collisions: climbs one block by jumping, slides along walls
		double h = Math.min(0.95, Math.max(0.3, model.heightBlocks()));
		double nx = x + vx, nz = z + vz;
		double r = 0.22;
		boolean blockX = blocked(world, nx + Math.signum(vx) * r, y, z, h);
		boolean blockZ = blocked(world, x, y, nz + Math.signum(vz) * r, h);
		if (blockX || blockZ) {
			boolean canStep = !blocked(world, blockX ? nx + Math.signum(vx) * r : x, y + 1.05, blockZ ? nz + Math.signum(vz) * r : z, h);
			if (canStep && onGround && want > 0.02) {
				vy = 0.42; // jumps up the block like a mob
			}
			if (blockX) {
				nx = x;
				vx = 0;
			}
			if (blockZ) {
				nz = z;
				vz = 0;
			}
			stuck += want > 0.02 ? 1 : 0;
		} else {
			stuck = Math.max(0, stuck - 2);
		}
		x = nx;
		z = nz;
		double ny = y + vy;
		double ground = surface(world, x, z, Math.max(y, ny) + 0.05);
		groundY = Double.isNaN(ground) ? y - 2 : ground;
		boolean wasOnGround = onGround;
		if (!Double.isNaN(ground) && ny <= ground) {
			if (!wasOnGround && vy < -0.15) {
				squash = Math.min(1F, (float) -vy * 2.5F); // lands with a squish
			}
			ny = ground;
			vy = 0;
			onGround = true;
		} else {
			onGround = false;
		}
		y = ny;
		BlockPos feet = BlockPos.ofFloored(x, y + 0.2, z);
		inWater = !world.getFluidState(feet).isEmpty();

		// turns towards where it walks; when standing, towards what it looks at
		double hs = Math.sqrt(vx * vx + vz * vz);
		if (mode == Mode.TAIL_CHASE) {
			spin += 22F;
		} else if (hs > 0.03) {
			yaw = turn(yaw, (float) (MathHelper.atan2(-vx, vz) * MathHelper.DEGREES_PER_RADIAN), 22F);
		} else if (hasLook) {
			float want2 = (float) (MathHelper.atan2(-(lookX - x), lookZ - z) * MathHelper.DEGREES_PER_RADIAN);
			if (Math.abs(MathHelper.wrapDegrees(want2 - yaw)) > 55) {
				yaw = turn(yaw, want2, 7F); // too far to just turn the head: turns the body
			}
		}
		if (model.kind == PetModel.Kind.ROLL) {
			// walkPhase is the angle he has rolled: distance / radius; when he stops he settles with his face to the front again
			double radius = Math.max(0.1, model.heightBlocks() * 0.5);
			if (hs > 0.02) {
				walkPhase += (float) (hs / radius);
			} else {
				float whole = (float) (Math.round(walkPhase / (2 * Math.PI)) * 2 * Math.PI);
				walkPhase += (whole - walkPhase) * 0.12F;
			}
		} else {
			walkPhase += (float) (hs * (hop ? 0 : 14.0 / Math.max(0.5, model.heightBlocks() + 0.35)));
		}
	}

	// ================================================================ movement: flying / floating pets
	private void fly(PlayerEntity owner, ClientWorld world) {
		boolean ghost = model.kind == PetModel.Kind.FLOAT;
		float oy = owner.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double fx = -Math.sin(oy), fz = Math.cos(oy);
		double rx = -Math.cos(oy), rz = -Math.sin(oy);
		if (mode == Mode.PERCH) {
			// sits on your left shoulder, faces where you face
			double sy = owner.getY() + (owner.isSneaking() ? 1.12 : 1.42);
			double px = owner.getX() - rx * 0.36 + fx * 0.02, pz = owner.getZ() - rz * 0.36 + fz * 0.02;
			double dd = Math.sqrt(sq(px - x, sy - y, pz - z));
			if (perched || dd < 0.2) {
				perched = true;
				x = px;
				y = sy;
				z = pz;
				vx = vy = vz = 0;
				yaw = owner.bodyYaw;
				groundY = sy;
			} else {
				steer(px, sy, pz, 0.12, 0.35);
				x += vx;
				y += vy;
				z += vz;
				yaw = turn(yaw, (float) (MathHelper.atan2(-vx, vz) * MathHelper.DEGREES_PER_RADIAN), 15F);
			}
			return;
		}
		orbit += model.erratic ? 0.035F + (float) rnd.nextGaussian() * 0.02F : ghost ? 0.012F : 0.02F;
		double radius = mode == Mode.ALERT ? 1.2 : ghost ? 1.3 : 1.6;
		double hx, hy, hz;
		if (mode == Mode.PLAY && friend != null) {
			hx = tx;
			hz = tz;
			hy = friend.y + 0.6;
		} else if (mode == Mode.ALERT) {
			hx = tx;
			hz = tz;
			hy = owner.getY() + 1.6;
		} else {
			// flies about at random: picks a point around you (higher, lower, in front, behind), goes there, picks another
			if (age >= wanderUntil || sq(wanderX - x, wanderY - y, wanderZ - z) < 0.5) {
				double ang = rnd.nextDouble() * Math.PI * 2, r = (ghost ? 1.0 : 1.2) + rnd.nextDouble() * (ghost ? 2.2 : 3.4);
				wanderX = owner.getX() + Math.cos(ang) * r;
				wanderZ = owner.getZ() + Math.sin(ang) * r;
				wanderY = owner.getY() + (ghost ? 0.5 : 1.0) + rnd.nextDouble() * (ghost ? 1.4 : 2.4);
				wanderUntil = age + (ghost ? 90 : 40) + rnd.nextInt(ghost ? 120 : 70);
			}
			// when you walk it stays with you instead of being left behind
			double behind = owner.isSprinting() ? 2.2 : 0.0;
			hx = wanderX - fx * behind + Math.sin(age * 0.07 + seedA) * 0.25;
			hz = wanderZ - fz * behind + Math.cos(age * 0.06 + seedB) * 0.25;
			hy = wanderY + Math.sin(age * (ghost ? 0.05 : 0.09) + seedB) * (ghost ? 0.18 : 0.25);
		}
		if (model.erratic) {
			hy += Math.sin(age * 0.31) * 0.2;
		}
		// never inside a block (the ghost does not mind)
		if (!ghost) {
			int up = 0;
			while (up < 4 && solid(world, hx, hy + 0.2, hz)) {
				hy += 1;
				up++;
			}
		}
		double max = owner.isSprinting() ? 0.55 : 0.4;
		steer(hx, hy, hz, ghost ? 0.06 : 0.1, max);
		if (!ghost && solid(world, x + vx, y + vy + 0.2, z + vz)) {
			vy = Math.max(vy, 0.12);
			vx *= 0.5;
			vz *= 0.5;
		}
		x += vx;
		y += vy;
		z += vz;
		double g = surface(world, x, z, y + 0.05);
		groundY = Double.isNaN(g) ? y - 3 : g;
		if (!ghost && !Double.isNaN(g) && y < g + 0.05) {
			y = g + 0.05;
			vy = Math.max(0, vy);
		}
		double hs = Math.sqrt(vx * vx + vz * vz);
		float targetYaw = hs > 0.05 ? (float) (MathHelper.atan2(-vx, vz) * MathHelper.DEGREES_PER_RADIAN)
				: hasLook ? (float) (MathHelper.atan2(-(lookX - x), lookZ - z) * MathHelper.DEGREES_PER_RADIAN) : owner.bodyYaw;
		float before = yaw;
		yaw = turn(yaw, targetYaw, ghost ? 5F : 12F);
		float turnRate = MathHelper.wrapDegrees(yaw - before);
		if (bank > 30F) {
			bank -= 24F; // finishing a loop
		} else {
			bank += (MathHelper.clamp(-turnRate * 2.2F, -28F, 28F) - bank) * 0.2F;
		}
		tilt += ((float) MathHelper.clamp(hs * 45 - vy * 30, -20, 25) - tilt) * 0.2F;
	}

	private void steer(double hx, double hy, double hz, double k, double max) {
		double dx = (hx - x) * k, dy = (hy - y) * k, dz = (hz - z) * k;
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l > max) {
			dx *= max / l;
			dy *= max / l;
			dz *= max / l;
		}
		vx += (dx - vx) * 0.25;
		vy += (dy - vy) * 0.25;
		vz += (dz - vz) * 0.25;
	}

	// ================================================================ animation targets
	private void animate(PlayerEntity owner, ClientWorld world) {
		double hs = Math.sqrt(vx * vx + vz * vz);
		float walkTarget = flies() ? 0 : (float) MathHelper.clamp(hs / 0.18, 0, 1);
		if (model.kind == PetModel.Kind.HOP) {
			walkTarget = onGround ? 0 : 1;
		}
		walk += (walkTarget - walk) * 0.3F;
		boolean sleeping = mode == Mode.SLEEP;
		sit += ((mode == Mode.SIT || mode == Mode.PERCH && !flies() ? 1 : 0) - sit) * 0.18F;
		lie += ((sleeping ? 1 : 0) - lie) * 0.08F;
		bow += ((mode == Mode.BOW ? 1 : 0) - bow) * 0.2F;
		squash *= 0.78F;
		stretch *= 0.8F;
		scratch = Math.max(0, scratch - 0.012F);
		boolean happy = mode == Mode.HAPPY;
		wave += ((happy ? 1 : 0) - wave) * 0.2F;
		float wagTarget = happy ? 1.05F : mode == Mode.PLAY || mode == Mode.ZOOMIES ? 0.85F : sleeping ? 0.03F
				: mode == Mode.ALERT ? 0.12F : walk > 0.3F ? 0.5F : 0.3F;
		wagSpeed += (wagTarget - wagSpeed) * 0.1F;
		wagPhase += wagSpeed;
		if (happy && modeTicks < 16 && !flies() && model.kind != PetModel.Kind.HOP) {
			spin += 360F / 16F; // a happy spin
		}
		if (spin >= 360F && prevSpin >= 360F) {
			spin -= 360F;
			prevSpin -= 360F;
		}
		if (mode != Mode.TAIL_CHASE && !(happy && modeTicks < 16)) {
			// settle the spin back to 0 the short way
			float s = MathHelper.wrapDegrees(spin);
			spin = s * 0.7F;
			prevSpin = MathHelper.wrapDegrees(prevSpin);
		}
		// wings: fast while flying, folded when perched / sitting
		boolean flying = flies() && !perched;
		fold += ((flying ? 0 : 1) - fold) * 0.2F;
		if (model.kind == PetModel.Kind.FLY) {
			flapPhase += flying ? 0.5F * model.flap * (vy > 0.02 ? 1.4F : vy < -0.05 ? 0.55F : 1F) : 0.03F;
		} else {
			flapPhase += 0.12F;
		}
		if (!flies()) {
			bank *= 0.8F;
			tilt *= 0.8F;
		}
		// head: looks at what interests it, within what a neck can do
		float hyT = 0, hpT = 0;
		if (sleeping) {
			hpT = 18F;
		} else if (hasLook && walk < 0.6F) {
			double dx = lookX - x, dz = lookZ - z;
			double dy = lookY - (y + model.heightBlocks() * 0.8);
			float want = (float) (MathHelper.atan2(-dx, dz) * MathHelper.DEGREES_PER_RADIAN);
			hyT = MathHelper.clamp(MathHelper.wrapDegrees(want - yaw - spin), -65F, 65F);
			hpT = (float) MathHelper.clamp(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN, -35, 35);
		}
		if (mode == Mode.SNIFF && interest != null && Math.sqrt(sq(interest.getX() - x, 0, interest.getZ() - z)) < 0.8) {
			hpT = 32F; // nose on the item
		}
		headYaw += (hyT - headYaw) * 0.25F;
		headPitch += (hpT - headPitch) * 0.25F;
		headRoll *= 0.985F;
		if (mode == Mode.ALERT && age % 40 == 0) {
			if (age - lastAlertSound > 200) { // one bark when it notices something, not one every 2 seconds
				lastAlertSound = age;
				sound(world, model.soundAmbient, 0.85F, 0.7F);
			}
			if (onGround && !flies()) {
				vy = 0.25; // barks with a little bounce
			}
			if ("flame".equals(model.particles)) {
				puff(world, ParticleTypes.SMALL_FLAME, 4);
			}
		}
		// dragons breathe a little fire now and then
		if ("flame".equals(model.particles) && !perched && rnd.nextInt(happy ? 6 : 220) == 0) {
			puff(world, ParticleTypes.SMALL_FLAME, 2);
		}
	}

	// ================================================================ interaction
	/** Right click on the pet. */
	/** PatPat on this pet: when the last pat was (ms) and how many in a row. */
	public long patAt;
	public int combo;

	void pet(ClientWorld world, boolean food) {
		setMode(Mode.HAPPY);
		if (!flies() && onGround) {
			vy = model.kind == PetModel.Kind.HOP ? 0.45 : 0.38;
		}
		if (flies()) {
			bank = 360F;
		}
		puff(world, ParticleTypes.HEART, food ? 7 : 4);
		if (food) {
			puff(world, ParticleTypes.HAPPY_VILLAGER, 5);
			sound(world, "entity.generic.eat", 1.2F, 0.6F);
		}
		sound(world, model.soundHappy != null ? model.soundHappy : model.soundAmbient, 1F, 1F);
	}

	/** Left click on the pet: a little startled jump. */
	void boop(ClientWorld world) {
		if (mode == Mode.SLEEP || mode == Mode.SIT || mode == Mode.PERCH) {
			setMode(Mode.FOLLOW);
		}
		if (onGround && !flies()) {
			vy = 0.3;
		}
		headRoll = 0.5F;
		sound(world, model.soundAmbient, 1.3F, 0.7F);
	}

	private void sounds(ClientWorld world) {
		if (--ambientIn <= 0) {
			ambientIn = 1200 + rnd.nextInt(2400); // once every 1-3 minutes, quietly
			if (mode != Mode.SLEEP) {
				sound(world, model.soundAmbient, 1F, 0.6F);
			}
		}
		// footsteps on the block under it
		if (!flies() && model.kind != PetModel.Kind.ROLL && onGround && walk > 0.4F && !inWater) {
			int step = (int) (walkPhase / Math.PI);
			if (step != stepSound) {
				stepSound = step;
				BlockPos under = BlockPos.ofFloored(x, y - 0.1, z);
				BlockState st = world.getBlockState(under);
				if (!st.isAir()) {
					world.playSoundClient(x, y, z, st.getSoundGroup().getStepSound(), net.minecraft.sound.SoundCategory.NEUTRAL,
							mine ? 0.09F : 0.05F, 1.6F + rnd.nextFloat() * 0.2F, false);
				}
			}
		}
	}

	void sound(ClientWorld world, String id, float pitchMul, float volMul) {
		if (dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.petSounds != null
				&& !dev.solclient.SolClient.settings.petSounds.on()) {
			return;
		}
		net.minecraft.sound.SoundEvent ev = Pets.soundEvent(id);
		if (ev == null) {
			return;
		}
		float vol = model.soundVolume * volMul * (mine ? 0.8F : 0.4F);
		world.playSoundClient(x, y + model.heightBlocks() * 0.5, z, ev, net.minecraft.sound.SoundCategory.NEUTRAL, vol,
				model.soundPitch * pitchMul * (0.92F + rnd.nextFloat() * 0.16F), false);
	}

	private void puff(ClientWorld world, ParticleEffect p, int n) {
		double h = model.heightBlocks();
		for (int i = 0; i < n; i++) {
			world.addParticleClient(p, x + rnd.nextGaussian() * 0.25, y + h * 0.9 + rnd.nextFloat() * 0.3, z + rnd.nextGaussian() * 0.25,
					rnd.nextGaussian() * 0.02, 0.05 + rnd.nextFloat() * 0.03, rnd.nextGaussian() * 0.02);
		}
	}

	/** Spawns (or teleports) the pet next to its owner, with a puff of smoke when it teleports. */
	void placeNear(PlayerEntity owner, boolean poof) {
		float oy = owner.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		double side = (ownerId & 1) == 0 ? 1 : -1;
		x = owner.getX() + (-Math.cos(oy)) * side * 0.9 + Math.sin(oy) * 0.8;
		z = owner.getZ() + (-Math.sin(oy)) * side * 0.9 - Math.cos(oy) * 0.8;
		y = owner.getY() + (flies() ? 1.6 : 0);
		if (!flies() && owner.getEntityWorld() instanceof ClientWorld w) {
			double g = surface(w, x, z, owner.getY() + 1.0);
			if (Double.isNaN(g) || Math.abs(g - owner.getY()) > 1.5 || blocked(w, x, g, z, 0.5)) {
				x = owner.getX();
				z = owner.getZ();
				g = owner.getY();
			}
			y = g;
		}
		prevX = x;
		prevY = y;
		prevZ = z;
		vx = vy = vz = 0;
		yaw = prevYaw = owner.bodyYaw;
		stuck = 0;
		groundY = owner.getY();
		if (poof && owner.getEntityWorld() instanceof ClientWorld w) {
			puff(w, ParticleTypes.POOF, 8);
			if (mode == Mode.PERCH || mode == Mode.SLEEP) {
				setMode(Mode.FOLLOW);
			}
		}
	}

	// ================================================================ block helpers
	/** Top of the highest solid block surface at or below 'fromY' (scans 6 blocks down), NaN when there is none. */
	static double surface(ClientWorld w, double x, double z, double fromY) {
		int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
		int top = MathHelper.floor(fromY);
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int by = top; by >= top - 6; by--) {
			p.set(bx, by, bz);
			BlockState st = w.getBlockState(p);
			if (st.isAir()) {
				continue;
			}
			VoxelShape s = st.getCollisionShape(w, p);
			if (!s.isEmpty()) {
				double t = by + s.getMax(Direction.Axis.Y);
				if (t <= fromY + 1e-3) {
					return t;
				}
			}
		}
		return Double.NaN;
	}

	static boolean solid(ClientWorld w, double x, double y, double z) {
		BlockPos p = BlockPos.ofFloored(x, y, z);
		BlockState st = w.getBlockState(p);
		if (st.isAir()) {
			return false;
		}
		VoxelShape s = st.getCollisionShape(w, p);
		if (s.isEmpty()) {
			return false;
		}
		double ly = y - p.getY();
		return ly >= s.getMin(Direction.Axis.Y) - 1e-3 && ly < s.getMax(Direction.Axis.Y);
	}

	/** Is the column at x/z blocked between y + 0.1 and y + h (the pet's body)? */
	static boolean blocked(ClientWorld w, double x, double y, double z, double h) {
		return solid(w, x, y + 0.1, z) || solid(w, x, y + h * 0.5, z) || solid(w, x, y + h - 0.05, z);
	}

	static double fluidTop(ClientWorld w, double x, double y, double z) {
		BlockPos p = BlockPos.ofFloored(x, y + 0.2, z);
		double top = y;
		for (int i = 0; i < 3; i++) {
			if (w.getFluidState(p).isEmpty()) {
				break;
			}
			top = p.getY() + w.getFluidState(p).getHeight(w, p);
			p = p.up();
		}
		return top;
	}

	private static float turn(float from, float to, float max) {
		return from + MathHelper.clamp(MathHelper.wrapDegrees(to - from), -max, max);
	}

	private static double sq(double a, double b, double c) {
		return a * a + b * b + c * c;
	}

	// ================================================================ pose for drawing
	/** Interpolated value between the last tick and this one. */
	private static float lerp(float d, float a, float b) {
		return a + (b - a) * d;
	}

	/**
	 * Fills 'out' with 9 floats per bone: rotation x, y, z (radians), offset x, y, z (pixels), scale x, y, z.
	 * 'd' is the progress between ticks (0..1).
	 */
	void pose(float d, float[] out) {
		float t = age + d;
		float wk = lerp(d, prevWalk, walk), ph = lerp(d, prevWalkPhase, walkPhase);
		float st = lerp(d, prevSit, sit), li = lerp(d, prevLie, lie), bw = lerp(d, prevBow, bow);
		float sq = lerp(d, prevSquash, squash), sr = lerp(d, prevStretch, stretch);
		float fl = lerp(d, prevFlapPhase, flapPhase), fo = lerp(d, prevFold, fold);
		float wg = lerp(d, prevWagPhase, wagPhase), wv = lerp(d, prevWave, wave), sc = lerp(d, prevScratch, scratch);
		float hy = lerp(d, prevHeadYaw, headYaw) * MathHelper.RADIANS_PER_DEGREE;
		float hp = lerp(d, prevHeadPitch, headPitch) * MathHelper.RADIANS_PER_DEGREE;
		float hr = lerp(d, prevHeadRoll, headRoll);
		poseFrom(model, out, t, wk, ph, st, li, bw, sq, sr, fl, fo, wg, wv, sc, hy, hp, hr, onGround, flies() && !perched);
	}

	/** The pose of a pet standing next to you in a menu (idle animation only). */
	static void previewPose(PetModel m, float t, float[] out) {
		boolean fly = m.kind == PetModel.Kind.FLY || m.kind == PetModel.Kind.FLOAT;
		float look = (float) Math.sin(t * 0.02F) * 0.5F;
		poseFrom(m, out, t, 0, 0, 0, 0, 0, 0, 0, t * 0.5F * m.flap, 0, t * 0.3F, 0, 0, look, 0, (float) Math.sin(t * 0.013F) * 0.15F, true, fly);
	}

	static void poseFrom(PetModel m, float[] out, float t, float wk, float ph, float st, float li, float bw, float sq, float sr,
			float fl, float fo, float wg, float wv, float sc, float hy, float hp, float hr, boolean ground, boolean flying) {
		PetModel.Bone[] bones = m.bones;
		float legH = 3F;
		for (PetModel.Bone b : bones) {
			if (b.role.startsWith("leg")) {
				legH = Math.max(1F, b.py);
				break;
			}
		}
		float breathe = (float) Math.sin(t * (li > 0.5F ? 0.05F : 0.09F)) * 0.025F;
		float sw = (float) Math.sin(ph);
		boolean hop = m.kind == PetModel.Kind.HOP, waddle = m.kind == PetModel.Kind.WADDLE, ghost = m.kind == PetModel.Kind.FLOAT;
		for (int i = 0; i < bones.length; i++) {
			float rx = 0, ry = 0, rz = 0, ox = 0, oy = 0, oz = 0, sx = 1, sy = 1, sz = 1;
			String r = bones[i].role;
			switch (r) {
				case "body" -> {
					if (m.kind == PetModel.Kind.WALK) {
						oy += Math.abs(sw) * 0.55F * wk;
						rz += sw * 0.04F * wk;
					} else if (waddle) {
						rz += sw * 0.26F * wk + (float) Math.sin(t * 0.06F) * 0.03F;
						oy += Math.abs(sw) * 0.4F * wk;
					} else if (ghost) {
						rz += (float) Math.sin(t * 0.07F) * 0.08F;
						rx += (float) Math.sin(t * 0.05F) * 0.05F;
					}
					if (m.kind == PetModel.Kind.ROLL) {
						rx -= ph; // rolls forward
					} else {
						rx += 0.38F * st - 0.3F * bw;
						oy -= st * legH * 0.3F + li * legH * 0.85F;
					}
					float squish = hop ? sq : sq * 0.4F;
					sx = sz = 1 + breathe * 0.6F + squish * 0.2F - sr * 0.07F;
					sy = 1 + breathe - squish * 0.3F + sr * 0.12F;
				}
				case "head" -> {
					ry -= hy; // the model turns the other way round than Minecraft's yaw
					if (m.kind == PetModel.Kind.ROLL) {
						rx += -hp;
						ry += 0F;
					} else {
						rx += -hp - 0.38F * st + 0.2F * bw + (float) Math.sin(ph * 2) * 0.05F * wk;
					}
					rz += hr + (float) Math.sin(t * 0.04F) * 0.03F;
					if (li > 0 && m.kind != PetModel.Kind.ROLL) {
						oy -= li * 0.6F;
					}
				}
				case "leg_fl", "leg_br", "leg_fr", "leg_bl" -> {
					boolean front = r.charAt(4) == 'f';
					boolean a = r.equals("leg_fl") || r.equals("leg_br");
					float swing = (a ? sw : -sw);
					if (waddle) {
						swing = r.endsWith("l") ? sw : -sw;
					}
					rx += swing * (waddle ? 0.7F : 0.9F) * wk;
					if (hop) {
						rx += front ? -0.5F * wk : 0.4F * wk; // tucked in mid-air
					}
					if (flying) {
						rx += -0.35F + (float) Math.sin(t * 0.1F + (front ? 0 : 1)) * 0.08F; // dangling
					}
					if (front) {
						rx += -0.38F * st + 0.35F * bw + 1.35F * li;
					} else {
						rx += 1.0F * st - 1.35F * li;
						if (sc > 0 && r.equals("leg_br")) {
							rx += (0.6F + (float) Math.sin(t * 1.7F) * 0.55F) * Math.min(1F, sc * 3F); // scratching behind the ear
						}
					}
				}
				case "arm_l", "arm_r" -> {
					float s = r.equals("arm_l") ? -1 : 1;
					rx += (r.equals("arm_l") ? sw : -sw) * 0.6F * wk;
					rz += s * ((float) Math.sin(t * 0.06F) * 0.06F + 0.05F);
					if (hop) {
						rz += s * 0.7F * wk; // arms up while hopping
					}
					if (ghost) {
						rz += s * (0.25F + (float) Math.sin(t * 0.09F + s) * 0.25F);
					}
					if (wv > 0 && r.equals("arm_r")) {
						rz += wv * (1.9F + (float) Math.sin(t * 0.9F) * 0.45F); // waves at you
					}
				}
				case "tail" -> {
					if (ghost) {
						ry += (float) Math.sin(t * 0.1F) * 0.12F;
						sy = 1 + (float) Math.sin(t * 0.15F) * 0.1F;
					} else if (flying) {
						ry += (float) Math.sin(t * 0.07F) * 0.35F;
						rx += (float) Math.sin(t * 0.05F) * 0.1F;
					} else {
						ry += (float) Math.sin(wg) * (0.5F + 0.2F * (1 - li)) * (1 - li * 0.8F);
						rx -= 0.25F * (1 - li) + wk * 0.15F - st * 0.3F; // up (negative x lifts the tip)
					}
				}
				case "ear_l", "ear_r" -> {
					float s = r.equals("ear_l") ? -1 : 1;
					rz += s * ((float) Math.sin(t * 0.05F + s) * 0.05F + Math.abs(sw) * 0.15F * wk);
					rx += -0.1F * wv;
				}
				case "wing_l", "wing_r" -> {
					float s = r.equals("wing_l") ? -1 : 1;
					float flap = (float) Math.sin(fl) * 0.85F * (1 - fo) + 0.1F;
					rz += s * flap;
					ry += -s * 1.15F * fo; // folded along the back
				}
				case "top" -> {
					rz += (float) Math.sin(t * 0.07F) * 0.1F + sw * 0.08F * wk;
					rx += (float) Math.sin(t * 0.05F) * 0.06F;
				}
				default -> {
				}
			}
			int o = i * 9;
			out[o] = rx;
			out[o + 1] = ry;
			out[o + 2] = rz;
			out[o + 3] = ox;
			out[o + 4] = oy;
			out[o + 5] = oz;
			out[o + 6] = sx;
			out[o + 7] = sy;
			out[o + 8] = sz;
		}
	}

	/** Interpolated world position. */
	double rx(float d) {
		return prevX + (x - prevX) * d;
	}

	double ry(float d) {
		return prevY + (y - prevY) * d;
	}

	double rz(float d) {
		return prevZ + (z - prevZ) * d;
	}

	float renderYaw(float d) {
		return prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * d + lerp(d, prevSpin, spin);
	}

	float renderBank(float d) {
		return lerp(d, prevBank, bank);
	}

	float renderTilt(float d) {
		return lerp(d, prevTilt, tilt);
	}
}
