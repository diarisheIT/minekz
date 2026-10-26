# MineKZ

A single-player Minecraft mod that teaches Kazakh vocabulary: when a player first gets an item, they must say its Kazakh name aloud or lose it; an animal can't be hurt until its name is said.

## Language

**Word**:
The Kazakh name of one item or animal type, with its Latin transliteration, its meaning in English and Russian, and its Sample, taken from the curated Word List.
_Avoid_: Translation, label, name

**UI Language**:
The language of everything the player reads except Words and their Samples, following the player's Minecraft language setting (English, Russian or Kazakh). It never changes what is learned: Words are always Kazakh.
_Avoid_: Locale, mode, learning language

**Word List**:
The hand-curated set of items and animal types that have a Word. Items and animals not on it never trigger a Challenge.
_Avoid_: Dictionary, vocabulary, lang file

**Challenge**:
The timed demand to say an item's or animal's Word, started the first time an item type on the Word List enters the player's inventory, or when the player hurts an animal on it whose Word is unlearned (the hit is cancelled).
_Avoid_: Quiz, test, prompt

**Sample**:
The recorded pronunciation of a Word, one audio file per Word shipped in the mod. It plays when a Challenge starts, on replay, and once more when the Word is learned; the mic is off while it plays.
_Avoid_: Clip, audio, TTS

**Challenge Window**:
The 10 seconds in which the player may try to say the Word as many times as they like.
_Avoid_: Timer, countdown

**Learned Word**:
A Word the player has said correctly once in the current world. Its items and animals never trigger a Challenge again for that player in that world. Each player learns on their own, and each new world starts with none.
_Avoid_: Known word, completed item

**Locked Item**:
An item in a player's inventory whose Challenge, for that player, is running or still waiting in the queue. The player can't use it until its Word is said. Locking belongs to the player, not the item: handed to someone who has Learned its Word, the same item is usable.
_Avoid_: Frozen item, disabled item

**Forfeit**:
What happens when a Challenge Window ends without the Word being said: the Locked Items of that Challenge are removed, and the Word stays unlearned.
_Avoid_: Deletion, penalty, fail

**Quest**:
A standing request to find an item of a given Word, shown either as the Word itself or only as its Sample, which plays when the player asks. Any item of that Word counts once it enters the inventory after the Quest appears and is not Locked, so an unlearned Word's Challenge must be won first. Words the player already holds, or whose Quest they have already completed, are never chosen. Quests follow one another for as long as the player plays.
_Avoid_: Mission, task, objective

**Race Quest**:
A Quest shared by everyone in the world, shown in red under their own Quest, which every player competes to finish. It is always shown by its Word, never only as a Sample. The first player to get an unlocked item of the Word wins it and alone gets the Reward; the others only see who won. Its Word is never one that an online player holds or that was already won. A new one follows after a random wait of a few minutes, and only starts while two or more players are online. A running one carries on when players leave, and a player who rejoins can still race it.
_Avoid_: Team Quest, Shared Quest

**Tier**:
How rare a Word's items are, set by hand on the Word List: common, uncommon, rare or legendary. A Word that mixes items of different rarity takes the Tier of its commonest item. The Tier sets a Quest's Time Limit and Reward.
_Avoid_: Rarity, level

**Time Limit**:
How long the player has to complete a Quest: longer for rarer Tiers.
_Avoid_: Timer, Challenge Window

**Reward**:
What completing a Quest gives: experience points, plus diamonds for legendary Words.
_Avoid_: Prize, loot

**Recall**:
A rare, timed demand to say a Learned Word that has a Tier, shown only by its item's icon: no Word, no Sample. It has the same 10 seconds and free tries as a Challenge, never overlaps one, and comes after a random 5 to 15 minutes of play. Saying the Word gives its Tier's experience points and plays its Sample; missing it only shows the Word and plays its Sample. The Word stays learned either way.
_Avoid_: Review, quiz, re-ask

**Archive**:
The player's own record of the Word List: every Word as a card, Learned ones in full and the rest as greyed silhouettes, split into Items and Animals, with how many are Learned and how many are left. On a server with other players it also ranks everyone by how many Words they have Learned, showing only names, counts and ranks.
_Avoid_: Dashboard, collection, journal
