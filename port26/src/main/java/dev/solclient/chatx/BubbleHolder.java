package dev.solclient.chatx;

import java.util.List;

/** Added to EntityRenderState: the chat bubbles to draw this frame. */
public interface BubbleHolder {
	List<dev.solclient.mods.ChatBubblesMod.Bubble> plegumo$bubbles();

	void plegumo$setBubbles(List<dev.solclient.mods.ChatBubblesMod.Bubble> bubbles);
}
