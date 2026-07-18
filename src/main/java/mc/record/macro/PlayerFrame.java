package mc.record.macro;

/**
 * One tick of recorded player state.
 *
 * <p>The movement booleans are ordered to match {@link net.minecraft.world.entity.player.Input},
 * so replaying them is a straight field-for-field copy. Attack and use are not part of that record
 * (vanilla drives them off the key mappings directly), so they are carried separately here.
 */
public record PlayerFrame(
		double x, double y, double z,
		float yaw, float pitch,
		int slot,
		boolean forward, boolean backward, boolean left, boolean right,
		boolean jump, boolean sneak, boolean sprint,
		boolean attack, boolean use
) {
	static final String HEADER = "# mcrec macro v1";

	String toCsv() {
		return String.format(java.util.Locale.ROOT,
				"%.6f,%.6f,%.6f,%.2f,%.2f,%d,%b,%b,%b,%b,%b,%b,%b,%b,%b",
				x, y, z, yaw, pitch, slot,
				forward, backward, left, right, jump, sneak, sprint, attack, use);
	}

	static PlayerFrame fromCsv(String line) {
		String[] p = line.split(",");

		if (p.length != 15) {
			throw new IllegalArgumentException("expected 15 fields, got " + p.length);
		}

		return new PlayerFrame(
				Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
				Float.parseFloat(p[3]), Float.parseFloat(p[4]),
				Integer.parseInt(p[5]),
				Boolean.parseBoolean(p[6]), Boolean.parseBoolean(p[7]),
				Boolean.parseBoolean(p[8]), Boolean.parseBoolean(p[9]),
				Boolean.parseBoolean(p[10]), Boolean.parseBoolean(p[11]),
				Boolean.parseBoolean(p[12]), Boolean.parseBoolean(p[13]),
				Boolean.parseBoolean(p[14]));
	}

	/**
	 * Parses a headerless line written by the 1.8.9 version of this mod, which used the field order
	 * forward,left,right,backward,jump,sprint,sneak,attack,use.
	 */
	static PlayerFrame fromLegacyCsv(String line) {
		String[] p = line.split(",");

		if (p.length != 15) {
			throw new IllegalArgumentException("expected 15 fields, got " + p.length);
		}

		return new PlayerFrame(
				Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
				Float.parseFloat(p[3]), Float.parseFloat(p[4]),
				Integer.parseInt(p[5]),
				Boolean.parseBoolean(p[6]),   // forward
				Boolean.parseBoolean(p[9]),   // backward
				Boolean.parseBoolean(p[7]),   // left
				Boolean.parseBoolean(p[8]),   // right
				Boolean.parseBoolean(p[10]),  // jump
				Boolean.parseBoolean(p[12]),  // sneak
				Boolean.parseBoolean(p[11]),  // sprint
				Boolean.parseBoolean(p[13]),  // attack
				Boolean.parseBoolean(p[14])); // use
	}

	public double distanceTo(double px, double py, double pz) {
		double dx = x - px;
		double dy = y - py;
		double dz = z - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
