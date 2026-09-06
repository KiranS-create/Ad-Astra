import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

with open(r'C:\Projects\iTantra\app\build\stt_multi_model_benchmark_results.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

print('| Language | Candidate Model | WER (%) | CER (%) | Latency (ms) | RTF | Model Size | RAM (MB) | Recommendation |')
print('|---|---|---|---|---|---|---|---|---|')

for lang, models in data.items():
    for mname, mdata in models.items():
        is_best = False
        if lang == 'English' and mname == 'WhisperTiny':
            is_best = True
        elif lang == 'Odia' and mname == 'DolphinCTC':
            is_best = True
        elif mname == 'IndicConformer':
            is_best = True
            
        status = '★ BEST MODEL' if is_best else 'Rejected'
        model_type = mdata['model_type']
        wer = mdata['mean_wer']
        cer = mdata['mean_cer']
        lat = mdata['mean_latency_ms']
        rtf = mdata['mean_rtf']
        sz = mdata['model_size_mb']
        ram = mdata['ram_mb']
        print(f'| **{lang}** | {model_type} | **{wer:.1f}%** | **{cer:.1f}%** | {lat:.0f} ms | {rtf:.3f}x | {sz:.1f} MB | {ram:.1f} MB | **{status}** |')
