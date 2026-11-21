package dev.sleepclient.fx;

/** Extra data on a dropped item's render state for Item Physics: is it lying on the ground / in water. */
public interface ItemPhysicsState {

	boolean kubo$onGround();

	boolean kubo$inWater();

	void kubo$setPhysics(boolean onGround, boolean inWater);
}
