import subprocess
import os

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "RZCY9396AGX"
EVAL_BASE = r"C:\Projects\iTantra\app\build\indic_eval"

audio_map = {
    "hi_in": "/data/local/tmp/test_hindi.wav",
    "bn_in": "/data/local/tmp/test_bn_in.wav",
    "ta_in": "/data/local/tmp/test_ta_in.wav",
    "te_in": "/data/local/tmp/test_te_in.wav",
    "mr_in": "/data/local/tmp/test_mr_in.wav",
    "gu_in": "/data/local/tmp/test_gu_in.wav",
    "or_in": "/data/local/tmp/test_or_in.wav",
    "ml_in": "/data/local/tmp/test_ml_in.wav",
    "kn_in": "/data/local/tmp/test_kn_in.wav",
    "en_us": "/data/local/tmp/test_en_us.wav"
}

for code, dest in audio_map.items():
    lang_dir = os.path.join(EVAL_BASE, code)
    wavs = sorted([f for f in os.listdir(lang_dir) if f.endswith(".wav")])
    if wavs:
        src = os.path.join(lang_dir, wavs[0])
        print(f"Pushing {src} -> {dest}...")
        subprocess.run([ADB, "-s", DEV, "push", src, dest])

res = subprocess.run([ADB, "-s", DEV, "shell", "ls -lh /data/local/tmp/test_*.wav"], capture_output=True, text=True)
print("\nAudio files on phone:\n", res.stdout)
