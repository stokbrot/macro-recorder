package mc.record.macro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import mc.record.Mcrec;

/**
 * Records and replays player input.
 *
 * <p>Playback works by handing the current frame to {@code KeyboardInputMixin}, which substitutes it
 * for the real keyboard state inside {@code KeyboardInput.tick}. Attack and use are not carried by
 * {@code Input}, so those two are driven through {@link KeyMapping#setDown} instead.
 *
 * <p>Recorded positions are never written back to the player — the client is not authoritative over
 * position, so forcing it would just get corrected by the server. Positions exist only so the
 * failsafe can tell whether playback has drifted away from the path that was recorded.
 */
public final class MacroEngine {
	public static final MacroEngine INSTANCE = new MacroEngine();

	public enum State { IDLE, RECORDING, PLAYING }

	/** Stop playback if the player is further than this (blocks) from the recorded path. */
	public static double failsafeDistance = 3.0;
	/** How many frames either side of the current one count as "on the path". */
	public static int failsafeWindow = 4;
	public static boolean failsafeEnabled = true;
	public static boolean loop = true;
	/** Smooth the *rendered* view rotation between recorded frames. Purely visual. */
	public static boolean smoothLook = true;

	private State state = State.IDLE;
	private List<PlayerFrame> frames = new ArrayList<>();
	private String loadedName = "";
	private int index;


	// Add these two fields at the class level of your MacroEngine if they aren't there yet:
	private long lastFrameTime = 0;
	private int driftTicks = 0;

	/** The frame the mixin should apply this tick, or null when playback is not driving input. */
	private PlayerFrame activeFrame;

	/** Index of the frame whose rotation is currently in {@code yRot}; -1 when not playing. */
	private int lastAppliedIndex = -1;

	private long runStartMs;
	private long lastRunMs;

	private MacroEngine() {
	}

	public State state() {
		return state;
	}

	public int frameCount() {
		return frames.size();
	}

	public int index() {
		return index;
	}

	public String loadedName() {
		return loadedName;
	}

	public PlayerFrame activeFrame() {
		return activeFrame;
	}

	/** Milliseconds elapsed in the current run, or the duration of the last completed run. */
	public long elapsedMs() {
		return state == State.IDLE ? lastRunMs : System.currentTimeMillis() - runStartMs;
	}

	public void toggleRecording(Minecraft mc) {
		if (state == State.PLAYING) {
			stop(mc, null);
		}

		if (state == State.RECORDING) {
			state = State.IDLE;
			lastRunMs = System.currentTimeMillis() - runStartMs;
			tell(mc, ChatFormatting.GRAY + "Recorded " + frames.size() + " frames ("
					+ formatDuration(lastRunMs) + "). Save it with /mrec.");
		} else {
			frames = new ArrayList<>();
			loadedName = "";
			index = 0;
			runStartMs = System.currentTimeMillis();
			state = State.RECORDING;
			tell(mc, ChatFormatting.RED + "Recording started.");
		}
	}

	public void togglePlayback(Minecraft mc) {
		if (state == State.RECORDING) {
			toggleRecording(mc);
		}

		if (state == State.PLAYING) {
			stop(mc, ChatFormatting.GRAY + "Playback stopped.");
			return;
		}

		if (frames.isEmpty()) {
			tell(mc, ChatFormatting.RED + "No macro loaded. Record one, or load one with /mrec.");
			return;
		}

		index = 0;
		runStartMs = System.currentTimeMillis();
		state = State.PLAYING;
		tell(mc, ChatFormatting.GREEN + "Playing " + frames.size() + " frames.");
	}

	/** Halts playback and releases every input this mod is holding down. */
	public void stop(Minecraft mc, String message) {
		if (state == State.PLAYING) {
			lastRunMs = System.currentTimeMillis() - runStartMs;
		}

		state = State.IDLE;
		activeFrame = null;
		lastAppliedIndex = -1;
		releaseHeldKeys(mc);

		if (message != null) {
			tell(mc, message);
		}
	}

	public void onEndClientTick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null) {
			// Left the world mid-run; drop everything so we don't resume into a different place.
			if (state != State.IDLE) {
				state = State.IDLE;
				activeFrame = null;
			}

			return;
		}

		switch (state) {
			case RECORDING -> recordFrame(mc, player);
			case PLAYING -> playFrame(mc, player);
			case IDLE -> activeFrame = null;
		}
	}

	private void recordFrame(Minecraft mc, LocalPlayer player) {
		frames.add(new PlayerFrame(
				player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot(),
				player.getInventory().getSelectedSlot(),
				mc.options.keyUp.isDown(),
				mc.options.keyDown.isDown(),
				mc.options.keyLeft.isDown(),
				mc.options.keyRight.isDown(),
				mc.options.keyJump.isDown(),
				mc.options.keyShift.isDown(),
				mc.options.keySprint.isDown(),
				mc.options.keyAttack.isDown(),
				mc.options.keyUse.isDown()));
	}

	private void playFrame(Minecraft mc, LocalPlayer player) {
		if (index >= frames.size()) {
			if (!loop) {
				stop(mc, ChatFormatting.GRAY + "Macro finished.");
				return;
			}
			index = 0;
		}

		// 1. MICRO-TEMPORAL SHIFTING: Break the rigid 50ms delivery rhythm
		long now = System.currentTimeMillis();
		if (lastFrameTime != 0) {
			long elapsed = now - lastFrameTime;
			// Real human network packets fluctuate. Occasionally delay a frame execution
			// by 1-3 milliseconds to mimic organic thread scheduling and network jitter.
			long targetInterval = 50 + (java.util.concurrent.ThreadLocalRandom.current().nextInt(3) - 1); // 49ms to 51ms
			if (elapsed < targetInterval) {
				return; // Skip this client tick cycle and catch up on the next one
			}
		}
		lastFrameTime = now;

		PlayerFrame frame = frames.get(index);

		// 2. HUMANIZED FAILSAFE RECOVERY: Avoid instant biological "dead silence"
		if (failsafeEnabled && hasDriftedOffPath(player)) {
			driftTicks++;
			// Give it a 3-tick window (150ms). Humans take time to notice a lag-back.
			// This prevents false positives from server lag and looks organic if you stop.
			if (driftTicks >= 3) {
				mc.options.keyAttack.setDown(false);
				mc.options.keyUse.setDown(false);
				activeFrame = null; 
				driftTicks = 0; // Reset
				
				stop(mc, ChatFormatting.RED + "Failsafe: you're "
						+ String.format(java.util.Locale.ROOT, "%.1f", frame.distanceTo(player.getX(), player.getY(), player.getZ()))
						+ " blocks off the recorded path. Stopped at frame " + index + ".");
				return;
			}
		} else {
			driftTicks = 0; // Reset if we are back on track
		}

		// ROTATION JITTER: Adds unique float variations to break statistical signature matching
		double jitterY = java.util.concurrent.ThreadLocalRandom.current().nextGaussian() * 0.00003;
		double jitterX = java.util.concurrent.ThreadLocalRandom.current().nextGaussian() * 0.00003;

		player.setYRot((float) (frame.yaw() + jitterY));
		player.setXRot((float) (frame.pitch() + jitterX));
		player.setYHeadRot((float) (frame.yaw() + jitterY));

		// INVENTORY: Safe client-driven hotbar switching
		if (frame.slot() >= 0 && frame.slot() < 9) {
			player.getInventory().setSelectedSlot(frame.slot());
		}

		// INTERACTIONS: Set use/attack triggers
		mc.options.keyAttack.setDown(frame.attack());
		mc.options.keyUse.setDown(frame.use());

		// PLUMBING: Movement state passed to your KeyboardInputMixin
		activeFrame = frame;
		lastAppliedIndex = index;
		index++;
	}



	/**
	 * True when the player is not within {@link #failsafeDistance} of any recorded position in a
	 * window around the current frame. The window absorbs normal timing jitter — a genuine problem
	 * (knocked off course, boat gone, stuck on a block) drifts away from all of them at once.
	 */
	private boolean hasDriftedOffPath(LocalPlayer player) {
		int from = Math.max(0, index - failsafeWindow);
		int to = Math.min(frames.size() - 1, index + failsafeWindow);

		for (int i = from; i <= to; i++) {
			if (frames.get(i).distanceTo(player.getX(), player.getY(), player.getZ()) < failsafeDistance) {
				return false;
			}
		}

		return true;
	}

	private void releaseHeldKeys(Minecraft mc) {
		if (mc.options != null) {
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
		}
	}

	public void loadInto(Minecraft mc, String name) {
		try {
			List<PlayerFrame> loaded = MacroStorage.load(name);

			if (loaded.isEmpty()) {
				tell(mc, ChatFormatting.RED + "'" + name + "' has no usable frames.");
				return;
			}

			stop(mc, null);
			frames = loaded;
			loadedName = name;
			index = 0;
			tell(mc, ChatFormatting.GREEN + "Loaded '" + name + "' (" + loaded.size() + " frames).");
		} catch (IOException | IllegalArgumentException e) {
			Mcrec.LOGGER.error("Could not load macro '{}'", name, e);
			tell(mc, ChatFormatting.RED + "Could not load '" + name + "': " + e.getMessage());
		}
	}

	public void saveCurrent(Minecraft mc, String name) {
		if (frames.isEmpty()) {
			tell(mc, ChatFormatting.RED + "Nothing to save — record something first.");
			return;
		}

		try {
			MacroStorage.save(name, frames);
			loadedName = name;
			tell(mc, ChatFormatting.GREEN + "Saved '" + name + "' (" + frames.size() + " frames).");
		} catch (IOException | IllegalArgumentException e) {
			Mcrec.LOGGER.error("Could not save macro '{}'", name, e);
			tell(mc, ChatFormatting.RED + "Could not save '" + name + "': " + e.getMessage());
		}
	}

	public List<PlayerFrame> framesView() {
		return Collections.unmodifiableList(frames);
	}

	// ---- Render-time view smoothing -------------------------------------------------------------
	//
	// A recording holds 20 rotation samples per second, and vanilla lerps between them linearly. That
	// is continuous in value but not in direction, so every tick boundary is a visible corner — which
	// is what makes replayed looking feel snappy next to live mouse input (which updates per frame).
	//
	// These fit a Catmull-Rom spline through the four frames around the current one. The curve passes
	// exactly through each recorded sample at the tick boundaries, so getYRot() — the value physics
	// and the server see — is untouched. Only what the camera draws in between changes.

	public boolean isSmoothingLook() {
		return state == State.PLAYING && smoothLook && lastAppliedIndex >= 0 && frames.size() >= 4;
	}

	public float smoothedYaw(float partialTick) {
		// The segment being rendered runs from the previously applied frame to the current one.
		float p1 = frameAt(lastAppliedIndex - 1).yaw();
		float p2 = frameAt(lastAppliedIndex).yaw();

		// Work in offsets from p1 so the spline is immune to the ±180 wrap.
		float u0 = -Mth.wrapDegrees(p1 - frameAt(lastAppliedIndex - 2).yaw());
		float u2 = Mth.wrapDegrees(p2 - p1);
		float u3 = u2 + Mth.wrapDegrees(frameAt(lastAppliedIndex + 1).yaw() - p2);

		return p1 + catmullRom(u0, 0.0F, u2, u3, partialTick);
	}

	public float smoothedPitch(float partialTick) {
		float value = catmullRom(
				frameAt(lastAppliedIndex - 2).pitch(),
				frameAt(lastAppliedIndex - 1).pitch(),
				frameAt(lastAppliedIndex).pitch(),
				frameAt(lastAppliedIndex + 1).pitch(),
				partialTick);

		// The spline can overshoot slightly; pitch outside this range is invalid.
		return Mth.clamp(value, -90.0F, 90.0F);
	}

	/** Neighbour lookup that wraps at the loop seam so the transition back to frame 0 is smooth too. */
	private PlayerFrame frameAt(int i) {
		int n = frames.size();

		if (loop) {
			return frames.get(Math.floorMod(i, n));
		}

		return frames.get(Mth.clamp(i, 0, n - 1));
	}

	private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
		return 0.5F * (2.0F * p1
				+ (p2 - p0) * t
				+ (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
				+ (3.0F * p1 - p0 - 3.0F * p2 + p3) * t * t * t);
	}

	public static String formatDuration(long ms) {
		long total = ms / 1000L;
		long hours = total / 3600L;

		// AFK runs go long, so grow to h:mm:ss rather than showing "184:07".
		if (hours > 0) {
			return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, (total % 3600L) / 60L, total % 60L);
		}

		return String.format(java.util.Locale.ROOT, "%d:%02d", total / 60L, total % 60L);
	}

	private static void tell(Minecraft mc, String message) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(message));
		}
	}
}
