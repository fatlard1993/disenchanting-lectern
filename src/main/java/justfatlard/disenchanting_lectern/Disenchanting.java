package justfatlard.disenchanting_lectern;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
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

		ItemEnchantments enchantments = held.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null || enchantments.isEmpty()) return InteractionResult.PASS;

		// An enchanted book's own enchantments live in STORED_ENCHANTMENTS rather than
		// ENCHANTMENTS, so one held against a lectern reads as unenchanted here and falls through.
		// That is the right answer anyway: there is nothing to move a book's enchantments into
		// except another book.

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
		consumeBlankBook(player, level, pos, state, lectern);
		popOff(level, pos, bookOf(moved));

		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.ENCHANT,
			pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.5);

		return InteractionResult.SUCCESS;
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
		if (keeping.isEmpty()) {
			held.remove(DataComponents.ENCHANTMENTS);
		} else {
			held.set(DataComponents.ENCHANTMENTS, keeping);
		}

		// The prior-work penalty is a tax on the enchanting that just left. Carrying it on an item
		// that is now bare would make a stripped tool quietly more expensive to work than a fresh
		// one, which is the opposite of what stripping it was for. The grindstone clears it too.
		held.remove(DataComponents.REPAIR_COST);
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
