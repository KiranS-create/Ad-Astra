import subprocess
import os

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "RZCY9396AGX"

def push_binary(local_path, remote_path):
    print(f"Pushing binary {local_path} ({os.path.getsize(local_path)} bytes) -> {remote_path}...")
    with open(local_path, "rb") as f:
        p = subprocess.Popen([ADB, "-s", DEV, "exec-in", f"run-as org.sih.itantra sh -c 'cat > {remote_path}'"], stdin=f)
        p.wait()
    res = subprocess.run([ADB, "-s", DEV, "shell", f"run-as org.sih.itantra ls -lh {remote_path}"], capture_output=True, text=True)
    print("Result:", res.stdout.strip())

# 1. IndicConformer tokens.txt
push_binary(r"C:\Projects\iTantra\app\build\indicconformer\tokens.txt", "files/models/stt/indicconformer/tokens.txt")

# 2. Dolphin tokens.txt
push_binary(r"C:\Projects\iTantra\app\build\dolphin\tokens.txt", "files/models/stt/dolphin/tokens.txt")

# 3. Verify head of IndicConformer tokens
p = subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra head -n 10 files/models/stt/indicconformer/tokens.txt"], capture_output=True)
print("\nFirst 10 lines raw bytes:\n", p.stdout.decode('utf-8', errors='replace'))
