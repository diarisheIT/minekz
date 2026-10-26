package kz.minekz.engine;

/** How rare a Word's items are, set by hand on the Word List. Sets a Quest's Time Limit and Reward. */
public enum Tier {
	COMMON(60, 120, 5, 0), UNCOMMON(25, 240, 15, 0), RARE(12, 480, 40, 0), LEGENDARY(3, 900, 100, 2);

	final int weight; // how often a Quest rolls this Tier, out of 100
	public final double timeLimitSeconds;
	public final int xp, diamonds;

	Tier(int weight, double timeLimitSeconds, int xp, int diamonds) {
		this.weight = weight;
		this.timeLimitSeconds = timeLimitSeconds;
		this.xp = xp;
		this.diamonds = diamonds;
	}
}
