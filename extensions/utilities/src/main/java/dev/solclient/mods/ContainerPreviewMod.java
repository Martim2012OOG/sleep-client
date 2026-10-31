package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shows everything inside shulker boxes (and other containers) in the tooltip. */
public class ContainerPreviewMod extends Mod {

	public static ContainerPreviewMod instance;
	private final Option.Num lines = num("Max Lines", 12, 3, 27, 1, "");

	public ContainerPreviewMod() {
		super("Container Preview", "container_preview", "See what is inside shulker boxes from the tooltip.", Category.UTILITY);
		instance = this;
	}

	public static void addLines(ItemStack stack, List<Text> out) {
		ContainerPreviewMod m = instance;
		if (m == null || !m.enabled || stack == null) return;
		var c = stack.get(DataComponentTypes.CONTAINER);
		if (c == null) return;
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (ItemStack it : c.iterateNonEmpty()) {
			sum.merge(it.getName().getString(), it.getCount(), Integer::sum);
		}
		if (sum.isEmpty()) return;
		out.add(Text.literal("Contents:").formatted(Formatting.GOLD));
		int n = 0;
		for (var e : sum.entrySet()) {
			if (n++ >= m.lines.i()) {
				out.add(Text.literal(" ...and " + (sum.size() - m.lines.i()) + " more").formatted(Formatting.DARK_GRAY));
				break;
			}
			out.add(Text.literal(" " + e.getValue() + "x " + e.getKey()).formatted(Formatting.GRAY));
		}
	}
}
