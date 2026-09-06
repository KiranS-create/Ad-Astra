import subprocess

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "RZCY9396AGX"
langs = ["bn", "gu", "hi", "kn", "ml", "mr", "ta", "te"]

for l in langs:
    cmd = [ADB, "-s", DEV, "shell", f"run-as org.sih.itantra stat -c '%s' files/models/stt/indicconformer/{l}/model.int8.onnx"]
    p = subprocess.run(cmd, capture_output=True, text=True)
    print(f"{l}: {p.stdout.strip()}")
