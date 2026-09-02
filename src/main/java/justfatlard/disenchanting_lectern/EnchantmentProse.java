package justfatlard.disenchanting_lectern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Plain-English lines saying what each enchantment actually does, loaded from data packs.
 *
 * <p>Minecraft has none of these. An enchantment's {@code description} field is its display name,
 * so the game can tell you an item has Fortune III and cannot tell you what Fortune does - which is
 * the one thing somebody reading an item at a lectern most wants to know.
 *
 * <p>They arrive as data rather than through a registration call, so a mod adds prose for its own
 * enchantment by shipping one small file and needs no dependency on this one, no reflection, and no
 * load-order arrangement. Data packs can do it too, and can overwrite lines they disagree with. The
 * vanilla set this mod ships is the first user of exactly that mechanism rather than a special case
 * beside it, which is the only way to know the extension path works.
 *
 * <p>The file for {@code namespace:name} is
 * {@code data/<namespace>/enchantment_descriptions/<name>.json}, mirroring where the enchantment
 * itself lives:
 *
 * <pre>{@code { "description": "Mine blocks faster." } }</pre>
 *
 * <p>Plain text rather than a translatable component on purpose: this is a server-side mod serving
 * vanilla clients, and a translation key the client has never heard of renders as the key.
 */
public final class EnchantmentProse implements SimpleSynchronousResourceReloadListener {

	private static final String DIRECTORY = "enchantment_descriptions";
	private static final String SUFFIX = ".json";
	private static final Gson GSON = new Gson();

	/** Replaced wholesale on reload, so a read never sees a half-built map. */
	private static volatile Map<Identifier, String> prose = Map.of();

	/** The line for this enchantment, or null if nobody has written one. */
	public static String forEnchantment(Holder<Enchantment> holder) {
		Identifier id = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
		return id == null ? null : prose.get(id);
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath(Main.MOD_ID, DIRECTORY);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, String> loaded = new HashMap<>();

		// One resource per id, already resolved across packs, so a data pack later in the order
		// silently replaces a line rather than colliding with it.
		for (Map.Entry<Identifier, Resource> entry
				: manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier file = entry.getKey();
			Identifier enchantment = enchantmentOf(file);
			if (enchantment == null) continue;

			try (BufferedReader reader = entry.getValue().openAsReader()) {
				JsonObject json = GSON.fromJson(reader, JsonObject.class);
				if (json == null || !json.has("description")) continue;

				String text = json.get("description").getAsString();
				if (!text.isBlank()) loaded.put(enchantment, text);
			} catch (Exception e) {
				// One malformed file is not worth losing the other forty-two over.
				Main.LOGGER.warn("Could not read enchantment description {}", file, e);
			}
		}

		prose = Map.copyOf(loaded);
		Main.LOGGER.info("Loaded {} enchantment descriptions", prose.size());
	}

	/** Turns {@code ns:enchantment_descriptions/name.json} back into {@code ns:name}. */
	private static Identifier enchantmentOf(Identifier file) {
		String path = file.getPath();
		int start = DIRECTORY.length() + 1;
		if (path.length() <= start + SUFFIX.length()) return null;

		return Identifier.fromNamespaceAndPath(
			file.getNamespace(), path.substring(start, path.length() - SUFFIX.length()));
	}
}
