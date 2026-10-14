package justfatlard.disenchanting_lectern.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A book of several enchantments split at a lectern: the one at the top of its tooltip onto the
 * blank book, the rest left on the one in hand; a book of one left alone; a cursed book refused;
 * and an enchanted sword still stripped as before.
 */
public final class BooksSplit implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			world.getServer().runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ServerLevel level = (ServerLevel) player.level();
				BlockPos pos = player.blockPosition().offset(2, 0, 0);

				// Three on one book: one comes off, two stay.
				ItemStack book = book(level, Enchantments.SHARPNESS, 5, Enchantments.MENDING, 1, Enchantments.UNBREAKING, 3);
				InteractionResult split = use(player, level, pos, book);
				check(split.consumesAction(), "the lectern did not split a book of three: " + split);
				ItemEnchantments left = book.get(DataComponents.STORED_ENCHANTMENTS);
				check(left != null && left.size() == 2, "the book in hand has " + (left == null ? 0 : left.size()) + " left, not two");
				List<ItemEntity> made = popped(level, pos);
				check(made.size() == 1, made.size() + " books popped off");
				ItemEnchantments off = made.get(0).getItem().get(DataComponents.STORED_ENCHANTMENTS);
				check(off != null && off.size() == 1, "the new book does not carry exactly one");
				Holder<Enchantment> taken = off.keySet().iterator().next();
				check(left.getLevel(taken) == 0, "the enchantment split off is still on the book in hand");
				check(!level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "the blank book was not spent");
				made.forEach(ItemEntity::discard);

				// Two more goes take it down to one, and then there is nothing left to split.
				check(use(player, level, pos, book).consumesAction(), "the second split was refused");
				check(book.get(DataComponents.STORED_ENCHANTMENTS).size() == 1, "two splits did not leave one");
				popped(level, pos).forEach(ItemEntity::discard);
				use(player, level, pos, book);
				check(level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "a book of one spent the blank book");
				check(popped(level, pos).isEmpty(), "a book of one was split");

				// A cursed book does not come apart.
				ItemStack cursed = book(level, Enchantments.MENDING, 1, Enchantments.BINDING_CURSE, 1, Enchantments.EFFICIENCY, 2);
				check(use(player, level, pos, cursed) == InteractionResult.FAIL, "a cursed book was split");
				check(cursed.get(DataComponents.STORED_ENCHANTMENTS).size() == 3, "the cursed book lost something");
				check(level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "a cursed book spent the blank book");

				// An item still comes clean.
				ItemStack sword = new ItemStack(Items.IRON_SWORD);
				ItemEnchantments.Mutable on = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				on.set(holder(level, Enchantments.SHARPNESS), 2);
				on.set(holder(level, Enchantments.LOOTING), 1);
				sword.set(DataComponents.ENCHANTMENTS, on.toImmutable());
				check(use(player, level, pos, sword).consumesAction(), "the sword was not stripped");
				check(sword.getEnchantments().isEmpty(), "the sword kept its enchantments");
				check(sword.isEnchantable(), "the stripped sword cannot go back on the enchanting table");
				List<ItemEntity> stripped = popped(level, pos);
				check(stripped.size() == 1 && stripped.get(0).getItem().get(DataComponents.STORED_ENCHANTMENTS).size() == 2,
					"stripping a sword did not make one book of both");
				stripped.forEach(ItemEntity::discard);

				// A sword an earlier version stripped, its enchantments taken away rather than emptied:
				// any lectern sets it right, and spends nothing doing it.
				ItemStack broken = new ItemStack(Items.IRON_SWORD);
				broken.remove(DataComponents.ENCHANTMENTS);
				check(!broken.isEnchantable(), "the test's broken sword is enchantable already");
				check(use(player, level, pos, broken).consumesAction(), "the lectern did not mend the broken sword");
				check(broken.isEnchantable(), "the mended sword still cannot be enchanted");
				check(level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "mending spent the blank book");
			});
		}
		context.waitTicks(10);
	}

	private static ItemStack book(ServerLevel level, Object... pairs) {
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (int i = 0; i < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
			stored.set(holder(level, key), (Integer) pairs[i + 1]);
		}
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		return book;
	}

	private static Holder<Enchantment> holder(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** A lectern here with a blank book on it, laid if it has none, and the stack used on it from the main hand. */
	private static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		if (!level.getBlockState(pos).is(Blocks.LECTERN) || !level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK)) {
			level.setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
			if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) lectern.setBook(new ItemStack(Items.WRITABLE_BOOK));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
	}

	private static List<ItemEntity> popped(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), item -> item.getItem().is(Items.ENCHANTED_BOOK));
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
