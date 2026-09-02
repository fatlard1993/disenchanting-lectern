package justfatlard.disenchanting_lectern;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Reading an item's enchantments out at an empty lectern.
 *
 * <p>Two things get printed per enchantment. The facts the game knows - how near the ceiling the
 * level is, what the enchantment refuses to sit beside, whether it is treasure or a curse - and
 * then a line of plain English saying what it actually does, which the game does not know and
 * {@link EnchantmentProse} loads from data packs.
 *
 * <p>An enchantment with no prose written for it still gets its facts, so a modded or datapack
 * enchantment reads as well as it can rather than not at all.
 */
public final class EnchantmentReadout {
	private EnchantmentReadout() {}

	/**
	 * Ticks a player has to wait between readouts.
	 *
	 * <p>A held right mouse button repeats every four ticks, which would put five copies of the
	 * same paragraph in the chat every second. One a second is still instant to anyone who meant
	 * to look once.
	 */
	private static final long COOLDOWN_TICKS = 20L;

	/** Last readout per player, so a held button does not flood the chat. */
	private static final Map<UUID, Long> lastRead = new HashMap<>();

	public static InteractionResult describe(ServerPlayer player, ServerLevel level, ItemStack held) {
		ItemEnchantments enchantments = enchantmentsOn(held);
		if (enchantments.isEmpty()) return InteractionResult.PASS;

		long now = level.getGameTime();
		Long previous = lastRead.get(player.getUUID());
		if (previous != null && now - previous < COOLDOWN_TICKS) {
			// Still consume the click. Falling through to PASS would let the same held button be
			// re-offered to the off hand and to vanilla, which is a different outcome than the one
			// the player just had - the interaction should look identical whether or not the
			// cooldown happens to be up.
			return InteractionResult.SUCCESS;
		}
		lastRead.put(player.getUUID(), now);

		player.sendSystemMessage(
			Component.empty().append(held.getHoverName()).withStyle(ChatFormatting.GOLD));

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			player.sendSystemMessage(line(entry.getKey(), entry.getIntValue()));

			String prose = EnchantmentProse.forEnchantment(entry.getKey());
			if (prose != null) {
				player.sendSystemMessage(Component.literal("      " + prose)
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * An item's own enchantments, or a book's stored ones.
	 *
	 * <p>The two live in different components, and a book is the one thing people most want to read
	 * before deciding what to do with it.
	 */
	private static ItemEnchantments enchantmentsOn(ItemStack stack) {
		ItemEnchantments own = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!own.isEmpty()) return own;

		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	private static Component line(Holder<Enchantment> holder, int level) {
		MutableComponent line = Component.literal("  ")
			.append(Enchantment.getFullname(holder, level));

		List<Component> notes = new ArrayList<>();
		notes.add(levelNote(holder, level));

		if (holder.is(EnchantmentTags.CURSE)) {
			notes.add(Component.literal("a curse, and stays put").withStyle(ChatFormatting.RED));
		} else if (holder.is(EnchantmentTags.TREASURE)) {
			notes.add(Component.literal("treasure - not from a table")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}

		Component conflicts = conflictNote(holder);
		if (conflicts != null) notes.add(conflicts);

		for (Component note : notes) {
			line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(note);
		}
		return line;
	}

	private static Component levelNote(Holder<Enchantment> holder, int level) {
		int max = holder.value().getMaxLevel();

		if (level >= max) {
			return Component.literal("maxed").withStyle(ChatFormatting.GREEN);
		}
		return Component.literal("level ").append(roman(level)).append(" of ").append(roman(max))
			.withStyle(ChatFormatting.GRAY);
	}

	/** The enchantments this one refuses to share an item with. */
	private static Component conflictNote(Holder<Enchantment> holder) {
		List<Component> names = new ArrayList<>();
		for (Holder<Enchantment> excluded : holder.value().exclusiveSet()) {
			names.add(excluded.value().description());
		}
		if (names.isEmpty()) return null;

		MutableComponent note = Component.literal("not with ");
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) note.append(", ");
			note.append(names.get(i));
		}
		return note.withStyle(ChatFormatting.GRAY);
	}

	/**
	 * The numeral the game itself uses for a level, so a readout matches the name printed beside it.
	 *
	 * <p>Vanilla only translates one through ten; past that it prints the number, which is what a
	 * datapack enchantment with a twelve-level ceiling should get rather than nothing.
	 */
	private static Component roman(int level) {
		if (level >= 1 && level <= 10) return Component.translatable("enchantment.level." + level);
		return Component.literal(Integer.toString(level));
	}
}
