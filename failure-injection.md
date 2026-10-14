# Phase 1 failure injection checklist

Automated coverage runs in CI via `packaging/run-quality-gate.ps1` (Rust tests + release build + `.so` size).

## Automated (CI / local)

| Case | Coverage |
|------|----------|
| Cancel while not Running ignored | `ss-jobs` + `ss-core` scheduler tests |
| Disk full halts dispatch | `ss-jobs::disk_full_halt_stops_dispatch_until_resume` |
| Crash recovery clears `m4a` tmp | `ss-jobs::crash_recovery_cleans_audio_tmp_suffix` |
| Settings skip thresholds | `ss-core` `decide_respects_settings_skip_disabled` |
| Image encode cancel flag | `ss-image` `compress_image_honours_cancel_callback` |
| Release `.so` < 15 MB | `packaging/check-so-size.ps1` |

## Manual on device (`docs/smoke-checklist.md`)

Sign-off table columns in `docs/07-phase1-signoff.md` → **Failure injection (manual)**.

| # | Case | Pass criteria | Signed (Y/N) |
|---|------|---------------|--------------|
| 1 | Corrupt file | Job fails; original untouched | |
| 2 | No write permission | `replace_not_writable`; no silent save-as | |
| 3 | Cancel mid-encode | Failed/Cancelled; no Succeeded | |
| 4 | HEVC fallback | H.264 output plays if HW HEVC fails | |
| 5 | Disk full | Queue stops; resume after free space | |
| 6 | 50-photo memory | Peak recorded in 07 §4; no OOM | |
| 7 | Kill process | Queued remain; running → Queued | |

Steps (detail):

1. **Corrupt file** — pick a truncated JPEG/MP4; job fails, original untouched.
2. **No write permission** — Photo Picker URI + replace policy; UI shows `replace_not_writable`, no silent save-as.
3. **Cancel mid-encode** — start video batch, cancel; job Failed/Cancelled, no Succeeded.
4. **HEVC fallback** — on HEVC-capable device, force incompatible recipe or use sample; output plays as H.264 if HW HEVC fails.
5. **Disk full** — fill storage until encode fails; queue stops, completed jobs kept, resume after free space.
6. **50-photo memory** — Android Profiler: batch 50 JPEGs, note peak heap; no OOM on target device.
7. **Kill process** — mid-batch force-stop app; reopen, queued jobs remain, running jobs back to Queued.

Record results in `docs/07-phase1-signoff.md` before changing status from **内测候选**.
