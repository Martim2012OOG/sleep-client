package dev.sleepclient.chatx;

import java.util.List;

/** Added to EntityRenderState: the chat bubbles to draw this frame. */
public interface BubbleHolder {
	List<dev.sleepclient.mods.ChatBubblesMod.Bubble> plegumo$bubbles();

	void plegumo$setBubbles(List<dev.sleepclient.mods.ChatBubblesMod.Bubble> bubbles);
}
