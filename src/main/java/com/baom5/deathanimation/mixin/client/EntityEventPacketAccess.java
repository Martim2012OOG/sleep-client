package com.baom5.deathanimation.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(EntityStatusS2CPacket.class)
public interface EntityEventPacketAccess {
   @Accessor("field_12175")
   int deathanimations$entityId();
}
