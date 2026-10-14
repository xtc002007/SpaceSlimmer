# Phase 1 device smoke checklist

Status: **内测候选** — run on a physical Android device (API 26+) before claiming sign-off.

Automated gate (Rust tests + release `.so` size): `packaging/run-quality-gate.ps1`.  
Device helper: `packaging/run-device-smoke.ps1` (push fixtures, install APK, `connectedDebugAndroidTest`).  
Instrumentation subset (JPEG batch, GIF/DNG skip, WAV→Opus, HEVC skip, short MP4): `SmokeInstrumentedTest.kt`.  
Failure injection matrix: `docs/failure-injection.md`.

## Setup

1. Automated gate (local/CI): `packaging/run-quality-gate.ps1`
2. Generate fixtures: `packaging/prepare-testdata.ps1` (see `testdata/README.md`)
3. Push to device: `packaging/push-testdata-to-device.ps1` (or pick from gallery)
4. Build release APK: `cd android && ./gradlew :app:assembleRelease` (Linux) or `gradlew.bat` (Windows)
5. Grant notifications (Android 13+) and media read permissions when prompted
6. Manual failure injection: `docs/failure-injection.md` § Manual — record in `docs/07-phase1-signoff.md`

## Photo

- [ ] Pick 3+ JPEGs, preset estimate shows byte range
- [ ] Batch compress completes; result compare slider works
- [ ] Save-as-new writes to gallery
- [ ] Replace-to-vault: vault entry created, original overwritten when writable
- [ ] Replace mode: result/tasks/home show **gallery saved** and **vault usage** together
- [ ] Replace blocked with message when URI not writable
- [ ] Pause/resume/cancel during image batch
- [ ] Failed job retry succeeds

## Video

- [ ] Pick 1080p H.264; balanced preset transcodes with progress
- [ ] HEVC fallback to H.264 on unsupported device
- [ ] `audio_copy` recipe copies audio when efficient
- [ ] FPS cap applied when recipe requests lower fps
- [ ] Video compare player plays before/after
- [ ] Cancel stops Transformer; job does not succeed after cancel

## Audio

- [ ] Pick WAV; speech preset produces smaller M4A
- [ ] Mono downmix on stereo speech source
- [ ] Cancel mid-encode does not corrupt output

## Product

- [ ] Settings skip thresholds change skip behaviour after save
- [ ] Default video preset / compat from settings used on preset screen
- [ ] Language switch (zh/en) applies after save + restart activity
- [ ] Space radar scans; mixed queue submits one group with per-item overrides
- [ ] Vault restore writes back to original URI
- [ ] Home shows expiring vault reminder when applicable
- [ ] Tasks screen shows groups; retry on failed jobs

## Quality gate

- [ ] `cargo test --workspace` green
- [ ] `packaging/check-so-size.ps1` passes on release `.so`
- [ ] No original file modified on failure/cancel (verify with read-only copy)
