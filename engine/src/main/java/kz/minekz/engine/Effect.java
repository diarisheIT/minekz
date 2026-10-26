package kz.minekz.engine;

/** One-time things the Minecraft side must do, reported by the engine. */
public sealed interface Effect {
	/** The Challenge Window ended: remove up to {@code count} of {@code itemId} from the inventory. */
	record Forfeit(String itemId, int count) implements Effect {}

	/** The player said the active Word: celebrate it. The items are already unlocked. */
	record Learned(Word word) implements Effect {}

	/** Play the Word's pronunciation sample, then report {@link ChallengeEngine#sampleFinished()}. */
	record PlaySample(Word word) implements Effect {}

	/** The Recall's Word was said: give {@code xp} points, never diamonds. Its Sample plays too. */
	record RecallSaid(Word word, int xp) implements Effect {}

	/** The Recall ran out: the HUD shows the Word. Nothing is lost and it stays learned. Its Sample plays too. */
	record RecallMissed(Word word) implements Effect {}

	/** Speech recognition is unavailable: tell the player once that Challenges are off. */
	record ChallengesOff() implements Effect {}

	/** The Quest's Word was found: give the Reward, {@code xp} points plus {@code diamonds}. */
	record QuestCompleted(Word word, int xp, int diamonds) implements Effect {}

	/** The Quest's Time Limit ran out: no penalty, the HUD just shows it. */
	record QuestTimedOut(Word word) implements Effect {}
}
