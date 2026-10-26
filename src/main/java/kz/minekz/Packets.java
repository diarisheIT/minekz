package kz.minekz;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kz.minekz.engine.ActiveChallenge;
import kz.minekz.engine.Quest;
import kz.minekz.engine.Standing;
import kz.minekz.engine.Word;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** What the server and each player's client tell each other. Every player, the LAN host too, goes through these. */
public final class Packets {
	private Packets() {}

	/** A Word by its cyrillic; one the Word List doesn't have means mismatched mods, so the packet is refused. */
	private static final StreamCodec<ByteBuf, Word> WORD = ByteBufCodecs.STRING_UTF8.map(cyrillic -> {
		Word word = MineKz.word(cyrillic);
		if (word == null) {
			throw new DecoderException("Unknown Word " + cyrillic);
		}
		return word;
	}, Word::cyrillic);
	private static final StreamCodec<ByteBuf, ActiveChallenge> ACTIVE = StreamCodec.composite(
			WORD, ActiveChallenge::word, ByteBufCodecs.STRING_UTF8, ActiveChallenge::itemId, ByteBufCodecs.DOUBLE, ActiveChallenge::secondsLeft,
			ByteBufCodecs.BOOL, ActiveChallenge::recall, ByteBufCodecs.BOOL, ActiveChallenge::animal, ActiveChallenge::new);

	/** Server to client, whenever it changes: the player's own Challenge card, whether their mic should be on, and what it last heard. */
	public record ChallengeSync(Optional<ActiveChallenge> active, boolean micOn, String lastHeard) implements CustomPacketPayload {
		public static final ChallengeSync NONE = new ChallengeSync(Optional.empty(), false, "");
		public static final Type<ChallengeSync> TYPE = typeOf("challenge");
		static final StreamCodec<ByteBuf, ChallengeSync> CODEC = StreamCodec.composite(ByteBufCodecs.optional(ACTIVE), ChallengeSync::active,
				ByteBufCodecs.BOOL, ChallengeSync::micOn, ByteBufCodecs.STRING_UTF8, ChallengeSync::lastHeard, ChallengeSync::new);

		@Override
		public Type<ChallengeSync> type() {
			return TYPE;
		}
	}

	/** Server to client, whenever it changes: the player's own Learned Words, when each was learned (older ones have no time) and completed Quests, for their Archive. */
	public record Progress(Set<Word> learned, Map<Word, Long> learnedAt, Set<Word> completed) implements CustomPacketPayload {
		public static final Progress NONE = new Progress(Set.of(), Map.of(), Set.of());
		public static final Type<Progress> TYPE = typeOf("progress");
		private static final StreamCodec<ByteBuf, Set<Word>> WORDS = WORD.apply(ByteBufCodecs.collection(HashSet::new)).map(Set::copyOf, HashSet::new);
		static final StreamCodec<ByteBuf, Progress> CODEC = StreamCodec.composite(WORDS, Progress::learned,
				ByteBufCodecs.<ByteBuf, Word, Long, Map<Word, Long>>map(HashMap::new, WORD, ByteBufCodecs.VAR_LONG).map(Map::copyOf, HashMap::new), Progress::learnedAt,
				WORDS, Progress::completed, Progress::new);

		@Override
		public Type<Progress> type() {
			return TYPE;
		}
	}

	/** Server to client, whenever it changes: item id to how many of the player's own items are Locked, for the client's use-blocking checks. */
	public record Locks(java.util.Map<String, Integer> counts) implements CustomPacketPayload {
		public static final Locks NONE = new Locks(java.util.Map.of());
		public static final Type<Locks> TYPE = typeOf("locks");
		static final StreamCodec<ByteBuf, Locks> CODEC = ByteBufCodecs.<ByteBuf, String, Integer, java.util.Map<String, Integer>>map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
				.map(Locks::new, Locks::counts);

		@Override
		public Type<Locks> type() {
			return TYPE;
		}
	}

	/** Server to client: how a Challenge or Recall ended, for the HUD's moment; {@code learned} means said, {@code lost} is what a Forfeit actually removed. */
	public record Ending(Word word, boolean learned, int lost) implements CustomPacketPayload {
		public static final Type<Ending> TYPE = typeOf("ending");
		static final StreamCodec<ByteBuf, Ending> CODEC = StreamCodec.composite(WORD, Ending::word, ByteBufCodecs.BOOL, Ending::learned,
				ByteBufCodecs.VAR_INT, Ending::lost, Ending::new);

		@Override
		public Type<Ending> type() {
			return TYPE;
		}
	}

	/** Server to every player, whenever it changes: everyone with progress in this world, ranked. Counts only, never which Words. */
	public record Leaderboard(List<Standing> standings) implements CustomPacketPayload {
		public static final Leaderboard NONE = new Leaderboard(List.of());
		public static final Type<Leaderboard> TYPE = typeOf("leaderboard");
		static final StreamCodec<ByteBuf, Leaderboard> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Standing::id, ByteBufCodecs.STRING_UTF8, Standing::name,
				ByteBufCodecs.VAR_INT, Standing::items, ByteBufCodecs.VAR_INT, Standing::animals, ByteBufCodecs.VAR_LONG, Standing::reachedAt,
				ByteBufCodecs.BOOL, Standing::online, Standing::new).apply(ByteBufCodecs.list()).map(Leaderboard::new, Leaderboard::standings);

		@Override
		public Type<Leaderboard> type() {
			return TYPE;
		}
	}

	/** Server to client, whenever it changes (every tick while one runs): the player's own Quest panel. */
	public record QuestSync(Optional<Quest> quest) implements CustomPacketPayload {
		public static final QuestSync NONE = new QuestSync(Optional.empty());
		public static final Type<QuestSync> TYPE = typeOf("quest");
		static final StreamCodec<ByteBuf, QuestSync> CODEC = ByteBufCodecs.optional(StreamCodec.composite(WORD, Quest::word,
				ByteBufCodecs.BOOL, Quest::byEar, ByteBufCodecs.DOUBLE, Quest::secondsLeft, Quest::new)).map(QuestSync::new, QuestSync::quest);

		@Override
		public Type<QuestSync> type() {
			return TYPE;
		}
	}

	/** Server to client: a Quest ended, for the HUD's "Time's up" or its title; {@code completed} Quests are paid once the title has appeared. */
	public record QuestEnding(Word word, boolean completed) implements CustomPacketPayload {
		public static final Type<QuestEnding> TYPE = typeOf("quest_ending");
		static final StreamCodec<ByteBuf, QuestEnding> CODEC = StreamCodec.composite(WORD, QuestEnding::word, ByteBufCodecs.BOOL, QuestEnding::completed, QuestEnding::new);

		@Override
		public Type<QuestEnding> type() {
			return TYPE;
		}
	}

	/** Server to client: play this Word's Sample here, then answer {@link ClientEvent.Kind#SAMPLE_FINISHED}. */
	public record PlaySample(Word word) implements CustomPacketPayload {
		public static final Type<PlaySample> TYPE = typeOf("play_sample");
		static final StreamCodec<ByteBuf, PlaySample> CODEC = WORD.map(PlaySample::new, PlaySample::word);

		@Override
		public Type<PlaySample> type() {
			return TYPE;
		}
	}

	/** Client to server: a phrase the player's speech recogniser heard. */
	public record Heard(String text) implements CustomPacketPayload {
		public static final Type<Heard> TYPE = typeOf("heard");
		static final StreamCodec<ByteBuf, Heard> CODEC = ByteBufCodecs.stringUtf8(256).map(Heard::new, Heard::text);

		@Override
		public Type<Heard> type() {
			return TYPE;
		}
	}

	/** Client to server: the Sample button on an Archive card, which the server only honours for a Learned Word. */
	public record ArchiveSample(Word word) implements CustomPacketPayload {
		public static final Type<ArchiveSample> TYPE = typeOf("archive_sample");
		static final StreamCodec<ByteBuf, ArchiveSample> CODEC = WORD.map(ArchiveSample::new, ArchiveSample::word);

		@Override
		public Type<ArchiveSample> type() {
			return TYPE;
		}
	}

	/** Client to server: a key press, the player's speech recognition failing, their Sample ending or their Quest title appearing. */
	public record ClientEvent(Kind kind) implements CustomPacketPayload {
		public enum Kind { REPLAY, LISTEN, SPEECH_FAILED, SAMPLE_FINISHED, TITLE_SHOWN }

		public static final Type<ClientEvent> TYPE = typeOf("client_event");
		static final StreamCodec<ByteBuf, ClientEvent> CODEC = ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal)
				.map(ClientEvent::new, ClientEvent::kind);

		@Override
		public Type<ClientEvent> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.playS2C().register(ChallengeSync.TYPE, ChallengeSync.CODEC);
		PayloadTypeRegistry.playS2C().register(Progress.TYPE, Progress.CODEC);
		PayloadTypeRegistry.playS2C().register(Locks.TYPE, Locks.CODEC);
		PayloadTypeRegistry.playS2C().register(Ending.TYPE, Ending.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestSync.TYPE, QuestSync.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestEnding.TYPE, QuestEnding.CODEC);
		PayloadTypeRegistry.playS2C().register(PlaySample.TYPE, PlaySample.CODEC);
		PayloadTypeRegistry.playS2C().register(Leaderboard.TYPE, Leaderboard.CODEC);
		PayloadTypeRegistry.playC2S().register(ArchiveSample.TYPE, ArchiveSample.CODEC);
		PayloadTypeRegistry.playC2S().register(Heard.TYPE, Heard.CODEC);
		PayloadTypeRegistry.playC2S().register(ClientEvent.TYPE, ClientEvent.CODEC);
	}

	/** Skips players without MineKz, who can't receive it. */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MineKz.MOD_ID, path));
	}
}
