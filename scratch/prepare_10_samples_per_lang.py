import os
import sys
import urllib.request
import tarfile

sys.stdout.reconfigure(encoding='utf-8')

BASE_DIR = r"C:\Projects\iTantra\app\build\indic_eval"
TARGET_COUNT = 10

fleurs_langs = [
    ("Hindi", "hi_in"),
    ("Gujarati", "gu_in"),
    ("Marathi", "mr_in"),
    ("Kannada", "kn_in"),
    ("Malayalam", "ml_in"),
    ("Tamil", "ta_in"),
    ("Telugu", "te_in"),
    ("Odia", "or_in"),
    ("Bengali", "bn_in")
]

for lang_name, code in fleurs_langs:
    lang_dir = os.path.join(BASE_DIR, code)
    os.makedirs(lang_dir, exist_ok=True)
    
    tsv_path = os.path.join(lang_dir, "test.tsv")
    if not os.path.exists(tsv_path):
        tsv_url = f"https://huggingface.co/datasets/google/fleurs/raw/main/data/{code}/test.tsv"
        print(f"Downloading TSV for {lang_name} ({code})...")
        req = urllib.request.Request(tsv_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=30) as resp:
            with open(tsv_path, "wb") as out_f:
                out_f.write(resp.read())
                
    wav_files = [f for f in os.listdir(lang_dir) if f.endswith(".wav")]
    if len(wav_files) < TARGET_COUNT:
        print(f"Streaming {code} audio tar.gz to get {TARGET_COUNT} samples (currently have {len(wav_files)})...")
        tar_url = f"https://huggingface.co/datasets/google/fleurs/resolve/main/data/{code}/audio/test.tar.gz"
        req = urllib.request.Request(tar_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=90) as resp:
            with tarfile.open(fileobj=resp, mode='r|gz') as tar:
                count = len(wav_files)
                for member in tar:
                    if member.name.endswith('.wav'):
                        fname = os.path.basename(member.name)
                        out_p = os.path.join(lang_dir, fname)
                        if not os.path.exists(out_p):
                            f = tar.extractfile(member)
                            with open(out_p, "wb") as wf:
                                wf.write(f.read())
                            count += 1
                            if count >= TARGET_COUNT:
                                break
                                
    wav_files = [f for f in os.listdir(lang_dir) if f.endswith(".wav")]
    print(f"Ready: {lang_name} ({code}) has {len(wav_files)} WAV files.")

# Now for English (LibriSpeech test-clean samples)
en_dir = os.path.join(BASE_DIR, "en_us")
os.makedirs(en_dir, exist_ok=True)
en_tsv = os.path.join(en_dir, "test.tsv")

# We can fetch sample LibriSpeech utterances or LibriSpeech mini
# If test.tsv for en doesn't exist, we can create it from LibriSpeech or FLEURS en_us!
# Note: Google FLEURS also has en_us!
fleurs_en_tsv = os.path.join(en_dir, "test.tsv")
if not os.path.exists(fleurs_en_tsv):
    tsv_url = "https://huggingface.co/datasets/google/fleurs/raw/main/data/en_us/test.tsv"
    print("Downloading TSV for English (en_us)...")
    req = urllib.request.Request(tsv_url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        with open(fleurs_en_tsv, "wb") as out_f:
            out_f.write(resp.read())

en_wav_files = [f for f in os.listdir(en_dir) if f.endswith(".wav")]
if len(en_wav_files) < TARGET_COUNT:
    print(f"Streaming English (en_us) audio tar.gz to get {TARGET_COUNT} samples...")
    tar_url = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/en_us/audio/test.tar.gz"
    req = urllib.request.Request(tar_url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=90) as resp:
        with tarfile.open(fileobj=resp, mode='r|gz') as tar:
            count = len(en_wav_files)
            for member in tar:
                if member.name.endswith('.wav'):
                    fname = os.path.basename(member.name)
                    out_p = os.path.join(en_dir, fname)
                    if not os.path.exists(out_p):
                        f = tar.extractfile(member)
                        with open(out_p, "wb") as wf:
                            wf.write(f.read())
                        count += 1
                        if count >= TARGET_COUNT:
                            break

print(f"Ready: English (en_us) has {len([f for f in os.listdir(en_dir) if f.endswith('.wav')])} WAV files.")
print("\nPhase 1: Dataset collection complete across all 10 languages!")
