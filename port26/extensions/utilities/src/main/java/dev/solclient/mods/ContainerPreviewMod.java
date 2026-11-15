package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows everything inside shulker boxes (and other containers) in the tooltip. */
public class ContainerPreviewMod extends Mod {

	public static ContainerPreviewMod instance;
	private final Option.Num lines = num("Max Lines", 12, 3, 27, 1, "");

	public ContainerPreviewMod() {
		super("Container Preview", "container_preview", "See what is inside shulker boxes from the tooltip.", Category.UTILITY);
		instance = this;
	}

	public static void addLines(ItemStack stack, List<Component> out) {
		ContainerPreviewMod m = instance;
		if (m == null || !m.enabled || stack == null) return;
		var c = stack.get(DataComponents.CONTAINER);
		if (c == null) return;
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (ItemStack it : (Iterable<ItemStack>) c.nonEmptyItemCopyStream()::iterator) {
			sum.merge(it.getHoverName().getString(), it.getCount(), Integer::sum);
		}
		if (sum.isEmpty()) return;
		out.add(Component.literal("Contents:").withStyle(ChatFormatting.GOLD));
		int n = 0;
		for (var e : sum.entrySet()) {
			if (n++ >= m.lines.i()) {
				out.add(Component.literal(" ...and " + (sum.size() - m.lines.i()) + " more").withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			out.add(Component.literal(" " + e.getValue() + "x " + e.getKey()).withStyle(ChatFormatting.GRAY));
		}
	}
}
