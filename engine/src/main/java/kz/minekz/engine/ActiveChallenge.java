package kz.minekz.engine;

/**
 * {@code itemId} is the item, or with {@code animal} the animal type, that started the Challenge; the HUD shows it. Some ids
 * are both (minecraft:chicken), so the HUD goes by {@code animal}. A {@code recall} shows only that item's icon.
 */
public record ActiveChallenge(Word word, String itemId, double secondsLeft, boolean recall, boolean animal) {}
