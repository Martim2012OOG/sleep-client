package dev.sleepclient.mixin.overlay;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {

	@Accessor("allMessages")
	List<GuiMessage> plegumo$messages();

	@Invoker("refreshTrimmedMessages")
	void plegumo$refresh();
}
