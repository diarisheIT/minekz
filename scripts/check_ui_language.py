#!/usr/bin/env python3
"""Fails if a Word List entry has no Russian meaning, or if the ru_ru / kk_kz lang files don't have exactly the en_us keys.

Run from the repo root: python3 scripts/check_ui_language.py
"""
import json, sys

LANG = 'src/main/resources/assets/minekz/lang/%s.json'
problems = [f"no Russian meaning: {e['cyrillic']}"
            for e in json.load(open('src/main/resources/data/minekz/word_list.json', encoding='utf-8'))
            if not e.get('meaning_ru', '').strip()]
english = set(json.load(open(LANG % 'en_us', encoding='utf-8')))
for code in ('ru_ru', 'kk_kz'):
    keys = set(json.load(open(LANG % code, encoding='utf-8')))
    problems += [f'{code} lacks {k}' for k in sorted(english - keys)] + [f'{code} has extra {k}' for k in sorted(keys - english)]
print('\n'.join(problems) or 'ok')
sys.exit(bool(problems))
