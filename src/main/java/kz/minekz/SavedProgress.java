package kz.minekz;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kz.minekz.engine.ChallengeEngine;
import kz.minekz.engine.Effect;
import kz.minekz.engine.Quest;
import kz.minekz.engine.QuestEngine;
import kz.minekz.engine.Standing;
import kz.minekz.engine.Word;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Each player's Learned Words, locked counts and Quests by UUID, kept in the world's save data so each world learns on its own. */
class SavedProgress extends SavedData {
	/** The current Quest, by the Word's cyrillic. */
	private record SavedQuest(String word, boolean byEar, double secondsLeft) {
		static final Codec<SavedQuest> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("word").forGetter(SavedQuest::word),
				Codec.BOOL.fieldOf("byEar").forGetter(SavedQuest::byEar),
				Codec.DOUBLE.fieldOf("secondsLeft").forGetter(SavedQuest::secondsLeft)
		).apply(i, SavedQuest::new));
	}

	/**
	 * One player's progress. {@code learned} and {@code completed} are Words by cyrillic, {@code locked} is item id and locked
	 * count in Challenge order, {@code learnedAt} is epoch millis by cyrillic (Words learned in older saves have none), {@code name}
	 * is the player's name when last saved, for the Leaderboard while they're offline.
	 */
	private record Entry(List<String> learned, List<Pair<String, Integer>> locked, Optional<SavedQuest> quest, List<String> completed,
			Map<String, Long> learnedAt, String name) {
		static final Entry EMPTY = new Entry(List.of(), List.of(), Optional.empty(), List.of(), Map.of(), "");
		// the same fields as the world-wide save before multiplayer (the optional ones came later), so an old save decodes as one Entry
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().fieldOf("learned").forGetter(Entry::learned),
				Codec.compoundList(Codec.STRING, Codec.INT).fieldOf("locked").forGetter(Entry::locked),
				SavedQuest.CODEC.optionalFieldOf("quest").forGetter(Entry::quest),
				Codec.STRING.listOf().optionalFieldOf("completed", List.of()).forGetter(Entry::completed),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("learnedAt", Map.of()).forGetter(Entry::learnedAt),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name)
		).apply(i, Entry::new));
	}

	private static final Codec<SavedProgress> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC)
			.fieldOf("players").codec().xmap(SavedProgress::new, p -> p.players);
	// a new file, so the world-wide "minekz" save of an older world stays on disk untouched for ticket 08 to migrate
	private static final SavedDataType<SavedProgress> TYPE =
			new SavedDataType<>(MineKz.MOD_ID + "_players", SavedProgress::new, CODEC, null); // no data fixer: the format is ours

	/** The world-wide save from before multiplayer; its progress goes to the first player who joins, then it is emptied. */
	private static class Legacy extends SavedData {
		static final SavedDataType<Legacy> TYPE = new SavedDataType<>(MineKz.MOD_ID, Legacy::new,
				Entry.CODEC.xmap(Legacy::new, l -> l.entry), null);

		Entry entry;

		Legacy() {
			this(Entry.EMPTY);
		}

		Legacy(Entry entry) {
			this.entry = entry;
		}
	}

	private final Map<UUID, Entry> players;
	private Legacy legacy;

	private SavedProgress() {
		this(Map.of());
	}

	private SavedProgress(Map<UUID, Entry> players) {
		this.players = new HashMap<>(players);
	}

	static SavedProgress of(MinecraftServer server) {
		SavedProgress progress = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		progress.legacy = server.overworld().getDataStorage().computeIfAbsent(Legacy.TYPE);
		return progress;
	}

	void save(UUID player, String name, ChallengeEngine engine, QuestEngine quests) {
		players.put(player, new Entry(
				engine.learnedWords().stream().map(Word::cyrillic).sorted().toList(),
				engine.lockedCounts().entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).toList(),
				quests.current().map(q -> new SavedQuest(q.word().cyrillic(), q.byEar(), q.secondsLeft())),
				quests.completedWords().stream().map(Word::cyrillic).sorted().toList(),
				engine.learnedAt().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().cyrillic(), Map.Entry::getValue)), name));
		setDirty();
	}

	List<Effect> restore(UUID player, ChallengeEngine engine, QuestEngine quests) {
		// nobody has saved yet, so this is the first player to join since the update: they inherit the old world-wide progress
		if (players.isEmpty() && !legacy.entry.equals(Entry.EMPTY)) {
			players.put(player, legacy.entry);
			legacy.entry = Entry.EMPTY;
			legacy.setDirty();
			setDirty();
		}
		Entry saved = players.getOrDefault(player, Entry.EMPTY);
		// Words dropped from the Word List (or no longer Tiered) since the save are skipped
		quests.rejoined(
				saved.quest().filter(q -> MineKz.word(q.word()) != null && MineKz.word(q.word()).tier() != null)
						.map(q -> new Quest(MineKz.word(q.word()), q.byEar(), q.secondsLeft())).orElse(null),
				saved.completed().stream().map(MineKz::word).filter(w -> w != null).toList());
		Map<String, Integer> counts = new LinkedHashMap<>();
		saved.locked().forEach(p -> counts.put(p.getFirst(), p.getSecond()));
		return engine.rejoined(learned(saved), times(saved), counts);
	}

	/** The Leaderboard standings of saved players other than {@code online}, who are listed live instead. */
	List<Standing> offline(Set<UUID> online) {
		return players.entrySet().stream().filter(e -> !online.contains(e.getKey()))
				// saves from before names were kept have none until that player rejoins
				.map(e -> Standing.of(e.getKey(), e.getValue().name().isEmpty() ? e.getKey().toString().substring(0, 8) : e.getValue().name(), learned(e.getValue()), times(e.getValue()), MineKz.ANIMALS.values(), false)).toList();
	}

	/** Words dropped from the Word List since the save are skipped. */
	private static List<Word> learned(Entry saved) {
		return saved.learned().stream().map(MineKz::word).filter(w -> w != null).toList();
	}

	private static Map<Word, Long> times(Entry saved) {
		Map<Word, Long> times = new LinkedHashMap<>();
		saved.learnedAt().forEach((cyrillic, at) -> {
			if (MineKz.word(cyrillic) != null) {
				times.put(MineKz.word(cyrillic), at);
			}
		});
		return times;
	}
}
