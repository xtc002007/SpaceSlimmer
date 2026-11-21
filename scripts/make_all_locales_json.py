# -*- coding: utf-8 -*-
import json
import glob
import os

all_data = {}
for path in glob.glob("store/locales/*.json"):
    loc = os.path.basename(path).replace(".json", "")
    with open(path, "r", encoding="utf-8") as f:
        d = json.load(f)
        all_data[loc] = {
            "name": d["name"],
            "short": d["short"],
            "full": d["full"]
        }

# Also map fr-CA to fr-FR
if "fr-FR" in all_data and "fr-CA" not in all_data:
    all_data["fr-CA"] = all_data["fr-FR"]

with open("scripts/all_locales_map.json", "w", encoding="utf-8") as f:
    json.dump(all_data, f, ensure_ascii=False, indent=2)

print(f"Generated scripts/all_locales_map.json with {len(all_data)} locales!")
