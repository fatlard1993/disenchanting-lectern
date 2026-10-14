package justfatlard.disenchanting_lectern;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pulling the enchantments off an item and into a book, at a lectern.
 *
 * <p>Put a blank book and quill on a lectern, right-click it holding something enchanted, and the
 * enchantments move: the item comes back clean in your hand, an enchanted book carrying what it
 * had pops off the lectern, and the blank book is spent making it.
 *
 * <p>This is the piece vanilla is missing between the grindstone and the anvil. The grindstone
 * takes enchantments off and destroys them; the anvil puts books on. There is no way to get one
 * back off an item you would rather not keep - so a good enchantment on the wrong tool is stuck
 * there. This is that step, and it costs a book and quill rather than levels, which is the sort of
 * price a workbench charges rather than an altar.
 */
public final class Disenchanting {
	private Disenchanting() {}

	/** How briskly the finished book leaves the lectern. */
	private static final double POP_SPEED = 0.2;

	/** Called with a lectern already known to have a book on it. */
	public static InteractionResult transfer(ServerPlayer player, ServerLevel level, BlockPos pos,
			BlockState state, ItemStack held) {
		if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
			return InteractionResult.PASS;
		}
		if (!isBlankBook(lectern.getBook())) return InteractionResult.PASS;
		if (held.is(Items.ENCHANTED_BOOK)) return split(player, level, pos, state, lectern, held);

		ItemEnchantments enchantments = held.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null || enchantments.isEmpty()) return InteractionResult.PASS;

		ItemEnchantments.Mutable moving = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable staying = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			// Curses do not come off, which is the whole of what a curse is. The grindstone makes
			// the same exception, and a lectern that did not would leave Binding and Vanishing as
			// drawbacks anybody could launder away for the price of a book.
			if (entry.getKey().is(EnchantmentTags.CURSE)) {
				staying.set(entry.getKey(), entry.getIntValue());
			} else {
				moving.set(entry.getKey(), entry.getIntValue());
			}
		}

		ItemEnchantments moved = moving.toImmutable();
		if (moved.isEmpty()) return InteractionResult.PASS;

		strip(held, staying.toImmutable());
		finish(player, level, pos, state, lectern, bookOf(moved));
		return InteractionResult.SUCCESS;
	}

	/**
	 * Splitting a book: one enchantment off a book that carries several, onto the blank one.
	 *
	 * <p>One a blank book, because one blank book makes one enchanted book, the same as stripping
	 * an item. The one at the top of the book's tooltip comes off and the rest stay on the book in
	 * your hand, so a book of four comes apart in three goes.
	 *
	 * <p>A book carrying a curse does not split. Splitting would let Mending walk away from the
	 * Binding it came bound to, which is the laundering a lectern refuses for items too; the
	 * grindstone is still there for a cursed book you would rather be rid of.
	 */
	private static InteractionResult split(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
			LecternBlockEntity lectern, ItemStack held) {
		ItemEnchantments stored = held.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.size() < 2) return InteractionResult.PASS;
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			if (enchantment.is(EnchantmentTags.CURSE)) {
				player.sendOverlayMessage(Component.literal("A cursed book doesn't come apart"));
				return InteractionResult.FAIL;
			}
		}

		Holder<Enchantment> top = topOf(level, stored);
		ItemEnchantments.Mutable taking = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable leaving = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
			(entry.getKey().equals(top) ? taking : leaving).set(entry.getKey(), entry.getIntValue());
		}

		held.set(DataComponents.STORED_ENCHANTMENTS, leaving.toImmutable());
		// Each half is a lighter book than the whole was; the work that went into the whole is
		// cleared, as stripping an item clears it.
		held.set(DataComponents.REPAIR_COST, 0);
		finish(player, level, pos, state, lectern, bookOf(taking.toImmutable()));
		return InteractionResult.SUCCESS;
	}

	/**
	 * The enchantment at the top of the book's tooltip: the one the player sees first, so the one
	 * they can tell will come off before they click. The book holds its enchantments in no order
	 * of its own; the tooltip sorts them by the game's tooltip order tag, and so does this.
	 */
	private static Holder<Enchantment> topOf(ServerLevel level, ItemEnchantments stored) {
		var order = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER);
		if (order.isPresent()) {
			for (Holder<Enchantment> enchantment : order.get()) if (stored.getLevel(enchantment) > 0) return enchantment;
		}
		return stored.keySet().iterator().next();
	}

	/** The blank book spent, the new one handed over, and the lectern's sound and sparkle. */
	private static void finish(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
			LecternBlockEntity lectern, ItemStack made) {
		consumeBlankBook(player, level, pos, state, lectern);
		popOff(level, pos, made);
		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.ENCHANT,
			pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.5);
	}

	/**
	 * Whether this is a book with nothing in it.
	 *
	 * <p>Blankness is what makes the interception safe, not just what the request happens to say. A
	 * lectern with a written book on it is a lectern somebody set up to be read, and a book and
	 * quill with pages in it is a draft; consuming either because the reader happened to be holding
	 * an enchanted pickaxe would be destroying something to do a favour nobody asked for. A blank
	 * book on a lectern has no other use, so acting on one cannot interrupt anything.
	 */
	private static boolean isBlankBook(ItemStack book) {
		if (!book.is(Items.WRITABLE_BOOK)) return false;

		WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
		return content == null || content.pages().isEmpty();
	}

	/**
	 * Leave the held item clean, in the hand it was already in.
	 *
	 * @param keeping the enchantments that do not come off, usually none
	 */
	private static void strip(ItemStack held, ItemEnchantments keeping) {
		// Set empty, never removed. The enchanting table only takes an item whose enchantments are
		// there and empty: one with the component taken away altogether reads as not enchantable at
		// all, and a stripped sword could never be enchanted again.
		held.set(DataComponents.ENCHANTMENTS, keeping);

		// The prior-work penalty is a tax on the enchanting that just left. Carrying it on an item
		// that is now bare would make a stripped tool quietly more expensive to work than a fresh
		// one, which is the opposite of what stripping it was for. The grindstone clears it too.
		held.set(DataComponents.REPAIR_COST, 0);
	}

	/**
	 * An item an earlier version of this mod stripped, put right: its enchantments were taken away
	 * altogether rather than emptied, which leaves it refused by the enchanting table for good.
	 *
	 * @return whether there was anything to put right
	 */
	public static boolean mend(ItemStack held) {
		if (held.has(DataComponents.ENCHANTMENTS) || !held.getPrototype().has(DataComponents.ENCHANTMENTS)) return false;
		held.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (!held.has(DataComponents.REPAIR_COST)) held.set(DataComponents.REPAIR_COST, 0);
		return true;
	}

	private static ItemStack bookOf(ItemEnchantments enchantments) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
		return book;
	}

	private static void consumeBlankBook(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
			LecternBlockEntity lectern) {
		lectern.clearContent();
		// Puts the lectern back to its empty state properly - the block property, the comparator
		// output that reads off it, and the game event a lectern emits when its book changes.
		LecternBlock.resetBookState(player, level, pos, state, false);
	}

	private static void popOff(ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemEntity entity = new ItemEntity(level,
			pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);

		// The constructor scatters it in a random direction; straight up reads as the lectern
		// handing the book over rather than dropping it.
		entity.setDeltaMovement(0.0, POP_SPEED, 0.0);
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}
}
