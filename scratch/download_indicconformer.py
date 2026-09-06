import os
import sys
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')

DEST_DIR = r"C:\Projects\iTantra\app\build\indicconformer"
os.makedirs(DEST_DIR, exist_ok=True)

BASE_URL = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"

# 1. Download shared tokens.txt
tokens_path = os.path.join(DEST_DIR, "tokens.txt")
if not os.path.exists(tokens_path):
    print("Downloading IndicConformer shared tokens.txt...")
    req = urllib.request.Request(f"{BASE_URL}/tokens.txt", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        with open(tokens_path, "wb") as f:
            f.write(resp.read())
    print(f"Saved {tokens_path} ({os.path.getsize(tokens_path)} bytes)")

# 2. Download candidate models
# We start with Kannada, Malayalam, Gujarati, and Hindi
langs_to_download = ["kn", "ml", "gu", "hi"]

for l in langs_to_download:
    ldir = os.path.join(DEST_DIR, l)
    os.makedirs(ldir, exist_ok=True)
    mpath = os.path.join(ldir, "model.int8.onnx")
    if not os.path.exists(mpath):
        print(f"Downloading IndicConformer {l}/model.int8.onnx (~188 MB)...")
        murl = f"{BASE_URL}/{l}/model.int8.onnx"
        req = urllib.request.Request(murl, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as resp:
            with open(mpath, "wb") as f:
                # stream in 1MB chunks
                downloaded = 0
                while True:
                    chunk = resp.read(1024 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
                    downloaded += len(chunk)
                    if downloaded % (20 * 1024 * 1024) == 0:
                        print(f"  {l}: {downloaded / 1024 / 1024:.1f} MB downloaded...")
        print(f"Saved {mpath} ({os.path.getsize(mpath)} bytes)")
    else:
        print(f"Already exists: {mpath} ({os.path.getsize(mpath)} bytes)")

print("\nIndicConformer candidate models download complete!")
