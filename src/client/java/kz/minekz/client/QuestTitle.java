package kz.minekz.client;

import kz.minekz.Packets;
import kz.minekz.engine.Effect;
import kz.minekz.engine.Tier;
import kz.minekz.engine.Word;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The "ТАПСЫРМА ОРЫНДАЛДЫ" title across the centre of the screen when a Quest completes, coloured by Tier. It waits
 * behind any Learned moment, and hands the Reward to the server as it appears. Render thread only.
 */
final class QuestTitle {
	private static final double TITLE_SECONDS = 3, SLAM_SECONDS = 0.35, FADE_SECONDS = 0.6;

	private static Effect.QuestCompleted shown;
	private static double shownAt;

	static void draw(GuiGraphics graphics) {
		double now = System.nanoTime() / 1e9;
		if (shown != null && now - shownAt > TITLE_SECONDS) {
			shown = null;
		}
		// peek before checking the moment: the server queues a Quest's Learned ending first, so it is seen too.
		// Not while paused, so the Reward (paid on the server tick) arrives with the sound
		if (shown == null && MineKzClient.questCompletions.peek() != null && !ChallengeOverlay.momentPlaying()
				&& !Minecraft.getInstance().isPaused()) {
			shown = MineKzClient.questCompletions.poll();
			if (shown != null) {
				shownAt = now;
				MineKzClient.send(new Packets.ClientEvent(Packets.ClientEvent.Kind.TITLE_SHOWN));
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f));
			}
		}
		if (shown == null) {
			return;
		}

		double t = now - shownAt;
		double alpha = Math.min(1, (TITLE_SECONDS - t) / FADE_SECONDS);
		int a = (int) (0xFF * alpha);
		Font font = Minecraft.getInstance().font;
		Component title = Component.translatable("minekz.quest.title");
		Word word = shown.word();
		int tierColour = colour(word.tier(), a);
		int centreX = graphics.guiWidth() / 2, centreY = graphics.guiHeight() / 2 - 30;
		graphics.fill(0, centreY - 22, graphics.guiWidth(), centreY + 34, ChallengeOverlay.argb((int) (0xB8 * alpha), 0, 0, 0));
		graphics.fill(0, centreY - 22, graphics.guiWidth(), centreY - 20, tierColour);
		graphics.fill(0, centreY + 32, graphics.guiWidth(), centreY + 34, tierColour);

		// slammed in like LEARNED!: starts large and settles at 2.5× size
		float scale = (float) (2.5 * (1.8 - 0.8 * ChallengeOverlay.ease(Math.min(1, t / SLAM_SECONDS))));
		scale = Math.min(scale, (graphics.guiWidth() - 16) / (float) font.width(title)); // narrow screens shrink it instead of clipping
		graphics.pose().pushMatrix();
		graphics.pose().translate(centreX, centreY - 4);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, title, -font.width(title) / 2, -4, tierColour);
		graphics.pose().popMatrix();

		String gloss = word.gloss(Minecraft.getInstance().getLanguageManager().getSelected());
		String meaning = gloss.isEmpty() ? word.cyrillic() : word.cyrillic() + " · " + gloss;
		graphics.drawString(font, meaning, centreX - font.width(meaning) / 2, centreY + 10, ChallengeOverlay.argb(a, 0xFF, 0xFF, 0xFF));
		Component reward = shown.diamonds() > 0 ? Component.translatable("minekz.quest.won_diamonds", shown.xp(), shown.diamonds())
				: Component.translatable("minekz.quest.won", shown.xp());
		graphics.drawString(font, reward, centreX - font.width(reward) / 2, centreY + 21, ChallengeOverlay.argb(a, 0x80, 0xFF, 0x20)); // vanilla XP green
	}

	/** White, green, aqua, gold: vanilla's colours for rising rarity. */
	private static int colour(Tier tier, int alpha) {
		int rgb = switch (tier) {
			case COMMON -> 0xFFFFFF;
			case UNCOMMON -> 0x55FF55;
			case RARE -> 0x55FFFF;
			case LEGENDARY -> 0xFFAA00;
		};
		return alpha << 24 | rgb;
	}
}
