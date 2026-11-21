package dev.sleepclient.mixin.social;

import dev.sleepclient.chatx.BubbleHolder;
import dev.sleepclient.mods.ChatBubblesMod;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class BubbleStateMixin implements BubbleHolder {

	@Unique
	private List<ChatBubblesMod.Bubble> plegumo$bubbles;

	@Override
	public List<ChatBubblesMod.Bubble> plegumo$bubbles() {
		return plegumo$bubbles;
	}

	@Override
	public void plegumo$setBubbles(List<ChatBubblesMod.Bubble> bubbles) {
		this.plegumo$bubbles = bubbles;
	}
}
