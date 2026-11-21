path = r'd:\Workspace\SpaceSlimmer\android\app\build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('versionCode = 3', 'versionCode = 4')
text = text.replace('versionName = "0.1.1"', 'versionName = "0.2.0"')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print('Successfully bumped versionCode to 4 and versionName to 0.2.0')
