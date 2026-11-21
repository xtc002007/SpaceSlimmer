import json

with open(r'C:\Users\Administrator\.gemini\antigravity\brain\3d925f2b-14c5-42b7-a11d-f75f7e4dc768\.system_generated\logs\transcript_full.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        idx = data.get('step_index')
        if idx in [968, 969, 970, 971, 972, 973]:
            content = data.get('content') or ''
            calls = data.get('tool_calls') or []
            print(f"STEP {idx}: calls={len(calls)}, content_len={len(content)}")
            for c in calls:
                print("  TOOL:", c['name'], c['args'])
            if 'dialog' in content.lower() or 'modal' in content.lower() or 'ref=e' in content:
                for cl in content.split('\n'):
                    if any(k in cl for k in ['dialog', 'modal', '提交', '确认', 'ref=e']):
                        print("  LINE:", cl[:120])
