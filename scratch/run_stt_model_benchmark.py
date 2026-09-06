import os
import sys
import time
import json
import re
import gc
import psutil
import numpy as np
from scipy.io import wavfile
import sherpa_onnx

sys.stdout.reconfigure(encoding='utf-8')

def levenshtein(a, b):
    dp = [[0] * (len(b) + 1) for _ in range(len(a) + 1)]
    for i in range(len(a) + 1): dp[i][0] = i
    for j in range(len(b) + 1): dp[0][j] = j
    for i in range(1, len(a) + 1):
        for j in range(1, len(b) + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
    return dp[len(a)][len(b)]

def calc_wer_cer(ref, hyp, is_english=False):
    if is_english:
        clean_ref = re.sub(r'[,.?!\"\'\-_;:()]', ' ', ref).strip().lower()
        clean_hyp = re.sub(r'[,.?!\"\'\-_;:()]', ' ', hyp).strip().lower()
    else:
        # Strip punctuation, keep native Indic unicode
        clean_ref = re.sub(r'[।,.?!\"\'\-_;:()a-zA-Z0-9]', ' ', ref).strip()
        clean_hyp = re.sub(r'[।,.?!\"\'\-_;:()a-zA-Z0-9]', ' ', hyp).strip()
        
    clean_ref = re.sub(r'\s+', ' ', clean_ref)
    clean_hyp = re.sub(r'\s+', ' ', clean_hyp)
    
    r_words = clean_ref.split()
    h_words = clean_hyp.split()
    wer = (levenshtein(r_words, h_words) / max(1, len(r_words))) * 100.0
    
    r_chars = [c for c in clean_ref if not c.isspace()]
    h_chars = [c for c in clean_hyp if not c.isspace()]
    cer = (levenshtein(r_chars, h_chars) / max(1, len(r_chars))) * 100.0
    
    return wer, cer

# Paths
DOLPHIN_MODEL = r"C:\Projects\iTantra\app\build\dolphin\model.int8.onnx"
DOLPHIN_TOKENS = r"C:\Projects\iTantra\app\build\dolphin\tokens.txt"

INDIC_DIR = r"C:\Projects\iTantra\app\build\indicconformer"
INDIC_TOKENS = os.path.join(INDIC_DIR, "tokens.txt")

WHISPER_ENCODER = r"C:\Projects\iTantra\app\src\main\assets\models\stt\whisper-tiny\tiny-encoder.int8.onnx"
WHISPER_DECODER = r"C:\Projects\iTantra\app\src\main\assets\models\stt\whisper-tiny\tiny-decoder.int8.onnx"
WHISPER_TOKENS = r"C:\Projects\iTantra\app\src\main\assets\models\stt\whisper-tiny\tiny-tokens.txt"

EVAL_BASE = r"C:\Projects\iTantra\app\build\indic_eval"

# Languages to evaluate
LANGUAGES = [
    ("English", "en_us", "en"),
    ("Kannada", "kn_in", "kn"),
    ("Malayalam", "ml_in", "ml"),
    ("Gujarati", "gu_in", "gu"),
    ("Hindi", "hi_in", "hi"),
    ("Bengali", "bn_in", "bn"),
    ("Tamil", "ta_in", "ta"),
    ("Telugu", "te_in", "te"),
    ("Marathi", "mr_in", "mr"),
    ("Odia", "or_in", "or")
]

process = psutil.Process()

def get_ram_mb():
    return process.memory_info().rss / 1024 / 1024

all_results = {}

for lang_name, eval_code, short_code in LANGUAGES:
    print("\n" + "=" * 80)
    print(f"EVALUATING: {lang_name.upper()} ({eval_code})")
    print("=" * 80)
    
    lang_dir = os.path.join(EVAL_BASE, eval_code)
    tsv_path = os.path.join(lang_dir, "test.tsv")
    
    if not os.path.exists(tsv_path):
        print(f"Error: {tsv_path} not found! Skipping {lang_name}")
        continue
        
    refs = {}
    with open(tsv_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split("\t")
            if len(parts) >= 4:
                refs[parts[1]] = parts[3]
                
    wav_files = sorted([f for f in os.listdir(lang_dir) if f.endswith(".wav")])[:10]
    print(f"Found {len(wav_files)} WAV files for {lang_name}")
    
    lang_eval = {}
    
    # -------------------------------------------------------------
    # Candidate 1: AI4Bharat IndicConformer (where available)
    # -------------------------------------------------------------
    indic_mpath = os.path.join(INDIC_DIR, short_code, "model.int8.onnx")
    if os.path.exists(indic_mpath):
        print(f"\n--- Testing Candidate: AI4Bharat IndicConformer NeMo CTC ({short_code}) ---")
        gc.collect()
        ram_before = get_ram_mb()
        rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=indic_mpath,
            tokens=INDIC_TOKENS,
            num_threads=4,
            sample_rate=16000,
            decoding_method="greedy_search"
        )
        ram_after = get_ram_mb()
        model_ram = max(0, ram_after - ram_before)
        model_size_mb = os.path.getsize(indic_mpath) / 1024 / 1024
        
        c_wers, c_cers, c_latencies, c_durations, c_records = [], [], [], [], []
        for wf in wav_files:
            wp = os.path.join(lang_dir, wf)
            ref_text = refs.get(wf, "")
            sr, audio = wavfile.read(wp)
            if audio.dtype != np.float32:
                audio = audio.astype(np.float32) / 32768.0
            dur = len(audio) / sr
            
            t0 = time.time()
            stream = rec.create_stream()
            stream.accept_waveform(sr, audio)
            rec.decode_stream(stream)
            hyp_text = stream.result.text
            lat_ms = (time.time() - t0) * 1000.0
            
            wer, cer = calc_wer_cer(ref_text, hyp_text, is_english=(short_code == "en"))
            c_wers.append(wer)
            c_cers.append(cer)
            c_latencies.append(lat_ms)
            c_durations.append(dur)
            c_records.append({
                "file": wf,
                "ref": ref_text,
                "hyp": hyp_text,
                "wer": round(wer, 2),
                "cer": round(cer, 2),
                "latency_ms": round(lat_ms, 1),
                "duration_s": round(dur, 2)
            })
            
        mean_wer = np.mean(c_wers)
        mean_cer = np.mean(c_cers)
        mean_lat = np.mean(c_latencies)
        mean_rtf = (mean_lat / 1000.0) / np.mean(c_durations)
        print(f"  [IndicConformer] Mean WER: {mean_wer:.1f}% | Mean CER: {mean_cer:.1f}% | Latency: {mean_lat:.0f}ms | RTF: {mean_rtf:.3f}x | Size: {model_size_mb:.1f}MB")
        lang_eval["IndicConformer"] = {
            "model_type": "AI4Bharat IndicConformer NeMo CTC INT8",
            "mean_wer": round(mean_wer, 2),
            "mean_cer": round(mean_cer, 2),
            "mean_latency_ms": round(mean_lat, 1),
            "mean_rtf": round(mean_rtf, 3),
            "model_size_mb": round(model_size_mb, 1),
            "ram_mb": round(model_ram, 1),
            "records": c_records
        }
        del rec
        gc.collect()

    # -------------------------------------------------------------
    # Candidate 2: Dolphin CTC INT8 (for non-English)
    # -------------------------------------------------------------
    if short_code != "en" and os.path.exists(DOLPHIN_MODEL):
        print(f"\n--- Testing Candidate: Dolphin Small Multi-Lang CTC INT8 ---")
        gc.collect()
        ram_before = get_ram_mb()
        rec = sherpa_onnx.OfflineRecognizer.from_dolphin_ctc(
            model=DOLPHIN_MODEL,
            tokens=DOLPHIN_TOKENS,
            num_threads=4,
            sample_rate=16000,
            decoding_method="greedy_search"
        )
        ram_after = get_ram_mb()
        model_ram = max(0, ram_after - ram_before)
        model_size_mb = os.path.getsize(DOLPHIN_MODEL) / 1024 / 1024
        
        c_wers, c_cers, c_latencies, c_durations, c_records = [], [], [], [], []
        for wf in wav_files:
            wp = os.path.join(lang_dir, wf)
            ref_text = refs.get(wf, "")
            sr, audio = wavfile.read(wp)
            if audio.dtype != np.float32:
                audio = audio.astype(np.float32) / 32768.0
            dur = len(audio) / sr
            
            t0 = time.time()
            stream = rec.create_stream()
            stream.accept_waveform(sr, audio)
            rec.decode_stream(stream)
            hyp_text = stream.result.text
            lat_ms = (time.time() - t0) * 1000.0
            
            wer, cer = calc_wer_cer(ref_text, hyp_text, is_english=False)
            c_wers.append(wer)
            c_cers.append(cer)
            c_latencies.append(lat_ms)
            c_durations.append(dur)
            c_records.append({
                "file": wf,
                "ref": ref_text,
                "hyp": hyp_text,
                "wer": round(wer, 2),
                "cer": round(cer, 2),
                "latency_ms": round(lat_ms, 1),
                "duration_s": round(dur, 2)
            })
            
        mean_wer = np.mean(c_wers)
        mean_cer = np.mean(c_cers)
        mean_lat = np.mean(c_latencies)
        mean_rtf = (mean_lat / 1000.0) / np.mean(c_durations)
        print(f"  [Dolphin CTC] Mean WER: {mean_wer:.1f}% | Mean CER: {mean_cer:.1f}% | Latency: {mean_lat:.0f}ms | RTF: {mean_rtf:.3f}x | Size: {model_size_mb:.1f}MB")
        lang_eval["DolphinCTC"] = {
            "model_type": "Dolphin Small Multi-Lang CTC INT8",
            "mean_wer": round(mean_wer, 2),
            "mean_cer": round(mean_cer, 2),
            "mean_latency_ms": round(mean_lat, 1),
            "mean_rtf": round(mean_rtf, 3),
            "model_size_mb": round(model_size_mb, 1),
            "ram_mb": round(model_ram, 1),
            "records": c_records
        }
        del rec
        gc.collect()

    # -------------------------------------------------------------
    # Candidate 3: Whisper-Tiny Multilingual INT8
    # -------------------------------------------------------------
    if os.path.exists(WHISPER_ENCODER) and os.path.exists(WHISPER_DECODER):
        print(f"\n--- Testing Candidate: Whisper-Tiny Multilingual INT8 ---")
        gc.collect()
        ram_before = get_ram_mb()
        rec = sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=WHISPER_ENCODER,
            decoder=WHISPER_DECODER,
            tokens=WHISPER_TOKENS,
            num_threads=4,
            language=short_code if short_code != "or" else "hi", # whisper has no or
            task="transcribe"
        )
        ram_after = get_ram_mb()
        model_ram = max(0, ram_after - ram_before)
        model_size_mb = (os.path.getsize(WHISPER_ENCODER) + os.path.getsize(WHISPER_DECODER)) / 1024 / 1024
        
        c_wers, c_cers, c_latencies, c_durations, c_records = [], [], [], [], []
        for wf in wav_files:
            wp = os.path.join(lang_dir, wf)
            ref_text = refs.get(wf, "")
            sr, audio = wavfile.read(wp)
            if audio.dtype != np.float32:
                audio = audio.astype(np.float32) / 32768.0
            dur = len(audio) / sr
            
            t0 = time.time()
            stream = rec.create_stream()
            stream.accept_waveform(sr, audio)
            rec.decode_stream(stream)
            hyp_text = stream.result.text
            lat_ms = (time.time() - t0) * 1000.0
            
            wer, cer = calc_wer_cer(ref_text, hyp_text, is_english=(short_code == "en"))
            c_wers.append(wer)
            c_cers.append(cer)
            c_latencies.append(lat_ms)
            c_durations.append(dur)
            c_records.append({
                "file": wf,
                "ref": ref_text,
                "hyp": hyp_text,
                "wer": round(wer, 2),
                "cer": round(cer, 2),
                "latency_ms": round(lat_ms, 1),
                "duration_s": round(dur, 2)
            })
            
        mean_wer = np.mean(c_wers)
        mean_cer = np.mean(c_cers)
        mean_lat = np.mean(c_latencies)
        mean_rtf = (mean_lat / 1000.0) / np.mean(c_durations)
        print(f"  [Whisper-Tiny] Mean WER: {mean_wer:.1f}% | Mean CER: {mean_cer:.1f}% | Latency: {mean_lat:.0f}ms | RTF: {mean_rtf:.3f}x | Size: {model_size_mb:.1f}MB")
        lang_eval["WhisperTiny"] = {
            "model_type": "OpenAI Whisper-Tiny Multilingual INT8",
            "mean_wer": round(mean_wer, 2),
            "mean_cer": round(mean_cer, 2),
            "mean_latency_ms": round(mean_lat, 1),
            "mean_rtf": round(mean_rtf, 3),
            "model_size_mb": round(model_size_mb, 1),
            "ram_mb": round(model_ram, 1),
            "records": c_records
        }
        del rec
        gc.collect()

    all_results[lang_name] = lang_eval

# Save detailed JSON report
out_json = r"C:\Projects\iTantra\app\build\stt_multi_model_benchmark_results.json"
with open(out_json, "w", encoding="utf-8") as f:
    json.dump(all_results, f, ensure_ascii=False, indent=2)
print(f"\nFull benchmark results saved to {out_json}")
