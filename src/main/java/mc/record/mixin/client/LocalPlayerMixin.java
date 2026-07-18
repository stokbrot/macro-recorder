package mc.record.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;

import mc.record.macro.MacroEngine;

/**
 * Smooths the rendered view rotation during playback.
 *
 * <p>These two methods feed the camera and the view vector, not movement. The authoritative rotation
 * — {@code getYRot()}/{@code getXRot()}, which drives the travel direction and is what gets sent to
 * the server — is left exactly as recorded, so replay follows the same path either way.
 */

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
	private void mcrec$smoothViewYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
		MacroEngine engine = MacroEngine.INSTANCE;

		if (engine.isSmoothingLook()) {
			cir.setReturnValue(engine.smoothedYaw(partialTick));
		}
	}

	@Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
	private void mcrec$smoothViewPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
		MacroEngine engine = MacroEngine.INSTANCE;

		if (engine.isSmoothingLook()) {
			cir.setReturnValue(engine.smoothedPitch(partialTick));
		}
	}
}
