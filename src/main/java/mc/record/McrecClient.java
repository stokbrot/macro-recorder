package mc.record;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import mc.record.gui.MacroScreen;
import mc.record.macro.MacroEngine;

public class McrecClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Mcrec.id("macros"));

	private KeyMapping recordKey;
	private KeyMapping playKey;
	private KeyMapping menuKey;

	/** Set from a command callback; the screen must be opened from the tick loop, not mid-command. */
	private boolean openMenuRequested;

	@Override
	public void onInitializeClient() {
		registerKeyMappings();
		registerCommand();
		registerHud();

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
	}

	private void registerKeyMappings() {
		recordKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.mcrec.record", InputConstants.KEY_B, CATEGORY));
		playKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.mcrec.play", InputConstants.KEY_P, CATEGORY));
		menuKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.mcrec.menu", InputConstants.KEY_K, CATEGORY));
	}

	private void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
				dispatcher.register(ClientCommands.literal("mrec").executes(ctx -> {
					openMenuRequested = true;
					return 1;
				})));
	}

	private void registerHud() {
		HudElementRegistry.addLast(Mcrec.id("macro_status"), (g, tracker) -> {
			Minecraft mc = Minecraft.getInstance();

			// Vanilla already skips the whole element stack when the GUI is hidden.
			if (mc.player == null) {
				return;
			}

			g.text(mc.font, Component.literal(statusText()), hudX, hudY, 0xFFFFFFFF, true);
		});
	}

	private static int hudX = 6;
	private static int hudY = 6;

	private static String statusText() {
		MacroEngine engine = MacroEngine.INSTANCE;

		return switch (engine.state()) {
			case RECORDING -> ChatFormatting.RED + "● Recording " + MacroEngine.formatDuration(engine.elapsedMs())
					+ ChatFormatting.GRAY + "  " + engine.frameCount() + "f";
			// A single clock for the whole run: it keeps counting across loops and only stops when
			// playback does, so it reads as total time spent rather than progress through one pass.
			case PLAYING -> ChatFormatting.GREEN + "▶ " + MacroEngine.formatDuration(engine.elapsedMs());
			case IDLE -> engine.elapsedMs() > 0
					? ChatFormatting.DARK_GRAY + "Idle " + ChatFormatting.GRAY + "last "
							+ MacroEngine.formatDuration(engine.elapsedMs())
					: ChatFormatting.DARK_GRAY + "Idle";
		};
	}

	private void onEndClientTick(Minecraft mc) {
		if (openMenuRequested) {
			openMenuRequested = false;
			mc.setScreenAndShow(new MacroScreen());
		}

		while (menuKey.consumeClick()) {
			mc.setScreenAndShow(new MacroScreen());
		}

		while (recordKey.consumeClick()) {
			MacroEngine.INSTANCE.toggleRecording(mc);
		}

		while (playKey.consumeClick()) {
			MacroEngine.INSTANCE.togglePlayback(mc);
		}

		MacroEngine.INSTANCE.onEndClientTick(mc);
	}
}
