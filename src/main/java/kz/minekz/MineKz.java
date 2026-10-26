package kz.minekz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kz.minekz.engine.Effect;
import kz.minekz.engine.ChallengeEngine;
import kz.minekz.engine.Quest;
import kz.minekz.engine.Standing;
import kz.minekz.engine.Tier;
import kz.minekz.engine.Word;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

public class MineKz implements ModInitializer {
	public static final String MOD_ID = "minekz";

	/** Each Word's iconic item for the HUD and Archive: the Word List's "icon", else its first item. Filled by loadWordList. */
	private static final Map<Word, String> ICONS = new LinkedHashMap<>();
	public static final Map<String, Word> WORDS = loadWordList("items"), ANIMALS = loadWordList("animals");
	private static final Map<String, Word> BY_CYRILLIC = words().stream().collect(Collectors.toMap(Word::cyrillic, w -> w));
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Online players by UUID; server thread only. */
	private static final Map<UUID, PlayerProgress> players = new HashMap<>();

	public static List<String> decoys() {
		try (var in = MineKz.class.getResourceAsStream("/data/minekz/decoys.txt")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read decoys.txt", e);
		}
	}

	public static Collection<Word> words() {
		Collection<Word> words = new LinkedHashSet<>(WORDS.values()); // one entry per Word, not per item
		words.addAll(ANIMALS.values());
		return words;
	}

	/** The item id (or, for an animal Word, the animal type) that pictures {@code word}, e.g. oak log for every log. */
	public static String icon(Word word) {
		return ICONS.get(word);
	}

	/** The Word whose cyrillic this is, or null. */
	public static Word word(String cyrillic) {
		return BY_CYRILLIC.get(cyrillic);
	}

	/** Made on the player's first tick only, so a packet arriving after they leave can't leave a stray entry behind. */
	private static PlayerProgress tickProgress(ServerPlayer player) {
		return players.computeIfAbsent(player.getUUID(), uuid -> new PlayerProgress(player.nameAndId().name()));
	}

	/** How many of {@code itemId} are Locked for this player; 0 before their first tick. Server thread only. */
	public static int lockedCount(ServerPlayer player, String itemId) {
		PlayerProgress progress = players.get(player.getUUID());
		return progress == null ? 0 : progress.engine.lockedCount(itemId);
	}

	/** Called as the player dies, before the inventory drops, so Forfeited items vanish instead of dropping. */
	public static void playerDied(ServerPlayer player) {
		PlayerProgress progress = players.get(player.getUUID());
		if (progress != null) {
			progress.watcher.apply(player, progress, progress.engine.died());
		}
	}

	private static void save(MinecraftServer server, UUID uuid, PlayerProgress progress) {
		if (progress.watcher.restored()) { // until then the engine is empty, so a save during loading must not overwrite the player's
			SavedProgress.of(server).save(uuid, progress.name, progress.engine, progress.quests);
		}
	}

	/** Online players live from their engines, the rest from the save. */
	private static Packets.Leaderboard leaderboard(MinecraftServer server) {
		List<Standing> standings = new ArrayList<>(SavedProgress.of(server).offline(players.keySet()));
		players.forEach((uuid, p) -> standings.add(Standing.of(uuid, p.name, p.engine.learnedWords(), p.engine.learnedAt(), ANIMALS.values(), true)));
		standings.sort(Standing.ORDER);
		return new Packets.Leaderboard(standings);
	}

	@Override
	public void onInitialize() {
		Packets.register();
		LockedItems.register();
		// dev games have made-up offline accounts, so a LAN guest (runClient2) could never pass Mojang's login check
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> server.setUsesAuthentication(false));
		}
		// a closed world must not leak its Challenges or inventory baselines into the next one
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			players.clear();
		});
		// a client that didn't announce MineKz's packets can't show Challenges, so it is turned away
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!ServerPlayNetworking.canSend(handler.player, Packets.ChallengeSync.TYPE)) {
				handler.disconnect(net.minecraft.network.chat.Component.literal("MineKz required: this world teaches Kazakh through a mod. Install Fabric for Minecraft 1.21.11 with Fabric API and the MineKz mod, then rejoin."));
			}
		});
		// the leaver's state freezes in the save; rejoining restores it with fresh Challenge Windows, like reopening a world
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PlayerProgress progress = players.remove(handler.player.getUUID());
			if (progress != null) {
				save(server, handler.player.getUUID(), progress);
			}
		});
		// packets before the player's first tick are dropped: no Challenge can be running yet
		ServerPlayNetworking.registerGlobalReceiver(Packets.Heard.TYPE, (packet, context) -> {
			PlayerProgress progress = players.get(context.player().getUUID());
			if (progress != null) {
				progress.speechEffects.addAll(progress.engine.wordHeard(packet.text()));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Packets.ArchiveSample.TYPE, (packet, context) -> {
			PlayerProgress progress = players.get(context.player().getUUID());
			if (progress != null) {
				progress.speechEffects.addAll(progress.engine.archiveSamplePressed(packet.word()));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Packets.ClientEvent.TYPE, (packet, context) -> {
			PlayerProgress progress = players.get(context.player().getUUID());
			if (progress == null) {
				return;
			}
			switch (packet.kind()) {
				case REPLAY -> progress.speechEffects.addAll(progress.engine.replayPressed());
				case LISTEN -> progress.quests.current().filter(Quest::byEar)
						.ifPresent(quest -> progress.speechEffects.addAll(progress.engine.samplePressed(quest.word())));
				case SPEECH_FAILED -> progress.speechFailed = true;
				case SAMPLE_FINISHED -> progress.speechEffects.addAll(progress.engine.sampleFinished());
				case TITLE_SHOWN -> {
					Effect.QuestCompleted reward = progress.rewardsDue.poll();
					if (reward != null) {
						InventoryWatcher.giveReward(context.player(), reward);
					}
				}
			}
		});
		// a player can't hurt an animal (by any means: getEntity is an arrow's shooter) until they've said its Word
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			boolean tamed = entity instanceof AbstractHorse horse && horse.isTamed() // horses, donkeys, mules, llamas
					|| entity instanceof TamableAnimal pet && pet.isTame(); // wolves, cats, parrots
			PlayerProgress progress = source.getEntity() instanceof ServerPlayer p ? players.get(p.getUUID()) : null;
			if (progress == null || tamed || !progress.engine.animalProtected(type)) {
				return true;
			}
			entity.clearFire(); // a Flame arrow ignites before it hurts, and the burning has no attacker to cancel
			progress.watcher.apply((ServerPlayer) source.getEntity(), progress, progress.engine.animalHurt(type));
			return false;
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> players.forEach((uuid, progress) -> save(server, uuid, progress)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerProgress progress = tickProgress(player);
				ChallengeEngine engine = progress.engine;
				List<Effect> effects = new ArrayList<>(progress.speechEffects);
				progress.speechEffects.clear();
				if (progress.speechFailed) {
					effects.addAll(engine.speechUnavailable());
				}
				progress.watcher.tick(player, progress, effects);
				Packets.ChallengeSync sync = new Packets.ChallengeSync(engine.active(), engine.micOn(), engine.lastHeard());
				if (!sync.equals(progress.sent)) {
					Packets.send(player, sync);
					progress.sent = sync;
				}
				Packets.QuestSync quest = new Packets.QuestSync(progress.quests.current());
				if (!quest.equals(progress.sentQuest)) {
					Packets.send(player, quest);
					progress.sentQuest = quest;
				}
				Packets.Progress learned = new Packets.Progress(engine.learnedWords(), engine.learnedAt(), progress.quests.completedWords());
				if (!learned.equals(progress.sentProgress)) {
					Packets.send(player, learned);
					progress.sentProgress = learned;
				}
				Packets.Locks locks = new Packets.Locks(engine.lockedCounts());
				if (!locks.equals(progress.sentLocks)) {
					Packets.send(player, locks);
					progress.sentLocks = locks;
				}
			}
			Packets.Leaderboard board = leaderboard(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerProgress progress = players.get(player.getUUID());
				if (!board.equals(progress.sentLeaderboard)) {
					Packets.send(player, board);
					progress.sentLeaderboard = board;
				}
			}
		});
	}

	/** {@code key} is "items" or "animals" (entity types); an entry has one or the other. */
	private static Map<String, Word> loadWordList(String key) {
		try (Reader in = new InputStreamReader(
				MineKz.class.getResourceAsStream("/data/minekz/word_list.json"), StandardCharsets.UTF_8)) {
			JsonArray entries = JsonParser.parseReader(in).getAsJsonArray();
			Map<String, Word> words = new LinkedHashMap<>();
			for (var entry : entries) {
				JsonObject o = entry.getAsJsonObject();
				if (!o.has(key)) {
					continue;
				}
				Word word = new Word(o.get("cyrillic").getAsString(), o.get("latin").getAsString(),
						o.get("meaning").getAsString(), o.get("sample").getAsString(),
						o.has("tier") ? Tier.valueOf(o.get("tier").getAsString().toUpperCase(Locale.ROOT)) : null,
						o.get("meaning_ru").getAsString());
				for (var item : o.getAsJsonArray(key)) {
					words.put(item.getAsString(), word);
				}
				ICONS.put(word, o.has("icon") ? o.get("icon").getAsString() : o.getAsJsonArray(key).get(0).getAsString());
			}
			return words;
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read word_list.json", e);
		}
	}
}
