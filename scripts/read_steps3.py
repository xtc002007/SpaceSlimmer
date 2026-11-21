import json

with open(r'C:\Users\Administrator\.gemini\antigravity\brain\3d925f2b-14c5-42b7-a11d-f75f7e4dc768\.system_generated\logs\transcript_full.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        idx = data.get('step_index')
        if idx in [966, 967, 968, 969, 970, 971, 972, 973]:
            print(f"=== STEP {idx} ===")
            print("CONTENT:", data.get('content'))
            print("THINKING:", data.get('thinking'))
            print("TOOL_CALLS:", data.get('tool_calls'))
