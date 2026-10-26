#!/usr/bin/env python3
"""Builds the Word List and the decoy list from Minecraft's Kazakh lang file (kk_kz.json).

Items and blocks; names are grouped by their last word, so every "... қайла" is one Word and dirt, coarse dirt and
farmland are all топырақ. Words the Vosk model doesn't know are dropped (they could never be recognised).

Decoys are words the grammar offers Vosk besides the Words, so wrong speech has somewhere else to land: common
Kazakh words plus every near miss of every Word (one letter changed, added, removed or swapped, e.g. тас -> бас)
that the model knows. Without near misses Vosk accepts "бас" as тас. A near miss that keeps stealing a correctly
said Word can be listed in scripts/decoys_remove.txt.

Run from the repo root with JDK 21 on PATH after one ./gradlew build (it needs the Vosk and JNA jars from Gradle's cache).
"""
import glob, json, os, re, subprocess, sys, tempfile
from collections import Counter, defaultdict

LANG, OUT, DECOYS, REMOVE = 'kk_kz.json', 'src/main/resources/data/minekz/word_list.json', 'src/main/resources/data/minekz/decoys.txt', \
    'scripts/decoys_remove.txt'
MODEL = 'prototypes/vosk-piper-PROTOTYPE/cache/vosk-model-small-kz-0.42'
COMMON = ['сәлем', 'рахмет', 'иә', 'жоқ', 'мен', 'сен', 'ол', 'біз', 'үй', 'су', 'ет', 'балық', 'қылыш', 'бала', 'ана',
          'әке', 'күн', 'ай', 'жер', 'аспан', 'от', 'жел', 'жаңбыр', 'қар', 'тау', 'өзен', 'көл', 'орман', 'гүл', 'жылқы',
          'сиыр', 'қой', 'ит', 'мысық', 'құс', 'қала', 'көше', 'мектеп', 'кітап', 'қалам', 'дос', 'жақсы', 'жаман',
          'үлкен', 'кішкентай', 'бір', 'екі', 'үш', 'төрт', 'бес']
ALPHABET, VOWELS = 'абвгғдеёжзийкқлмнңопөрстуұүфхһцчшщъыіьэюя', set('аәеёиоөуұүыіэюя')
SKIP = re.compile(r'spawn_egg|banner_pattern|music_disc|smithing_template|pottery_sherd|pottery_shard|^debug|^knowledge|^bundle|'
                  r'_boat$|_chest_boat$|potion|arrow|^filled_|^written_|^command_block|^structure|^jigsaw|^light$|^barrier')
# blocks that are never an inventory item (placed-only variants, fluids, crops, technical blocks)
BLOCK_SKIP = re.compile(r'^potted_|_wall_|^wall_|^attached_|_stem$|_plant$|^infested_|_cauldron$|_candle_cake$|'
                        r'^(air|cave_air|void_air|water|lava|fire|soul_fire|nether_portal|end_portal|end_gateway|'
                        r'moving_piston|piston_head|bubble_column|frosted_ice|redstone_wire|tripwire|carrots|potatoes|'
                        r'beetroots|cocoa|sweet_berry_bush|torchflower_crop|pitcher_crop|tall_seagrass|powder_snow|'
                        r'cave_vines|bamboo_sapling|spawner|trial_spawner|vault|end_portal_frame|reinforced_deepslate|'
                        r'petrified_oak_slab|budding_amethyst|farmland|set_spawn|dirt_path|test_block|test_instance_block)$')
# group words replaced by hand: logs read "<tree> бөренесі", but the word to learn is ағаш (tree, wood);
# жұмыртас (cobblestone) is unknown to the model
RENAME = {'бөрене': ('ағаш', 'wood'), 'бөренесі': ('ағаш', 'wood'), 'ағашы': ('ағаш', 'wood'), 'жұмыртас': ('тас', 'stone')}
# "<material> блок" is a block of that material: group it under the material (темір блок -> темір), not under "block"
GENERIC = {'блок', 'блогі', 'блогы'}

LAT = dict(zip('абвгғдеёжзийкқлмнңопөрстуұүфхһцчшщыіэюя',
               ['a','b','v','g','ğ','d','e','yo','j','z','i','y','k','q','l','m','n','ŋ','o','p','ö','r','s','t','w','u','ü',
                'f','x','h','ts','ç','ş','şç','ı','i','e','iu','ia']))
translit = lambda w: ''.join(LAT.get(c, '') for c in w)
# sample clip file names are plain ASCII: accented names can be stored decomposed (macOS) and then miss in the jar
ascii_name = lambda latin: latin.translate(str.maketrans({'ğ': 'gh', 'ş': 'sh', 'ŋ': 'ng', 'ı': 'i', 'ö': 'o', 'ü': 'u', 'ç': 'ch'}))



def known(words):
    """The subset of words the Vosk model's vocabulary contains."""
    jars = [glob.glob(os.path.expanduser(f'~/.gradle/caches/modules-2/files-2.1/{g}/**/{j}'), recursive=True)[0]
            for g, j in [('com.alphacephei', 'vosk-0.3.38.jar'), ('net.java.dev.jna', 'jna-5.13.0.jar')]]
    with tempfile.NamedTemporaryFile('w', encoding='utf-8', suffix='.txt', delete=False) as f:
        f.write('\n'.join(words))
    err = subprocess.run(['java', '-cp', ':'.join(jars), 'scripts/VocabCheck.java', MODEL, f.name],
                         capture_output=True, text=True, check=True).stderr
    os.unlink(f.name)
    return set(words) - set(re.findall(r"missing in vocabulary: '([^']*)'", err))


def near_misses(w):
    out = set()
    for i in range(len(w) + 1):
        for a in ALPHABET:
            out.add(w[:i] + a + w[i:])
            if i < len(w):
                out.add(w[:i] + a + w[i + 1:])
        if i < len(w):
            out.add(w[:i] + w[i + 1:])
        if i < len(w) - 1:
            out.add(w[:i] + w[i + 1] + w[i] + w[i + 2:])
    # not the Word with a suffix (темірі) or an и/ы for і spelling (темир): those are the Word said right
    same = lambda x: x.replace('и', 'і').replace('ы', 'і')
    return {n for n in out if set(n) & VOWELS and not n.startswith(w) and same(n) != same(w)}

names = {}  # item id -> Kazakh name; an item's own name wins over its block's
for key, name in json.load(open(LANG, encoding='utf-8')).items():
    m = re.fullmatch(r'(block|item)\.minecraft\.([a-z0-9_]+)', key)
    if m and not (SKIP if m.group(1) == 'item' else BLOCK_SKIP).search(m.group(2)):
        if m.group(1) == 'item' or m.group(2) not in names:
            names[m.group(2)] = name.strip().lower()



def bare_forms(w):
    """Dictionary forms of a possessive head noun, most likely first: сиыр еті -> ет, жапырағы -> жапырақ, бөтелкесі -> бөтелке."""
    forms = []
    if w[-1:] in 'ыі' and len(w) > 2:
        stem = w[:-1]
        forms.append(stem[:-1] + {'ғ': 'қ', 'г': 'к', 'б': 'п'}.get(stem[-1], stem[-1]))  # жапырағ -> жапырақ
        if w[-2] == 'с' and w[-3] in VOWELS:
            forms.append(w[:-2])  # бөтелке+сі
    return forms


named = {i: n for i, n in names.items() if re.fullmatch(r'[а-яәғқңөұүһіё]+( [а-яәғқңөұүһіё]+)*', n)}
heads = {}  # item id -> head noun of its name
for ident, name in named.items():
    parts = name.split()
    heads[ident] = parts[-2] if parts[-1] in GENERIC and len(parts) > 1 else parts[-1]
# only compound names carry the possessive (Шикі сиыр еті); one-word names such as тері or шыны keep their ending
compound = {i for i, n in named.items() if len(n.split()) > 1 and heads[i] == n.split()[-1]}
bare_vocab = known(sorted({f for i in compound for f in bare_forms(heads[i])}))
for ident in compound:
    heads[ident] = next((f for f in bare_forms(heads[ident]) if f in bare_vocab), heads[ident])

groups, meanings = defaultdict(list), {'ет': 'meat', 'қалқан': 'shield'}  # meanings fixed by hand where the most common item name is wrong
for ident in sorted(named):
    word, meaning = RENAME.get(heads[ident], (heads[ident], None))
    groups[word].append(ident)
    if meaning:
        meanings[word] = meaning

vocab = known(sorted(groups))
entries, dropped = [], []
for word, ids in sorted(groups.items()):
    if word not in vocab:
        dropped.append(word); continue
    meaning = meanings.get(word) or Counter(i.removesuffix('_block').split('_')[-1] for i in ids).most_common(1)[0][0]
    entries.append((['minecraft:' + i for i in ids], word, meaning))

out = [{'items': i, 'cyrillic': w, 'latin': translit(w), 'meaning': m, 'sample': ascii_name(translit(w))} for i, w, m in entries]
# Tiers, icons, Russian meanings and animal Words are set by hand: carry them over from the current list
old = json.load(open(OUT, encoding='utf-8')) if os.path.exists(OUT) else []
for key in ('tier', 'icon', 'meaning_ru'):
    kept = {e['cyrillic']: e[key] for e in old if key in e}
    for e in out:
        if e['cyrillic'] in kept:
            e[key] = kept[e['cyrillic']]
out += [e for e in old if 'animals' in e]
samples = [e['sample'] for e in out]
assert len(set(samples)) == len(samples) and all(s.isascii() and s.isalpha() for s in samples), 'sample names must be unique ASCII'
open(OUT, 'w', encoding='utf-8').write('[\n' + ',\n'.join('\t' + json.dumps(e, ensure_ascii=False) for e in out) + '\n]\n')

words = [e['cyrillic'] for e in out]
candidates = set().union(*(near_misses(w) for w in words)) - set(words)
removed = {l.split('#')[0].strip() for l in open(REMOVE, encoding='utf-8')} if os.path.exists(REMOVE) else set()
decoys = sorted((known(sorted(candidates)) | (set(COMMON) - set(words))) - removed)
open(DECOYS, 'w', encoding='utf-8').write('\n'.join(decoys) + '\n')
print(len(out), 'Words,', sum(len(e.get('items', [])) for e in out), 'items,', len(decoys), 'decoys; dropped (unknown to the model):',
      ' '.join(dropped), file=sys.stderr)
