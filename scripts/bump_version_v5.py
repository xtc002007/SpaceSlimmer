import re

file_path = r'android/app/build.gradle.kts'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5', content)
content = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.2.1"', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Bumped to versionCode = 5, versionName = '0.2.1'")
