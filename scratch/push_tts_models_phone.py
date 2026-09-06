import subprocess
import os

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "RZCY9396AGX"
TTS_BASE = r"C:\Projects\iTantra\app\src\main\assets\models\tts"

print("Ensuring files/models/tts exists in phone sandbox...")
subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra mkdir -p files/models/tts"])

print("Streaming updated TTS models to physical phone via cd and tar -xf -...")
p_tar = subprocess.Popen(["tar.exe", "-cf", "-", "-C", TTS_BASE, "."], stdout=subprocess.PIPE)
p_adb = subprocess.Popen([ADB, "-s", DEV, "exec-in", "run-as org.sih.itantra sh -c 'cd files/models/tts && tar -xf -'"], stdin=p_tar.stdout)
p_tar.stdout.close()
p_adb.communicate()
p_tar.wait()

res = subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra ls -la files/models/tts"], capture_output=True, text=True)
print("\nUpdated TTS models on phone:\n", res.stdout)

res_mr = subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra ls -lh files/models/tts/vits-piper-mr"], capture_output=True, text=True)
print("\nMarathi TTS files on phone:\n", res_mr.stdout)
