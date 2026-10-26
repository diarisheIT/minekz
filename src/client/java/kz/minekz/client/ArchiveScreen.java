package kz.minekz.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import kz.minekz.MineKz;
import kz.minekz.Packets;
import kz.minekz.engine.Standing;
import kz.minekz.engine.Tier;
import kz.minekz.engine.Word;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/**
 * The Archive (look B of {@code prototypes/archive-PROTOTYPE/}): a left rail with this world's progress ring, the Items and
 * Animals tabs and the filters, a grid of cards in Word List order (Learned ones with their Word, the rest dimmed with
 * "???"), and a detail pane for the clicked Learned card with its Sample button. When this world holds more than one player's
 * progress, a Leaderboard tab ranks them in the grid pane by counts only. It doesn't pause, so Challenges, Quests and
 * Recalls keep counting down with their HUD behind it.
 */
final class ArchiveScreen extends Screen {
	private static final int INSET_X = 12, INSET_Y = 10, GAP = 4, RAIL = 110, DETAIL = 112, PAD = 6;
	private static final int COLUMNS = 5, CARD_HEIGHT = 34, ROW_HEIGHT = 26, CARD_GAP = 3, RING = 32, RING_THICKNESS = 3;
	private static final int GLASS = 0xBD0C0E14, EDGE = 0x1AFFFFFF, GOLD = 0xFFFFD36A, GREY = 0xFFAAAAAA, MUTED = 0xFF777777;
	// oyu-örnek: a qoshqar müiz (ram's horn) motif, tiled under the title
	private static final String[] ORNAMENT = {"..##.......##..", ".#..#.....#..#.", ".#.#...#...#.#.", "..#...#.#...#..",
			".....#...#.....", "....#.....#....", "###############"};

	private enum Show { LEARNED, UNLEARNED, ALL }

	private enum Sort { WORD_LIST, RECENT }

	private enum Tab { ITEMS, ANIMALS, LEADERBOARD }

	/** A clickable area drawn this frame; clicks go to the last frame's, so drawing and clicking share one layout. */
	private record Control(int x, int y, int width, int height, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private final KeyMapping key;
	private final Map<Word, ItemStack> items = new LinkedHashMap<>(); // item Words in Word List order, with their icons
	private final Set<Word> animals = new LinkedHashSet<>(MineKz.ANIMALS.values());
	private final int total = MineKz.words().size();
	private final List<int[]> ring = new ArrayList<>(); // x, y and angle (out of 1000, clockwise from the top) of each ring pixel
	private final List<Control> controls = new ArrayList<>();
	private Tab tab = Tab.ITEMS;
	private Show show = Show.ALL;
	private Sort sort = Sort.WORD_LIST;
	private final Set<Tier> tiers = EnumSet.noneOf(Tier.class); // empty shows every Tier
	private double scroll;
	private Word selected;
	private boolean draggingScrollbar;
	private int gridX, gridRight, cardWidth; // set in init, so they follow the window size

	ArchiveScreen(KeyMapping key) {
		super(Component.translatable("minekz.archive.title"));
		this.key = key;
		MineKz.WORDS.values().forEach(word -> items.computeIfAbsent(word,
				w -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(MineKz.icon(w))))));
		double c = (RING - 1) / 2.0;
		for (int y = 0; y < RING; y++) {
			for (int x = 0; x < RING; x++) {
				double d = Math.hypot(x - c, y - c);
				if (d <= c + .3 && d >= c - RING_THICKNESS + .3) {
					ring.add(new int[] {x, y, (int) ((Math.atan2(x - c, c - y) / (2 * Math.PI) + 1) % 1 * 1000)});
				}
			}
		}
	}

	@Override
	protected void init() {
		gridX = INSET_X + RAIL + GAP;
		gridRight = width - INSET_X - DETAIL - GAP;
		cardWidth = Math.max(16, (gridRight - gridX - 2 * PAD - (COLUMNS - 1) * CARD_GAP) / COLUMNS);
		scroll = Math.min(scroll, maxScroll());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.clamp(scroll - scrollY * (CARD_HEIGHT + CARD_GAP), 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (maxScroll() > 0 && event.x() >= gridRight - PAD && event.x() < gridRight
				&& event.y() >= INSET_Y && event.y() < height - INSET_Y) {
			draggingScrollbar = true;
			scrollTo(event.y());
			return true;
		}
		for (Control control : List.copyOf(controls)) {
			if (control.contains(event.x(), event.y())) {
				control.action().run();
				return true;
			}
		}
		Word word = cardAt(event.x(), event.y());
		if (word != null && MineKzClient.progress.learned().contains(word)) {
			selected = word;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingScrollbar = false;
		return super.mouseReleased(event);
	}

	/** Puts the scrollbar thumb's middle under {@code mouseY}. */
	private void scrollTo(double mouseY) {
		int top = INSET_Y + PAD, track = height - 2 * top, thumb = thumbHeight();
		scroll = Math.clamp((mouseY - top - thumb / 2.0) / (track - thumb) * maxScroll(), 0, maxScroll());
	}

	private int thumbHeight() {
		int view = height - 2 * INSET_Y, track = view - 2 * PAD;
		return Math.max(10, (int) (track * view / (view + maxScroll())));
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics); // no menu blur: it would blur the Challenge and Quest HUD behind the Archive
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Set<Word> learned = MineKzClient.progress.learned(), completed = MineKzClient.progress.completed();
		Map<Word, Long> learnedAt = MineKzClient.progress.learnedAt();
		List<Standing> standings = MineKzClient.leaderboard.standings();
		if (tab == Tab.LEADERBOARD && standings.size() < 2) {
			tab = Tab.ITEMS;
		}
		controls.clear();
		scroll = Math.min(scroll, maxScroll()); // a filter or a new Learned Word may shorten the grid
		drawRail(graphics, learned, standings, mouseX, mouseY);
		if (tab == Tab.LEADERBOARD) {
			drawLeaderboard(graphics, standings);
		} else {
			drawGrid(graphics, learned, cardAt(mouseX, mouseY));
		}
		drawDetail(graphics, selected != null && learned.contains(selected) ? selected : null, completed, learnedAt, mouseX, mouseY); // Set.copyOf rejects contains(null)
	}

	private void drawRail(GuiGraphics graphics, Set<Word> learned, List<Standing> standings, int mouseX, int mouseY) {
		int x = INSET_X, y = INSET_Y, inner = RAIL - 16;
		glass(graphics, x, y, x + RAIL, height - INSET_Y);
		x += 8;
		drawScaled(graphics, t("minekz.archive.title"), x, y + 8, 1.5f, 0xFFFFFFFF);
		for (int dx = 0; dx < inner; dx++) {
			for (int dy = 0; dy < ORNAMENT.length; dy++) {
				if (ORNAMENT[dy].charAt(dx % ORNAMENT[dy].length()) == '#') {
					graphics.fill(x + dx, y + 26 + dy, x + dx + 1, y + 27 + dy, 0xD9E8B64A);
				}
			}
		}

		int ringY = y + 39, filled = learned.size() * 1000 / total;
		for (int[] p : ring) {
			graphics.fill(x + p[0], ringY + p[1], x + p[0] + 1, ringY + p[1] + 1, p[2] < filled ? GOLD : 0x24FFFFFF);
		}
		graphics.drawString(font, t("minekz.archive.this_world"), x + RING + 5, ringY + 6, GREY);
		graphics.drawString(font, learned.size() + "/" + total, x + RING + 5, ringY + 17, 0xFFFFFFFF);

		int tabY = ringY + RING + 6;
		tab(graphics, t("minekz.archive.items"), count(items.keySet(), learned), Tab.ITEMS, x, tabY, inner, mouseX, mouseY);
		tab(graphics, t("minekz.archive.animals"), count(animals, learned), Tab.ANIMALS, x, tabY + 22, inner, mouseX, mouseY);
		if (standings.size() > 1) {
			tab(graphics, t("minekz.archive.leaderboard"), String.valueOf(standings.size()), Tab.LEADERBOARD, x, tabY + 44, inner, mouseX, mouseY);
			tabY += 22; // the filters move down below it
		}
		if (tab == Tab.LEADERBOARD) {
			return; // the filters only sort cards
		}

		int chipY = chips(graphics, x, tabY + 50, inner, mouseX, mouseY, List.of(t("minekz.archive.show.learned"), t("minekz.archive.show.unlearned"), t("minekz.archive.show.all")),
				i -> 0, i -> show == Show.values()[i], i -> show = Show.values()[i]);
		chipY = chips(graphics, x, chipY + 3, inner, mouseX, mouseY, List.of(Tier.values()).stream().map(ArchiveScreen::tierName).toList(),
				i -> tierColour(Tier.values()[i]), i -> tiers.contains(Tier.values()[i]), i -> {
					Tier tier = Tier.values()[i];
					if (!tiers.remove(tier)) {
						tiers.add(tier);
					}
				});
		chips(graphics, x, chipY + 3, inner, mouseX, mouseY, List.of(t("minekz.archive.sort.word_list"), t("minekz.archive.sort.recent")),
				i -> 0, i -> sort == Sort.values()[i], i -> sort = Sort.values()[i]);
	}

	private static String count(Set<Word> words, Set<Word> learned) {
		return words.stream().filter(learned::contains).count() + "/" + words.size();
	}

	private void tab(GuiGraphics graphics, String name, String count, Tab tab, int x, int y, int width, int mouseX, int mouseY) {
		boolean on = this.tab == tab;
		Control control = new Control(x, y, width, 20, () -> {
			if (this.tab != tab) {
				this.tab = tab;
				scroll = 0;
				selected = null;
			}
		});
		controls.add(control);
		graphics.fill(x, y, x + width, y + 20, on ? 0x1FFFFFFF : control.contains(mouseX, mouseY) ? 0x12FFFFFF : 0);
		if (on) {
			graphics.fill(x, y + 19, x + width, y + 20, GOLD);
		}
		graphics.drawString(font, name, x + 4, y + 6, on ? 0xFFFFFFFF : GREY);
		graphics.drawString(font, count, x + width - 4 - font.width(count), y + 6, GREY);
	}

	/** Small toggle chips that wrap within {@code width}, each with an optional colour dot; returns the y below them. */
	private int chips(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY, List<String> labels,
			IntUnaryOperator dot, IntPredicate on, IntConsumer click) {
		int cx = x;
		for (int i = 0; i < labels.size(); i++) {
			int dotColour = dot.applyAsInt(i), chipWidth = (int) Math.ceil(font.width(labels.get(i)) * 0.75f) + 6 + (dotColour != 0 ? 5 : 0);
			if (cx > x && cx + chipWidth > x + width) {
				cx = x;
				y += 12;
			}
			int index = i;
			Control control = new Control(cx, y, chipWidth, 10, () -> {
				click.accept(index);
				scroll = 0;
			});
			controls.add(control);
			graphics.fill(cx, y, cx + chipWidth, y + 10, on.test(i) ? 0x33FFFFFF : control.contains(mouseX, mouseY) ? 0x1FFFFFFF : 0x0EFFFFFF);
			int textX = cx + 3;
			if (dotColour != 0) {
				graphics.fill(textX, y + 4, textX + 3, y + 7, dotColour);
				textX += 5;
			}
			drawScaled(graphics, labels.get(i), textX, y + 2, 0.75f, on.test(i) ? 0xFFFFFFFF : GREY);
			cx += chipWidth + 2;
		}
		return y + 10;
	}

	private void drawGrid(GuiGraphics graphics, Set<Word> learned, Word hovered) {
		glass(graphics, gridX, INSET_Y, gridRight, height - INSET_Y);
		graphics.enableScissor(gridX + 1, INSET_Y + 1, gridRight - 1, height - INSET_Y - 1);
		List<Word> visible = visible(learned);
		for (int i = 0; i < visible.size(); i++) {
			Word word = visible.get(i);
			int x = cardX(i), y = cardY(i);
			if (y + CARD_HEIGHT < INSET_Y || y > height - INSET_Y) {
				continue;
			}
			int middle = x + cardWidth / 2;
			if (learned.contains(word)) {
				boolean lifted = word.equals(hovered) || word.equals(selected);
				if (lifted) {
					y--; // the soft lift
				}
				graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT, lifted ? 0x1FFFFFFF : 0x0EFFFFFF);
				graphics.fill(x, y + CARD_HEIGHT - 1, x + cardWidth, y + CARD_HEIGHT, tierColour(word.tier()));
				drawIcon(graphics, word, middle - 8, y + 4, 16);
				String text = font.plainSubstrByWidth(word.cyrillic(), (int) ((cardWidth - 2) / 0.75f));
				if (!text.equals(word.cyrillic())) {
					text = font.plainSubstrByWidth(word.cyrillic(), (int) ((cardWidth - 2) / 0.75f) - font.width("…")) + "…";
				}
				drawScaled(graphics, text, middle - font.width(text) * 3 / 8, y + 23, 0.75f, 0xFFFFFFFF);
			} else {
				graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT, 0x0EFFFFFF);
				drawIcon(graphics, word, middle - 8, y + 4, 16);
				drawScaled(graphics, "???", middle - font.width("???") * 3 / 8, y + 23, 0.75f, MUTED);
				graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT, 0x99101216); // dims the icon to a grey silhouette
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics);
	}

	private void drawScrollbar(GuiGraphics graphics) {
		double max = maxScroll();
		if (max > 0) { // in the grid pane's right padding, clear of the cards and rows
			int top = INSET_Y + PAD, track = height - 2 * top, thumb = thumbHeight(), x = gridRight - 4;
			int thumbY = top + (int) ((track - thumb) * scroll / max);
			graphics.fill(x, top, x + 2, top + track, 0x24FFFFFF);
			graphics.fill(x, thumbY, x + 2, thumbY + thumb, draggingScrollbar ? GOLD : 0x99FFFFFF);
		}
	}

	/** One row per player in rank order: rank, head, name with an online dot, then item and animal counts; offline players dimmed. */
	private void drawLeaderboard(GuiGraphics graphics, List<Standing> standings) {
		glass(graphics, gridX, INSET_Y, gridRight, height - INSET_Y);
		int x = gridX + PAD, right = gridRight - PAD;
		graphics.enableScissor(gridX + 1, INSET_Y + 1, gridRight - 1, height - INSET_Y - 1);
		for (int i = 0; i < standings.size(); i++) {
			Standing s = standings.get(i);
			int y = INSET_Y + PAD + i * ROW_HEIGHT - (int) scroll;
			if (y + ROW_HEIGHT < INSET_Y || y > height - INSET_Y) {
				continue;
			}
			int colour = s.online() ? 0xFFFFFFFF : MUTED;
			graphics.fill(x, y, right, y + 24, s.online() ? 0x14FFFFFF : 0x08FFFFFF);
			drawScaled(graphics, "#" + (i + 1), x + 4, y + 7, 1.25f, i == 0 && s.online() ? GOLD : colour);
			PlayerFaceRenderer.draw(graphics, skin(s.id()), x + 28, y + 4, 16, s.online() ? 0xFFFFFFFF : 0xFF777777);
			String name = font.plainSubstrByWidth(s.name(), right - x - 61);
			graphics.drawString(font, name, x + 48, y + 3, colour);
			int dotX = x + 48 + font.width(name) + 3, dot = s.online() ? 0xFF5FD06F : 0xFF777777; // a round 5px dot: online or not
			graphics.fill(dotX + 1, y + 4, dotX + 4, y + 9, dot);
			graphics.fill(dotX, y + 5, dotX + 5, y + 8, dot);
			drawScaled(graphics, s.items() + " items · " + s.animals() + " animals learned", x + 48, y + 14, 0.75f, s.online() ? GREY : MUTED);
		}
		graphics.disableScissor();
		drawScrollbar(graphics);
	}

	/** The player's skin from the tab list; offline players aren't in it, so they get their default skin. */
	private PlayerSkin skin(UUID id) {
		PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
	}

	private void drawDetail(GuiGraphics graphics, Word word, Set<Word> completed, Map<Word, Long> learnedAt, int mouseX, int mouseY) {
		int left = width - INSET_X - DETAIL, middle = left + DETAIL / 2, inner = DETAIL - 20;
		glass(graphics, left, INSET_Y, left + DETAIL, height - INSET_Y);
		if (word == null) {
			if (tab == Tab.LEADERBOARD) {
				return;
			}
			drawCentred(graphics, font.split(Component.translatable("minekz.archive.pick"), inner), middle, INSET_Y + 50, MUTED);
			return;
		}
		int y = INSET_Y + 10;
		drawIcon(graphics, word, middle - 24, y, 48);
		y += 52;
		// as big as fits; long Words shrink instead of clipping
		float scale = Math.min(1.5f, inner / (float) font.width(word.cyrillic()));
		drawScaled(graphics, word.cyrillic(), middle - (int) (font.width(word.cyrillic()) * scale / 2), y, scale, 0xFFFFFFFF);
		y += 15;
		String gloss = word.gloss(minecraft.getLanguageManager().getSelected()); // empty in Kazakh
		if (!gloss.isEmpty()) {
			y = drawCentred(graphics, font.split(Component.literal(gloss), inner), middle, y, 0xFFFFFFFF);
		}
		y += 4;
		if (word.tier() != null) {
			y = drawCentred(graphics, List.of(Component.literal(tierName(word.tier())).getVisualOrderText()), middle, y, tierColour(word.tier()));
		}
		if (learnedAt.containsKey(word)) {
			y = drawCentred(graphics, List.of(Component.literal(t("minekz.archive.learned_ago", ago(System.currentTimeMillis() - learnedAt.get(word)))).getVisualOrderText()), middle, y, GREY);
		}
		if (completed.contains(word)) {
			y = drawCentred(graphics, List.of(Component.translatable("minekz.archive.quest_done").getVisualOrderText()), middle, y, GOLD);
		}

		// like Replay; the engine ignores it during a Challenge or Recall, so it looks off then
		boolean usable = MineKzClient.challenge.active().isEmpty();
		Control sample = new Control(left + 10, y + 8, inner, 16, () -> MineKzClient.send(new Packets.ArchiveSample(word)));
		controls.add(sample);
		graphics.fill(sample.x(), sample.y(), sample.x() + inner, sample.y() + 16,
				usable && sample.contains(mouseX, mouseY) ? 0x40FFFFFF : 0x1FFFFFFF);
		drawCentred(graphics, List.of(Component.translatable("minekz.archive.sample").getVisualOrderText()), middle, sample.y() + 4, usable ? 0xFFFFFFFF : MUTED);
	}

	/** The Word's iconic item, or for an animal its 3D head, in the {@code size}px square at (x, y). */
	private void drawIcon(GuiGraphics graphics, Word word, int x, int y, int size) {
		if (animals.contains(word)) {
			ChallengeOverlay.drawHead(graphics, MineKz.icon(word), x, y, size);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(size / 16f, size / 16f);
		graphics.renderFakeItem(items.get(word), 0, 0);
		graphics.pose().popMatrix();
	}

	/** The current tab's Words that pass the filters, in Word List order or newest Learned first (untimed ones last). */
	private List<Word> visible(Set<Word> learned) {
		Map<Word, Long> learnedAt = sort == Sort.RECENT ? MineKzClient.progress.learnedAt() : Map.of();
		if (tab == Tab.LEADERBOARD) {
			return List.of();
		}
		return (tab == Tab.ANIMALS ? animals : items.keySet()).stream()
				.filter(w -> show == Show.ALL || learned.contains(w) == (show == Show.LEARNED))
				.filter(w -> tiers.isEmpty() || tiers.contains(w.tier()))
				.sorted(Comparator.comparing((Word w) -> learnedAt.getOrDefault(w, Long.MIN_VALUE)).reversed()) // stable: ties keep Word List order
				.toList();
	}

	private static String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String tierName(Tier tier) {
		return t("minekz.archive.tier." + tier.name().toLowerCase(Locale.ROOT));
	}

	private static String ago(long millis) {
		long minutes = millis / 60_000, hours = minutes / 60, days = hours / 24;
		return days > 0 ? t("minekz.archive.ago.days", days) : hours > 0 ? t("minekz.archive.ago.hours", hours)
				: minutes > 0 ? t("minekz.archive.ago.minutes", minutes) : t("minekz.archive.ago.now");
	}

	/** Draws {@code lines} centred on {@code middle} and returns the y below them. */
	private int drawCentred(GuiGraphics graphics, List<FormattedCharSequence> lines, int middle, int y, int colour) {
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, middle - font.width(line) / 2, y, colour);
			y += 10;
		}
		return y;
	}

	private static void glass(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
		graphics.fill(x1, y1, x2, y2, GLASS);
		graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, EDGE);
	}

	/** Creative-only Words have no Tier, so they get plain white. */
	private static int tierColour(Tier tier) {
		return tier == null ? 0xFFFFFFFF : switch (tier) {
			case COMMON -> 0xFFA0A0A0;
			case UNCOMMON -> 0xFF5FD06F;
			case RARE -> 0xFF5B9BFF;
			case LEGENDARY -> 0xFFFFBE3C;
		};
	}

	private void drawScaled(GuiGraphics graphics, String text, int x, int y, float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, colour);
		graphics.pose().popMatrix();
	}

	private int cardX(int i) {
		return gridX + PAD + i % COLUMNS * (cardWidth + CARD_GAP);
	}

	private int cardY(int i) {
		return INSET_Y + PAD + i / COLUMNS * (CARD_HEIGHT + CARD_GAP) - (int) scroll;
	}

	/** The card under the mouse, or null, counting only the grid pane's visible area. */
	private Word cardAt(double mouseX, double mouseY) {
		if (mouseX < gridX || mouseX >= gridRight || mouseY < INSET_Y || mouseY >= height - INSET_Y) {
			return null;
		}
		List<Word> visible = visible(MineKzClient.progress.learned());
		for (int i = 0; i < visible.size(); i++) {
			int x = cardX(i), y = cardY(i);
			if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT) {
				return visible.get(i);
			}
		}
		return null;
	}

	private double maxScroll() {
		int content = tab == Tab.LEADERBOARD ? MineKzClient.leaderboard.standings().size() * ROW_HEIGHT - 2
				: (visible(MineKzClient.progress.learned()).size() + COLUMNS - 1) / COLUMNS * (CARD_HEIGHT + CARD_GAP) - CARD_GAP;
		return Math.max(0, content + 2 * PAD - (height - 2 * INSET_Y));
	}
}
