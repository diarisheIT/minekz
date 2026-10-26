#!/usr/bin/env python3
"""Generates one pronunciation sample per Word with Azure Speech's kk-KZ-DauletNeural voice.

Use a paid pay-as-you-go (S0) Speech resource, not the free tier (F0) or edge-tts: the clips ship inside the mod.
The mod credits the voice as synthetic in fabric.mod.json.

    AZURE_SPEECH_KEY=... AZURE_SPEECH_REGION=westeurope python3 scripts/gen_samples.py

Writes src/main/resources/assets/minekz/samples/<sample>.wav (the "sample" name from word_list.json) and skips files
that exist, so re-running only fills gaps. To swap a bad clip for a real recording, replace that one file with a PCM
WAV of the same name; to regenerate it, delete it and re-run.
"""
import array, io, json, os, sys, urllib.request, wave
from xml.sax.saxutils import escape

WORDS, OUT = 'src/main/resources/data/minekz/word_list.json', 'src/main/resources/assets/minekz/samples'
VOICE = 'kk-KZ-DauletNeural'


def trim(audio):
    """Cuts Azure's trailing silence: the mic stays off while a sample plays, so dead air costs the player time."""
    src = wave.open(io.BytesIO(audio))
    pcm = array.array('h', src.readframes(src.getnframes()))
    loud = max(map(abs, pcm)) * 0.03
    end = max(i for i, x in enumerate(pcm) if abs(x) > loud) + src.getframerate() // 5  # keep 0.2 s of tail
    out = io.BytesIO()
    with wave.open(out, 'wb') as dst:
        dst.setparams(src.getparams())
        dst.writeframes(pcm[:end].tobytes())
    return out.getvalue()


key, region = os.environ['AZURE_SPEECH_KEY'], os.environ['AZURE_SPEECH_REGION']
os.makedirs(OUT, exist_ok=True)
made = 0
for entry in json.load(open(WORDS, encoding='utf-8')):
    path = os.path.join(OUT, entry['sample'] + '.wav')
    if os.path.exists(path):
        continue
    ssml = f"<speak version='1.0' xml:lang='kk-KZ'><voice name='{VOICE}'>{escape(entry['cyrillic'])}</voice></speak>"
    request = urllib.request.Request(f'https://{region}.tts.speech.microsoft.com/cognitiveservices/v1', ssml.encode(), {
        'Ocp-Apim-Subscription-Key': key, 'Content-Type': 'application/ssml+xml',
        'X-Microsoft-OutputFormat': 'riff-24khz-16bit-mono-pcm', 'User-Agent': 'minekz-gen-samples'})
    with urllib.request.urlopen(request) as response:
        audio = trim(response.read())
    with open(path, 'wb') as f:
        f.write(audio)
    made += 1
    print(made, entry['sample'], file=sys.stderr, flush=True)
print(made, 'samples written to', OUT, file=sys.stderr)
