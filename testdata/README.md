# Phase 1 manual test fixtures



Large binaries are **not** committed. Generate fixtures locally before device smoke:



```powershell

# Windows

.\packaging\prepare-testdata.ps1

# Optional: 10-minute WAV for Sprint 4 acceptance

.\packaging\prepare-testdata.ps1 -LongAudio

```



```bash

# Linux / macOS (requires ffmpeg for video)

./packaging/prepare-testdata.sh

./packaging/prepare-testdata.sh --long-audio

```



Push to a connected API 26+ device:



```powershell

.\packaging\push-testdata-to-device.ps1

```



Then run `docs/smoke-checklist.md` and record manual failure-injection items from `docs/failure-injection.md` in `docs/07-phase1-signoff.md`.



## Layout (after prepare)



```

testdata/

  photo/large.jpg          # >500KB JPEG (skip/estimate)

  photo/small.jpg          # minimal JPEG

  photo/alpha.png          # PNG with alpha

  video/h264_1080p.mp4     # H.264 1080p (if ffmpeg installed)

  audio/speech.wav         # 5s mono WAV

  audio/wav_long.wav       # 30s default, 600s with -LongAudio

  corrupt/truncated.jpg    # failure-injection corrupt file

```



## Profiler (50-photo batch)



Use Android Studio Profiler while compressing 50 picks from `photo/large.jpg` copies or the device gallery. Target: no OOM on your reference device.


