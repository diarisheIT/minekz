package kz.minekz.client;

import java.util.List;
import kz.minekz.engine.Quest;
import kz.minekz.engine.Tier;
import kz.minekz.engine.Word;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Quest panel in the top-left corner, styled like the Challenge banner: the Word (or, by ear, only the Listen key and
 * a speaker) and a draining Time Limit bar, or a grey "Time's up" that fades. Render thread only.
 */
final class QuestOverlay {
	private static final int WIDTH = 110, BASE_HEIGHT = 43, MARGIN = 4;
	private static final int BELOW_BANNER_Y = 100; // under a Challenge banner with a 5-line meaning, for screens too narrow to sit beside it
	private static final double FADE_SECONDS = 1.5;
	private static final double ENDING_GRACE_SECONDS = 0.25; // a timeout reaches the HUD up to a server tick after the Quest goes

	private static Quest shown; // kept after it ends so "Time's up" can fade
	private static double goneAt = Double.NaN;
	private static double timedOutAt = Double.NaN;

	static void draw(GuiGraphics graphics, Component listenKey) {
		double now = System.nanoTime() / 1e9;
		Quest current = MineKzClient.quest.quest().orElse(null);
		if (current != null) {
			shown = current;
			goneAt = timedOutAt = Double.NaN;
		} else if (shown != null && Double.isNaN(goneAt)) {
			goneAt = now;
		}
		for (Word word; (word = MineKzClient.questTimeouts.poll()) != null;) {
			if (shown != null && word.equals(shown.word())) {
				timedOutAt = now;
			}
		}
		if (shown == null) {
			return;
		}
		double fade = Double.isNaN(timedOutAt) ? 0 : (now - timedOutAt) / FADE_SECONDS;
		if (fade >= 1 || Double.isNaN(timedOutAt) && now - goneAt > ENDING_GRACE_SECONDS) { // timed out, or completed
			shown = null;
			return;
		}

		Font font = Minecraft.getInstance().font;
		Word word = shown.word();
		Tier tier = word.tier();
		Component reward = tier.diamonds > 0 ? Component.translatable("minekz.quest.reward_diamonds", tier.xp, tier.diamonds)
				: Component.translatable("minekz.quest.reward", tier.xp);
		List<FormattedCharSequence> rewardLines = font.split(reward, WIDTH - 12);
		int height = BASE_HEIGHT + rewardLines.size() * 10;
		int x = MARGIN;
		boolean besideBanner = MARGIN + WIDTH + MARGIN <= (graphics.guiWidth() - ChallengeOverlay.WIDTH) / 2;
		int y = besideBanner ? MARGIN : BELOW_BANNER_Y;
		graphics.fill(x, y, x + WIDTH, y + height, ChallengeOverlay.argb((int) (0xB8 * (1 - fade)), 0, 0, 0));

		if (!Double.isNaN(timedOutAt)) {
			Component text = Component.translatable("minekz.quest.times_up");
			graphics.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y + (height - 8) / 2,
					ChallengeOverlay.argb((int) (0xFF * (1 - fade)), 0xAA, 0xAA, 0xAA));
			return;
		}

		graphics.drawString(font, Component.translatable("minekz.quest.find"), x + 6, y + 5, 0xFFAAAAAA);
		int seconds = (int) Math.ceil(Math.max(0, shown.secondsLeft()));
		String time = seconds / 60 + ":" + String.format("%02d", seconds % 60);
		graphics.drawString(font, time, x + WIDTH - 6 - font.width(time), y + 5, 0xFFFFFFFF);
		if (shown.byEar()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 6, y + 19);
			graphics.pose().scale(1.5f, 1.5f);
			graphics.drawString(font, Component.literal("[").append(listenKey).append("] 🔊"), 0, 0, 0xFFFFFFFF);
			graphics.pose().popMatrix();
		} else {
			// as big as fits; long Words shrink instead of clipping
			float scale = Math.min(1.5f, (WIDTH - 12) / (float) font.width(word.cyrillic()));
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 6, y + 16);
			graphics.pose().scale(scale, scale);
			graphics.drawString(font, word.cyrillic(), 0, 0, 0xFFFFFFFF);
			graphics.pose().popMatrix();
			String language = Minecraft.getInstance().getLanguageManager().getSelected();
			if (!language.equals("ru_ru") && !language.equals("kk_kz")) { // no Latin for Russian or Kazakh readers
				graphics.drawString(font, word.latin(), x + 6, y + 30, 0xFFAAAAAA);
			}
		}
		for (int i = 0; i < rewardLines.size(); i++) {
			graphics.drawString(font, rewardLines.get(i), x + 6, y + 41 + i * 10, 0xFF80FF20); // vanilla XP green
		}

		int barWidth = (int) (WIDTH * Math.max(0, shown.secondsLeft()) / tier.timeLimitSeconds);
		graphics.fill(x, y + height - 2, x + barWidth, y + height, 0xFFFFC83C);
	}
}
