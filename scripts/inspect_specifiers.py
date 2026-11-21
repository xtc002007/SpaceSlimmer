import json

with open('scripts/missing_130.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

for k, v in data.items():
    if '%' in v['en']:
        en = v['en']
        zh = v['zh']
        print(f"{k}:\n   EN: {en}\n   ZH: {zh}")
