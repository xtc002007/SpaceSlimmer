import json

with open(r'C:\Users\Administrator\.gemini\antigravity\brain\3d925f2b-14c5-42b7-a11d-f75f7e4dc768\.system_generated\logs\transcript_full.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        idx = data.get('step_index')
        if idx and 840 <= idx <= 860:
            print(f"=== STEP {idx} ({data.get('type')}) ===")
            if 'tool_calls' in data:
                print("TOOL_CALLS:", data['tool_calls'])
            if 'content' in data:
                print("CONTENT:", str(data['content'])[:300])
