package mc.record.macro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

import mc.record.Mcrec;

/** Reads and writes {@code .macro} files in {@code <game dir>/macros}. */
public final class MacroStorage {
	private static final String EXT = ".macro";

	private MacroStorage() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("macros");
	}

	/** Macro names (without extension), alphabetical. Never null; empty on any IO problem. */
	public static List<String> list() {
		Path dir = dir();

		if (!Files.isDirectory(dir)) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().endsWith(EXT))
					.map(p -> {
						String n = p.getFileName().toString();
						return n.substring(0, n.length() - EXT.length());
					})
					.sorted(Comparator.naturalOrder())
					.toList();
		} catch (IOException e) {
			Mcrec.LOGGER.error("Could not list macros in {}", dir, e);
			return List.of();
		}
	}

	public static void save(String name, List<PlayerFrame> frames) throws IOException {
		Path dir = dir();
		Files.createDirectories(dir);

		List<String> lines = new ArrayList<>(frames.size() + 1);
		lines.add(PlayerFrame.HEADER);

		for (PlayerFrame f : frames) {
			lines.add(f.toCsv());
		}

		Files.write(resolve(dir, name), lines);
	}

	public static List<PlayerFrame> load(String name) throws IOException {
		List<String> lines = Files.readAllLines(resolve(dir(), name));
		// Files written by the 1.8.9 version have no header and use a different column order.
		boolean legacy = lines.isEmpty() || !lines.getFirst().startsWith(PlayerFrame.HEADER);
		List<PlayerFrame> frames = new ArrayList<>(lines.size());

		for (String line : lines) {
			String trimmed = line.trim();

			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}

			try {
				frames.add(legacy ? PlayerFrame.fromLegacyCsv(trimmed) : PlayerFrame.fromCsv(trimmed));
			} catch (IllegalArgumentException e) {
				// Skip the bad line rather than losing the whole macro.
				Mcrec.LOGGER.warn("Skipping malformed frame in macro '{}': {}", name, e.getMessage());
			}
		}

		if (legacy) {
			Mcrec.LOGGER.info("Loaded '{}' in legacy 1.8.9 format; re-save it to upgrade.", name);
		}

		return frames;
	}

	/** @return true if a file was actually deleted. */
	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(resolve(dir(), name));
		} catch (IOException e) {
			Mcrec.LOGGER.error("Could not delete macro '{}'", name, e);
			return false;
		}
	}

	/**
	 * Resolves a macro name to a path inside {@code dir}, rejecting anything that would escape it.
	 * Names come from a text field, so treat them as untrusted.
	 */
	private static Path resolve(Path dir, String name) {
		String clean = name.endsWith(EXT) ? name.substring(0, name.length() - EXT.length()) : name;

		if (clean.isBlank() || !clean.matches("[A-Za-z0-9 _.-]+") || clean.contains("..")) {
			throw new IllegalArgumentException("Invalid macro name: " + name);
		}

		return dir.resolve(clean + EXT);
	}
}
