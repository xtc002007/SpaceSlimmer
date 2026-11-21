import os
import re

java_dir = 'android/app/src/main/java'
hardcoded = []
for root, dirs, files in os.walk(java_dir):
    for f in files:
        if not f.endswith('.kt'):
            continue
        path = os.path.join(root, f)
        with open(path, 'r', encoding='utf-8') as src:
            for idx, line in enumerate(src, 1):
                s = line.strip()
                if s.startswith('//') or s.startswith('*'):
                    continue
                matches = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
                for m in matches:
                    if re.search(r'[\u4e00-\u9fff]', m):
                        hardcoded.append((path, idx, m))

print(f"Total hardcoded strings found: {len(hardcoded)}")
for p, i, m in hardcoded:
    print(f"{p}:{i} -> {m}")
