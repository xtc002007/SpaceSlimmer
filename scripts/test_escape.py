import re

with open('android/app/src/main/res/values/strings.xml', 'r', encoding='utf-8') as f:
    text = f.read()

for m in re.finditer(r'<string name="([^"]+)">([^<]*\'[^<]*)</string>', text):
    print(m.group(1), '->', m.group(2))
