# -*- coding: utf-8 -*-
import os
import re

JAVA_DIR = 'android/app/src/main/java'

out_lines = []
for root, dirs, files in os.walk(JAVA_DIR):
    for file in files:
        if not file.endswith('.kt'):
            continue
        filepath = os.path.join(root, file)
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        found = []
        for idx, line in enumerate(lines, 1):
            s = line.strip()
            if s.startswith('//') or s.startswith('*'):
                continue
            # Look for double-quoted strings with Chinese
            matches = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
            for m in matches:
                if re.search(r'[\u4e00-\u9fff]', m):
                    found.append((idx, m))
        
        if found:
            rel = os.path.relpath(filepath, '.')
            out_lines.append(f"=== {rel} ({len(found)} strings) ===")
            for idx, m in found:
                out_lines.append(f"  Line {idx}: {repr(m)}")

with open('hardcoded_report.txt', 'w', encoding='utf-8') as f:
    f.write("\n".join(out_lines))
print("Wrote hardcoded_report.txt with", len(out_lines), "lines.")
