package kz.minekz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import kz.minekz.engine.ChallengeEngine;
import kz.minekz.engine.Effect;
import kz.minekz.engine.QuestEngine;

/** One online player's engines and inventory baseline: made on their first tick, saved and dropped when they leave. Server thread only. */
final class PlayerProgress {
	final ChallengeEngine engine = new ChallengeEngine(MineKz.WORDS, MineKz.ANIMALS, new Random());
	final QuestEngine quests = new QuestEngine(MineKz.words(), new Random());
	/** Completed Quests whose title hasn't appeared yet; each title paid its Reward in order. */
	final Queue<Effect.QuestCompleted> rewardsDue = new ArrayDeque<>();
	final InventoryWatcher watcher = new InventoryWatcher();
	/** Effects of the player's packets since the last tick, handed to the tick's inventory diff. */
	final List<Effect> speechEffects = new ArrayList<>();
	boolean speechFailed;
	Packets.ChallengeSync sent = Packets.ChallengeSync.NONE;
	Packets.QuestSync sentQuest = Packets.QuestSync.NONE;
	Packets.Progress sentProgress = Packets.Progress.NONE;
	Packets.Locks sentLocks = Packets.Locks.NONE;
	Packets.Leaderboard sentLeaderboard = Packets.Leaderboard.NONE;
	final String name;

	PlayerProgress(String name) {
		this.name = name;
	}
}
