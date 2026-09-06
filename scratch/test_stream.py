import subprocess, os

ADB = r"C:\Users\kiran akash\AppData\Local\Android\Sdk\platform-tools\adb.exe"
dev = "RZCY9396AGX"
src = r"C:\Projects\iTantra\app\build\indicconformer\hi\model.int8.onnx"
target_dir = "files/models/stt/indicconformer/hi"
target_file = f"{target_dir}/model.int8.onnx"

subprocess.run([ADB, "-s", dev, "shell", f"run-as org.sih.itantra mkdir -p {target_dir}"])
print("Streaming Hindi model...")
with open(src, "rb") as f:
    subprocess.run([ADB, "-s", dev, "exec-in", f"run-as org.sih.itantra sh -c 'cat > {target_file}'"], stdin=f)

p = subprocess.run([ADB, "-s", dev, "shell", f"run-as org.sih.itantra ls -l {target_file}"], capture_output=True, text=True)
print("Remote file:", p.stdout.strip())
print("Local size: ", os.path.getsize(src))
