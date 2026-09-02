package justfatlard.disenchanting_lectern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What a lectern does when you offer it something enchanted.
 *
 * <p>Which of the two it is turns on whether the lectern already has a book, which is also the
 * difference between the two things a lectern is for. An empty one is a desk you put something on
 * to look at it, so it reads the enchantments out. One with a blank book is a desk set up for
 * copying, so it copies them onto the book.
 */
public final class LecternUse {
	private LecternUse() {}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
			BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof LecternBlock)) return InteractionResult.PASS;

		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) return InteractionResult.PASS;

		if (state.getValue(LecternBlock.HAS_BOOK)) {
			return Disenchanting.transfer(serverPlayer, serverLevel, pos, state, held);
		}
		return EnchantmentReadout.describe(serverPlayer, serverLevel, held);
	}
}
