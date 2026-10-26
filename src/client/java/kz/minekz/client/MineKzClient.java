package kz.minekz.client;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import kz.minekz.LockedItems;
import kz.minekz.MineKz;
import kz.minekz.Packets;
import kz.minekz.engine.Effect;
import kz.minekz.engine.Word;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class MineKzClient implements ClientModInitializer {
	private static final KeyMapping REPLAY = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.minekz.replay", GLFW.GLFW_KEY_R, KeyMapping.Category.MISC));
	private static final KeyMapping LISTEN = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.minekz.listen", GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
	private static final KeyMapping ARCHIVE = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.minekz.archive", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));

	/** This player's Challenge as the server last sent it; read by the HUD and the speech thread. */
	static volatile Packets.ChallengeSync challenge = Packets.ChallengeSync.NONE;
	/** This player's Quest as the server last sent it; read by the HUD. */
	static volatile Packets.QuestSync quest = Packets.QuestSync.NONE;
	/** Words whose Quest timed out, for the HUD's "Time's up". */
	static final Queue<Word> questTimeouts = new ConcurrentLinkedQueue<>();
	/** Completed Quests, for the HUD's title. */
	static final Queue<Effect.QuestCompleted> questCompletions = new ConcurrentLinkedQueue<>();
	/** This player's Learned Words and completed Quests as the server last sent them; read by the Archive. */
	static volatile Packets.Progress progress = Packets.Progress.NONE;
	/** Everyone with progress in this world, ranked, as the server last sent it; read by the Archive. */
	static volatile Packets.Leaderboard leaderboard = Packets.Leaderboard.NONE;
	/** Endings from the server for the HUD to play. */
	static final Queue<Packets.Ending> endings = new ConcurrentLinkedQueue<>();
	/** Phrases heard by the speech thread, sent to the server on the client tick. */
	static final Queue<String> heard = new ConcurrentLinkedQueue<>();
	static volatile boolean speechFailed;
	private static boolean speechFailedSent; // per connection: each server needs telling

	@Override
	public void onInitializeClient() {
		SpeechListener.start();
		ClientPlayNetworking.registerGlobalReceiver(Packets.ChallengeSync.TYPE, (packet, context) -> challenge = packet);
		ClientPlayNetworking.registerGlobalReceiver(Packets.QuestSync.TYPE, (packet, context) -> quest = packet);
		ClientPlayNetworking.registerGlobalReceiver(Packets.QuestEnding.TYPE, (packet, context) -> {
			if (packet.completed()) {
				questCompletions.add(new Effect.QuestCompleted(packet.word(), packet.word().tier().xp, packet.word().tier().diamonds));
			} else {
				questTimeouts.add(packet.word());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.Progress.TYPE, (packet, context) -> progress = packet);
		ClientPlayNetworking.registerGlobalReceiver(Packets.Locks.TYPE, (packet, context) -> LockedItems.mine = packet.counts());
		ClientPlayNetworking.registerGlobalReceiver(Packets.Leaderboard.TYPE, (packet, context) -> leaderboard = packet);
		ClientPlayNetworking.registerGlobalReceiver(Packets.PlaySample.TYPE, (packet, context) -> {
			Minecraft client = context.client();
			// answered even when the clip can't play, because the server's Challenge Window waits for it
			SamplePlayer.play(packet.word(), () -> client.execute(() -> send(new Packets.ClientEvent(Packets.ClientEvent.Kind.SAMPLE_FINISHED))));
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.Ending.TYPE, (packet, context) -> endings.add(packet));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			challenge = Packets.ChallengeSync.NONE;
			quest = Packets.QuestSync.NONE;
			questTimeouts.clear();
			questCompletions.clear();
			leaderboard = Packets.Leaderboard.NONE;
			progress = Packets.Progress.NONE;
			LockedItems.mine = Map.of();
			endings.clear();
			heard.clear();
			speechFailedSent = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (String text; (text = heard.poll()) != null;) {
				send(new Packets.Heard(text));
			}
			if (speechFailed && !speechFailedSent && ClientPlayNetworking.canSend(Packets.ClientEvent.TYPE)) {
				send(new Packets.ClientEvent(Packets.ClientEvent.Kind.SPEECH_FAILED));
				speechFailedSent = true;
			}
			while (REPLAY.consumeClick()) {
				send(new Packets.ClientEvent(Packets.ClientEvent.Kind.REPLAY));
			}
			while (LISTEN.consumeClick()) {
				send(new Packets.ClientEvent(Packets.ClientEvent.Kind.LISTEN));
			}
			while (ARCHIVE.consumeClick()) {
				if (client.screen == null && client.level != null) {
					client.setScreen(new ArchiveScreen(ARCHIVE));
				}
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.fromNamespaceAndPath(MineKz.MOD_ID, "challenge"),
				(graphics, tickCounter) -> ChallengeOverlay.draw(graphics, REPLAY.getTranslatedKeyMessage()));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.fromNamespaceAndPath(MineKz.MOD_ID, "quest"), (graphics, tickCounter) -> QuestOverlay.draw(graphics, LISTEN.getTranslatedKeyMessage()));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.fromNamespaceAndPath(MineKz.MOD_ID, "quest_title"), (graphics, tickCounter) -> QuestTitle.draw(graphics));
	}

	/** Dropped when not connected to a server with MineKz, e.g. a key pressed on the title screen. */
	static void send(CustomPacketPayload payload) {
		if (ClientPlayNetworking.canSend(payload.type())) {
			ClientPlayNetworking.send(payload);
		}
	}
}
