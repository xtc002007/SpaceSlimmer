import re

# 1. Update AndroidManifest.xml
manifest_path = r'android/app/src/main/AndroidManifest.xml'
with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = f.read()

# Remove READ_MEDIA permissions
manifest = re.sub(r'\r?\n\s*<uses-permission android:name="android\.permission\.READ_MEDIA_[A-Z]+" />', '', manifest)

with open(manifest_path, 'w', encoding='utf-8') as f:
    f.write(manifest)

print('Updated AndroidManifest.xml')

# 2. Update build.gradle.kts
gradle_path = r'android/app/build.gradle.kts'
with open(gradle_path, 'r', encoding='utf-8') as f:
    gradle = f.read()

gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 6', gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.2.2"', gradle)

with open(gradle_path, 'w', encoding='utf-8') as f:
    f.write(gradle)

print('Bumped build.gradle.kts to versionCode = 6, versionName = 0.2.2')
