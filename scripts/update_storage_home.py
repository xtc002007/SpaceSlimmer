import sys

file_path = r'android/app/src/main/java/com/spaceslimmer/app/ui/storage/StorageHomeScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """        color = if (hasFullAccess) {
            Color(0xFF10B981).copy(alpha = 0.12f)
        } else {
            Color(0xFFF59E0B).copy(alpha = 0.15f)
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hasFullAccess) Icons.Default.CheckCircle else Icons.Default.Security,
                contentDescription = null,
                tint = if (hasFullAccess) Color(0xFF059669) else Color(0xFFD97706),
                modifier = Modifier.size(28.dp),
            )"""

replacement = """        color = Color(0xFF10B981).copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(28.dp),
            )"""

if '\r\n' in content and '\r\n' not in target:
    target = target.replace('\n', '\r\n')
    replacement = replacement.replace('\n', '\r\n')

if target in content:
    content = content.replace(target, replacement, 1)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("SUCCESS: Colors and icons updated")
else:
    print("ERROR: Target not found")
    sys.exit(1)
