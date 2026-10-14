# Memory profiling — 50 JPEG batch (Phase 1)

Record peak native heap during a 50-photo compression batch and enter the value in [07-phase1-signoff.md](../docs/07-phase1-signoff.md).

## Prerequisites

- Physical Android device (API 26+), USB debugging enabled
- Android Studio with Profiler
- Release or debug APK with Rust core built (`packaging/run-quality-gate.ps1` or `assembleDebug`)

## Steps

1. **Prepare photos** — On device, pick 50 JPEGs (≥2 MP each) via the app Photo Picker, or push fixtures:
   ```powershell
   cd packaging
   .\prepare-testdata.ps1
   .\push-testdata-to-device.ps1
   ```
2. **Attach Profiler** — Android Studio → **View → Tool Windows → Profiler** → select the device → **+** → **SpaceSlimmer** process.
3. **Memory track** — Open the **Memory** row → enable **Record native allocations** if offered (API 28+).
4. **Run batch** — In the app: Photo → select 50 JPEGs → preset **高清** or **小体积** → **开始压缩**. Do not background the app during the run.
5. **Capture peak** — Note the highest **Native** / **Total** heap point during the batch (not after idle). Screenshot optional.
6. **Sign off** — Add a row under § Memory in `docs/07-phase1-signoff.md`:

   | Metric | Value | Device | Date |
   |--------|-------|--------|------|
   | Peak native heap (50 JPEG batch) | _MB_ | _model / API_ | _YYYY-MM-DD_ |

## Pass criteria (guidance)

- No OOM or process kill during the batch
- Peak native heap stable enough for mid-range devices (team target: document actual; hard cap TBD after first device matrix)

## Related

- Automated gate: `packaging/run-quality-gate.ps1`
- Device smoke helper: `packaging/run-device-smoke.ps1`
- Sprint 7 plan: [06-phase1-development-plan.md](../docs/06-phase1-development-plan.md)
