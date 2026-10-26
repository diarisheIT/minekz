package kz.minekz.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kz.minekz.MineKz;
import kz.minekz.Packets;
import kz.minekz.engine.ActiveChallenge;
import kz.minekz.engine.ChallengeEngine;
import kz.minekz.engine.Word;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Challenge card at the top of the screen: slides in, pulses and shakes as the Window runs low, stamps
 * LEARNED!/LOST when the Challenge ends, then slides out. A Recall's card shows only the icon until it ends, then stamps
 * its XP or, when missed, the Word. Render thread only.
 */
final class ChallengeOverlay {
	static final int WIDTH = 170;
	private static final int TEXT_X = 36; // right of the 1.5× item icon
	private static final double SLIDE_IN_SECONDS = 0.35, ENDING_SECONDS = 1.4, SLIDE_OUT_SECONDS = 0.35;
	private static final double ENDING_GRACE_SECONDS = 0.25; // an ending reaches the HUD up to a server tick after the Challenge goes
	private static final double URGENT_SECONDS = 5;
	private static final int GREY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF5555;

	private static ActiveChallenge shown; // the Challenge on the card; kept after it ends so its moment can play
	private static double shownAt;
	private static double goneAt = Double.NaN; // when the server stopped reporting the shown Challenge
	private static Packets.Ending ending; // of the shown Challenge
	private static double endingAt;

	/** Blocks across the icon for each animal; their heads differ more than their eye heights. */
	private static final Map<String, Float> HEAD_SIZES = Map.ofEntries(Map.entry("minecraft:chicken", 0.5f), Map.entry("minecraft:rabbit", 0.6f),
			Map.entry("minecraft:horse", 1.4f), Map.entry("minecraft:donkey", 1.3f), Map.entry("minecraft:mule", 1.3f),
			Map.entry("minecraft:bee", 0.4f), Map.entry("minecraft:bat", 0.5f), Map.entry("minecraft:parrot", 0.4f),
			Map.entry("minecraft:frog", 0.5f), Map.entry("minecraft:tadpole", 0.4f), Map.entry("minecraft:turtle", 0.6f),
			Map.entry("minecraft:cat", 0.6f), Map.entry("minecraft:fox", 0.7f), Map.entry("minecraft:wolf", 0.7f),
			Map.entry("minecraft:spider", 1.0f), Map.entry("minecraft:cave_spider", 0.6f), Map.entry("minecraft:goat", 1.0f),
			Map.entry("minecraft:panda", 1.2f), Map.entry("minecraft:polar_bear", 1.3f), Map.entry("minecraft:dolphin", 1.2f),
			Map.entry("minecraft:hoglin", 1.4f), Map.entry("minecraft:llama", 1.5f), Map.entry("minecraft:trader_llama", 1.5f),
			Map.entry("minecraft:camel", 2.0f));
	private static final Map<String, Entity> iconAnimals = new HashMap<>(); // one model per animal type, posed afresh each frame
	private static Level iconLevel;

	static void draw(GuiGraphics graphics, Component replayKey) {
		double now = System.nanoTime() / 1e9;
		Packets.ChallengeSync challenge = MineKzClient.challenge;
		ActiveChallenge active = challenge.active().orElse(null);
		if (shown == null && active != null) {
			shown = active;
			shownAt = now;
		} else if (shown != null && Double.isNaN(goneAt)) {
			if (active != null && active.word().equals(shown.word())) {
				shown = active;
			} else {
				goneAt = now;
			}
		}
		for (Packets.Ending next; (next = MineKzClient.endings.poll()) != null;) {
			if (shown == null || !next.word().equals(shown.word())) {
				continue; // a Challenge the card isn't showing, e.g. one that ended while the HUD was hidden
			}
			if (ending != null && !ending.learned() && !next.learned()) {
				// one Forfeit per item type of the Word
				ending = new Packets.Ending(next.word(), false, ending.lost() + next.lost());
			} else {
				ending = next;
				endingAt = now;
			}
		}
		if (shown == null) {
			return;
		}
		double outStart = ending != null ? endingAt + ENDING_SECONDS : !Double.isNaN(goneAt) ? goneAt + ENDING_GRACE_SECONDS : Double.MAX_VALUE;
		if (now > outStart + SLIDE_OUT_SECONDS) {
			shown = null;
			goneAt = Double.NaN;
			ending = null;
			return;
		}
		double slide = ease(Math.min(1, (now - shownAt) / SLIDE_IN_SECONDS)) * (1 - ease(Math.max(0, (now - outStart) / SLIDE_OUT_SECONDS)));
		drawCard(graphics, challenge, replayKey, now, slide);
	}

	/** Whether a LEARNED!/LOST moment is on screen until its card has slid out, or queued and about to be. */
	static boolean momentPlaying() {
		return ending != null || !MineKzClient.endings.isEmpty();
	}

	private static void drawCard(GuiGraphics graphics, Packets.ChallengeSync challenge, Component replayKey, double now, double slide) {
		Font font = Minecraft.getInstance().font;
		var word = shown.word();
		Component subtitle = shown.recall() ? Component.translatable("minekz.recall.subtitle")
				: Component.literal(word.gloss(Minecraft.getInstance().getLanguageManager().getSelected()));
		List<FormattedCharSequence> meaning = font.split(subtitle, WIDTH - TEXT_X - 6);
		int rowY = Math.max(34, 24 + meaning.size() * 10) + 2;
		int height = rowY + 13;

		boolean running = ending == null && Double.isNaN(goneAt);
		double urgency = running ? Math.max(0, 1 - shown.secondsLeft() / URGENT_SECONDS) : 0;
		double pulse = urgency > 0 ? 0.5 + 0.5 * Math.sin(now * (4.5 + 7.5 * urgency)) : 0;
		int shake = urgency > 0.4 ? (int) Math.round(Math.sin(now * 43) * urgency * 1.5) : 0;
		int x = (graphics.guiWidth() - WIDTH) / 2 + shake;
		int y = 4 + (int) Math.round((slide - 1) * (height + 8));

		int background;
		if (ending == null) {
			background = argb(0xB8, (int) (0x78 * urgency * pulse), 0, 0);
		} else {
			double t = Math.min(1, (now - endingAt) / ENDING_SECONDS);
			background = ending.learned() ? argb((int) (0xD8 - 0x98 * t), 0x3C, 0xC8, 0x50) : argb((int) (0xCC - 0x80 * t), 0xC8, 0x14, 0x14);
		}
		graphics.fill(x, y, x + WIDTH, y + height, background);

		if (shown.animal()) {
			drawHead(graphics, shown.itemId(), x + 5, y + 5, 24);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 5, y + 5);
			graphics.pose().scale(1.5f, 1.5f);
			// the Word's iconic item, not the exact one picked up: every log shows as oak
			graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(MineKz.icon(word)))), 0, 0);
			graphics.pose().popMatrix();
		}

		if (ending != null) {
			stamp(graphics, font, x, y, height, now);
			return;
		}

		// the Word as big as fits beside the icon; long Words shrink instead of clipping
		Component title = shown.recall() ? Component.translatable("minekz.recall.title") : Component.literal(word.cyrillic());
		float scale = Math.min(2f, (WIDTH - TEXT_X - 6) / (float) font.width(title));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + TEXT_X, y + 5);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, title, 0, 0, 0xFFFFFFFF);
		graphics.pose().popMatrix();
		for (int i = 0; i < meaning.size(); i++) {
			graphics.drawString(font, meaning.get(i), x + TEXT_X, y + 24 + i * 10, GREY);
		}

		int rowX = x + 6;
		if (challenge.micOn()) {
			graphics.fill(rowX, y + rowY + 2, rowX + 4, y + rowY + 6, RED);
			// four bars that rise with the mic level, so the player sees they're being heard
			int lit = (int) Math.ceil(Math.min(1, SpeechListener.level * 3) * 4);
			for (int bar = 0; bar < 4; bar++) {
				int barHeight = 2 + bar * 2;
				graphics.fill(rowX + 7 + bar * 3, y + rowY + 8 - barHeight, rowX + 9 + bar * 3, y + rowY + 8, bar < lit ? 0xFFFFFFFF : 0xFF555555);
			}
			rowX += 22;
		} else {
			graphics.drawString(font, "♪", rowX, y + rowY, GREY); // a sample plays and the mic is off
			rowX += 10;
		}
		String heard = challenge.lastHeard();
		if (!heard.isEmpty()) {
			Component line = shown.recall() ? Component.translatable("minekz.heard", heard) : heardLine(word, heard); // matched letters would give a Recall away
			graphics.drawString(font, line, rowX, y + rowY, GREY);
			rowX += font.width(line) + 6;
		}
		var replay = Component.translatable("minekz.replay", replayKey);
		int replayX = x + WIDTH - 6 - font.width(replay);
		if (rowX <= replayX && !shown.recall()) { // a long heard phrase takes the row; the replay key still works
			graphics.drawString(font, replay, replayX, y + rowY, 0xFF888888);
		}

		int barWidth = (int) (WIDTH * Math.max(0, shown.secondsLeft()) / ChallengeEngine.WINDOW_SECONDS);
		graphics.fill(x, y + height - 2, x + barWidth, y + height, windowColour(shown.secondsLeft()));
	}

	/**
	 * The animal's own 3D model, facing the player and zoomed in on its head, in the {@code size}px icon square at (x, y).
	 * Drawn like the inventory's player, so resource packs apply. The Archive draws it too.
	 */
	static void drawHead(GuiGraphics graphics, String animalType, int x, int y, int size) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (iconLevel != mc.level) {
			iconAnimals.clear(); // an entity belongs to its level
			iconLevel = mc.level;
		}
		Entity animal = iconAnimals.computeIfAbsent(animalType,
				type -> BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type)).create(mc.level, EntitySpawnReason.LOAD));
		if (animal == null) {
			return;
		}
		if (!(mc.getEntityRenderDispatcher().getRenderer(animal).createRenderState(animal, 1f) instanceof LivingEntityRenderState state)) {
			graphics.pose().pushMatrix(); // the Ender Dragon's model has no head to frame: its head item stands in
			graphics.pose().translate(x, y);
			graphics.pose().scale(size / 16f, size / 16f);
			graphics.renderFakeItem(new ItemStack(Items.DRAGON_HEAD), 0, 0);
			graphics.pose().popMatrix();
			return;
		}
		state.lightCoords = LightTexture.FULL_BRIGHT;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.bodyRot = 180; // facing the camera, so the head sits in the middle whatever the body's length
		state.yRot = 0;
		state.xRot = 0;
		float pxPerBlock = size / HEAD_SIZES.getOrDefault(animalType, 0.85f);
		Quaternionf flip = new Quaternionf().rotateZ((float) Math.PI), tilt = new Quaternionf().rotateX(15 * Mth.DEG_TO_RAD);
		graphics.submitEntityRenderState(state, pxPerBlock, new Vector3f(0, state.eyeHeight, 0), flip.mul(tilt), tilt, x, y, x + size, y + size);
	}

	/** "Heard: Тал" with the letters that match the Word green and the rest red. */
	private static Component heardLine(Word word, String heard) {
		boolean[] matches = word.lettersMatching(heard);
		MutableComponent line = Component.translatable("minekz.heard", "");
		for (int i = 0; i < heard.length(); i++) {
			line.append(Component.literal(String.valueOf(heard.charAt(i))).withColor(matches[i] ? GREEN : RED));
		}
		return line;
	}

	/** LEARNED! or LOST ×n, or for a Recall its XP or the missed Word, slammed onto the card: starts large and settles at twice normal size. */
	private static void stamp(GuiGraphics graphics, Font font, int x, int y, int height, double now) {
		Component text = shown.recall() ? Component.literal(ending.learned() ? "+" + shown.word().tier().xp + " XP" : shown.word().cyrillic())
				: ending.learned() ? Component.translatable("minekz.learned") : Component.translatable("minekz.lost", ending.lost());
		float fit = Math.min(1, (WIDTH - TEXT_X) / (2f * font.width(text))); // a long Word settles small enough to fit
		float scale = (float) (2 * fit * (2.2 - 1.2 * ease(Math.min(1, (now - endingAt) * 3))));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + (WIDTH + 30) / 2f, y + height / 2f);
		graphics.pose().rotate((float) Math.toRadians(-4));
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, -font.width(text) / 2, -4, ending.learned() ? 0xFFFFFFFF : 0xFFFFD0D0);
		graphics.pose().popMatrix();
	}

	private static int windowColour(double secondsLeft) {
		double left = secondsLeft / ChallengeEngine.WINDOW_SECONDS;
		return left > 0.5 ? 0xFFFFC83C : left > 1 / 3.0 ? 0xFFFF8C28 : 0xFFE62828;
	}

	static double ease(double t) {
		return 1 - Math.pow(1 - t, 3);
	}

	static int argb(int a, int r, int g, int b) {
		return a << 24 | r << 16 | g << 8 | b;
	}
}
