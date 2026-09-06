import subprocess
import os
import sys

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "RZCY9396AGX"
INDIC_BASE = r"C:\Projects\iTantra\app\build\indicconformer"
DOLPHIN_BASE = r"C:\Projects\iTantra\app\build\dolphin"

def run_cmd(cmd_list, stdin=None):
    return subprocess.run(cmd_list, stdin=stdin, capture_output=True, text=True)

def push_dir_tar(src_parent, folder_name, remote_dest):
    print(f"Transferring {folder_name} to {remote_dest}...")
    subprocess.run([ADB, "-s", DEV, "shell", f"run-as org.sih.itantra mkdir -p {remote_dest}"])
    
    # Run tar on host and pipe stdout directly to adb exec-in stdin
    p_tar = subprocess.Popen(["tar.exe", "-cf", "-", "-C", src_parent, folder_name], stdout=subprocess.PIPE)
    p_adb = subprocess.Popen([ADB, "-s", DEV, "exec-in", f"run-as org.sih.itantra tar -xf - -C {remote_dest}"], stdin=p_tar.stdout)
    p_tar.stdout.close()
    p_adb.communicate()
    p_tar.wait()
    
    # Verify
    chk = subprocess.run([ADB, "-s", DEV, "shell", f"run-as org.sih.itantra ls -lh {remote_dest}/{folder_name}"], capture_output=True, text=True)
    print("Remote result:", chk.stdout.strip() or chk.stderr.strip())

# 1. Push mr, ta, te
for lang in ["mr", "ta", "te"]:
    push_dir_tar(INDIC_BASE, lang, "files/models/stt/indicconformer")

# 2. Push dolphin
push_dir_tar(r"C:\Projects\iTantra\app\build", "dolphin", "files/models/stt")

# 3. Check all models in indicconformer
res = subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra ls -lh files/models/stt/indicconformer"], capture_output=True, text=True)
print("\nIndicConformer status:\n", res.stdout)

res2 = subprocess.run([ADB, "-s", DEV, "shell", "run-as org.sih.itantra ls -lh files/models/stt/dolphin"], capture_output=True, text=True)
print("\nDolphin status:\n", res2.stdout)
