#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/testdata"
LONG_AUDIO=0
if [[ "${1:-}" == "--long-audio" ]]; then LONG_AUDIO=1; fi

mkdir -p "$OUT/photo" "$OUT/video" "$OUT/audio" "$OUT/corrupt"

# Minimal JPEG bytes (base64) padded to >500KB
python3 - "$OUT" "$LONG_AUDIO" <<'PY'
import base64, os, struct, sys
out, long_audio = sys.argv[1], int(sys.argv[2])
jpeg = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDAREAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCfAA//2Q=="
)
with open(os.path.join(out, "photo", "small.jpg"), "wb") as f:
    f.write(jpeg)
with open(os.path.join(out, "photo", "large.jpg"), "wb") as f:
    while f.tell() < 600 * 1024:
        f.write(jpeg)
with open(os.path.join(out, "corrupt", "truncated.jpg"), "wb") as f:
    f.write(jpeg[:200])
png = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
with open(os.path.join(out, "photo", "alpha.png"), "wb") as f:
    f.write(png)

def write_wav(path, seconds, rate=44100):
    channels, bits = 1, 16
    data_bytes = rate * seconds * channels * (bits // 8)
    with open(path, "wb") as f:
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + data_bytes))
        f.write(b"WAVEfmt ")
        f.write(struct.pack("<IHHIIHH", 16, channels, rate, rate * channels * (bits // 8), channels * (bits // 8), bits))
        f.write(b"data")
        f.write(struct.pack("<I", data_bytes))
        f.write(b"\x00" * data_bytes)

write_wav(os.path.join(out, "audio", "speech.wav"), 5)
write_wav(os.path.join(out, "audio", "wav_long.wav"), 600 if long_audio else 30)
PY

if command -v ffmpeg >/dev/null 2>&1; then
  ffmpeg -y -f lavfi -i "testsrc=size=1920x1080:rate=30" -t 3 -c:v libx264 -pix_fmt yuv420p "$OUT/video/h264_1080p.mp4"
else
  echo "ffmpeg not found — skip video/h264_1080p.mp4"
fi

echo "Done. Fixtures under $OUT"
