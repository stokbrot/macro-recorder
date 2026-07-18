package mc.record.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import mc.record.macro.MacroEngine;
import mc.record.macro.MacroStorage;

/** Save / load / delete macros and tune the failsafe. Opened with {@code /mrec}. */
public class MacroScreen extends Screen {
	private static final int ROWS = 6;
	private static final int ROW_HEIGHT = 24;
	private static final int LIST_TOP = 92;

	private EditBox nameField;
	private List<String> macros = List.of();
	private int scroll;
	private String status = "";

	public MacroScreen() {
		super(Component.literal("Macro Recorder"));
	}

	@Override
	protected void init() {
		macros = MacroStorage.list();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, macros.size() - ROWS)));

		int centre = this.width / 2;

		String previous = this.nameField != null ? this.nameField.getValue() : MacroEngine.INSTANCE.loadedName();
		this.nameField = new EditBox(this.font, centre - 105, 56, 140, 20, Component.literal("Macro name"));
		this.nameField.setMaxLength(48);
		this.nameField.setValue(previous);
		addRenderableWidget(this.nameField);

		addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
			String name = this.nameField.getValue().trim();

			if (name.isEmpty()) {
				status = ChatFormatting.RED + "Enter a name first.";
				return;
			}

			MacroEngine.INSTANCE.saveCurrent(this.minecraft, name);
			status = "Saved " + name;
			rebuild();
		}).bounds(centre + 42, 56, 62, 20).build());

		buildList(centre);
		buildOptions();
	}

	private void buildList(int centre) {
		int visible = Math.min(ROWS, macros.size() - scroll);

		for (int i = 0; i < visible; i++) {
			String name = macros.get(scroll + i);
			int y = LIST_TOP + i * ROW_HEIGHT;

			addRenderableWidget(Button.builder(Component.literal("Load"), b -> {
				MacroEngine.INSTANCE.loadInto(this.minecraft, name);
				this.nameField.setValue(name);
				status = "Loaded " + name;
			}).bounds(centre + 4, y, 50, 20).build());

			addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
				status = MacroStorage.delete(name) ? "Deleted " + name : ChatFormatting.RED + "Could not delete " + name;
				rebuild();
			}).bounds(centre + 58, y, 50, 20).build());
		}
	}

	private void buildOptions() {
		int y = LIST_TOP + ROWS * ROW_HEIGHT + 12;
		int left = this.width / 2 - 105;

		addRenderableWidget(Button.builder(loopLabel(), b -> {
			MacroEngine.loop = !MacroEngine.loop;
			b.setMessage(loopLabel());
		}).bounds(left, y, 100, 20).build());

		addRenderableWidget(Button.builder(failsafeLabel(), b -> {
			MacroEngine.failsafeEnabled = !MacroEngine.failsafeEnabled;
			b.setMessage(failsafeLabel());
			status = MacroEngine.failsafeEnabled
					? "Failsafe on"
					: ChatFormatting.YELLOW + "Failsafe off — nothing will stop the macro if you drift.";
		}).bounds(left + 106, y, 110, 20).build());

		int y2 = y + 24;

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			MacroEngine.failsafeDistance = Math.max(0.5, MacroEngine.failsafeDistance - 0.5);
			status = "Failsafe distance " + MacroEngine.failsafeDistance;
		}).bounds(left, y2, 20, 20).build());

		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			MacroEngine.failsafeDistance = Math.min(64.0, MacroEngine.failsafeDistance + 0.5);
			status = "Failsafe distance " + MacroEngine.failsafeDistance;
		}).bounds(left + 80, y2, 20, 20).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			MacroEngine.failsafeWindow = Math.max(0, MacroEngine.failsafeWindow - 1);
			status = "Failsafe window " + MacroEngine.failsafeWindow;
		}).bounds(left + 106, y2, 20, 20).build());

		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			MacroEngine.failsafeWindow = Math.min(64, MacroEngine.failsafeWindow + 1);
			status = "Failsafe window " + MacroEngine.failsafeWindow;
		}).bounds(left + 190, y2, 20, 20).build());

		addRenderableWidget(Button.builder(smoothLabel(), b -> {
			MacroEngine.smoothLook = !MacroEngine.smoothLook;
			b.setMessage(smoothLabel());
			status = MacroEngine.smoothLook ? "Smooth look on" : "Smooth look off";
		}).bounds(left, y2 + 24, 130, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width / 2 - 50, y2 + 52, 100, 20).build());
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	private static Component loopLabel() {
		return Component.literal((MacroEngine.loop ? "[x]" : "[ ]") + " Loop");
	}

	private static Component failsafeLabel() {
		return Component.literal((MacroEngine.failsafeEnabled ? "[x]" : "[ ]") + " Failsafe");
	}

	private static Component smoothLabel() {
		return Component.literal((MacroEngine.smoothLook ? "[x]" : "[ ]") + " Smooth look");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		int centre = this.width / 2;
		g.centeredText(this.font, this.title, centre, 18, 0xFFFFFFFF);

		MacroEngine engine = MacroEngine.INSTANCE;
		String summary = switch (engine.state()) {
			case RECORDING -> ChatFormatting.RED + "Recording — " + engine.frameCount() + " frames";
			case PLAYING -> ChatFormatting.GREEN + "Playing — frame " + engine.index() + "/" + engine.frameCount();
			case IDLE -> ChatFormatting.GRAY + (engine.frameCount() == 0
					? "No macro in memory"
					: engine.frameCount() + " frames in memory");
		};
		g.centeredText(this.font, Component.literal(summary), centre, 34, 0xFFFFFFFF);

		int visible = Math.min(ROWS, macros.size() - scroll);

		for (int i = 0; i < visible; i++) {
			g.text(this.font, Component.literal(macros.get(scroll + i)),
					centre - 105, LIST_TOP + i * ROW_HEIGHT + 6, 0xFFAAAAAA);
		}

		if (macros.isEmpty()) {
			g.text(this.font, Component.literal(ChatFormatting.DARK_GRAY + "No saved macros yet"),
					centre - 105, LIST_TOP + 6, 0xFF888888);
		}

		drawScrollBar(g, centre);

		int y2 = LIST_TOP + ROWS * ROW_HEIGHT + 36;
		g.text(this.font, Component.literal(String.format(java.util.Locale.ROOT, "%.1fm", MacroEngine.failsafeDistance)),
				this.width / 2 - 105 + 26, y2 + 6, 0xFFFFFFFF);
		g.text(this.font, Component.literal("±" + MacroEngine.failsafeWindow + " frames"),
				this.width / 2 - 105 + 132, y2 + 6, 0xFFFFFFFF);

		if (!status.isEmpty()) {
			g.centeredText(this.font, Component.literal(status), centre, this.height - 18, 0xFF55FF55);
		}
	}

	private void drawScrollBar(GuiGraphicsExtractor g, int centre) {
		if (macros.size() <= ROWS) {
			return;
		}

		int trackHeight = ROWS * ROW_HEIGHT - 4;
		int x = centre + 112;
		int barHeight = Math.max(16, (int) ((float) ROWS / macros.size() * trackHeight));
		int maxScroll = macros.size() - ROWS;
		int barY = LIST_TOP + (int) ((float) scroll / maxScroll * (trackHeight - barHeight));

		g.fill(x, LIST_TOP, x + 4, LIST_TOP + trackHeight, 0xFF444444);
		g.fill(x, barY, x + 4, barY + barHeight, 0xFFAAAAAA);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int maxScroll = Math.max(0, macros.size() - ROWS);
		int updated = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical)));

		if (updated != scroll) {
			scroll = updated;
			rebuild();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
