package justfatlard.disenchanting_lectern;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A lectern that takes enchantments off things.
 *
 * <p>Server-side and nothing else: no new block, no new item, no screen. A vanilla lectern holding
 * a vanilla book hands back a vanilla enchanted book, so a player needs nothing installed to use
 * one.
 */
public class Main implements ModInitializer {

	public static final String MOD_ID = "disenchanting-lectern-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		UseBlockCallback.EVENT.register(LecternUse::onUseBlock);

		// Server data rather than client resources: the prose is composed into chat messages here and
		// sent as finished text, so it has to be readable on a server whose players run vanilla.
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new EnchantmentProse());

		LOGGER.info("Disenchanting Lectern loaded - blank book on a lectern pulls enchantments off an item; empty lectern reads them out");
	}
}
