package mc.record;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

/**
 * Shared entrypoint. Everything this mod does is client-side and lives in {@link McrecClient}.
 */
public class Mcrec implements ModInitializer {
	public static final String MOD_ID = "mcrec";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
