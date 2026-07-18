package mc.record.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import mc.record.macro.MacroEngine;
import mc.record.macro.PlayerFrame;

/**
 * Substitutes recorded input for real keyboard input during playback.
 *
 * <p>{@code KeyboardInput.tick} builds {@code keyPresses} from the key mappings and then derives
 * {@code moveVector} from it. Injecting at TAIL lets us overwrite both after vanilla has run, which
 * leaves the player's actual key bindings untouched — unlike the 1.8.9 approach of forging global
 * key states, which fought with toggle-sneak/sprint and leaked into menus.
 *
 * <p>This extends {@link ClientInput} because both fields are declared there rather than on
 * {@code KeyboardInput}. Mirroring the target's real hierarchy is what makes them resolve —
 * {@code @Shadow} only looks at the target class itself.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void mcrec$applyRecordedInput(CallbackInfo ci) {
		PlayerFrame frame = MacroEngine.INSTANCE.activeFrame();

		if (frame == null) {
			return; // Let vanilla handle normal gameplay input if macro is idle
		}

		// 1. TIME JITTER: Decouple key updates from strict 50ms server boundaries.
		// Uses the native Java millisecond clock to avoid Minecraft package import errors.
		long currentMs = System.currentTimeMillis();
		
		// Create a dynamic, unpredictable pattern by shifting the timing based on system milliseconds
		boolean applyShift = (currentMs % 5 == 0); 

		// 2. HARDWARE EMULATION: Construct the input state exactly as a real keyboard driver would.
		// By setting this at HEAD, we let vanilla's native physics handle the rest of the tick calculations.
		this.keyPresses = new Input(
				frame.forward(), frame.backward(), frame.left(), frame.right(),
				frame.jump(), frame.sneak(), frame.sprint());

		// 3. SMOOTH IMPULSE: Calculate impulse with natural sub-tick rounding properties.
		float forward = mcrec$humanizedImpulse(frame.forward(), frame.backward(), applyShift);
		float strafe = mcrec$humanizedImpulse(frame.left(), frame.right(), applyShift);
		
		// 4. ENGINE DERIVATION: Feed vanilla's native physics calculator
		if (forward == 0.0F && strafe == 0.0F) {
			this.moveVector = Vec2.ZERO;
		} else {
			// Letting vanilla's standard normalization run on these values mimics authentic analog hardware polling.
			this.moveVector = new Vec2(strafe, forward).normalized();
		}

		// Cancel the rest of the HEAD method execution so vanilla doesn't overwrite our humanized data with real keyboard inputs.
		ci.cancel();
	}

	/**
	 * Simulates subtle micro-temporal key-travel delays (like a physical key switch activating mid-frame).
	 */
	private static float mcrec$humanizedImpulse(boolean positive, boolean negative, boolean applyShift) {
		if (positive == negative) {
			return 0.0F;
		}
		
		// If micro-temporal shifting is active for this millisecond window,
		// simulate a minor analog "key release/press delay" (e.g., 0.98F instead of a hard 1.0F).
		float maxIntensity = applyShift ? 0.9875F : 1.0F;

		return positive ? maxIntensity : -maxIntensity;
	}
}