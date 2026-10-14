#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST="$ROOT/rust"
JNI="$ROOT/android/core-android/src/main/jniLibs"
BINDGEN_OUT="$ROOT/android/core-android/build/generated/source/uniffi/debug/java"

export CMAKE_POLICY_VERSION_MINIMUM="${CMAKE_POLICY_VERSION_MINIMUM:-3.5}"
mkdir -p "$JNI"
cd "$RUST"
cargo ndk -t arm64-v8a -t x86_64 build --release -p ss-core --features opus-encode
SO="$JNI/arm64-v8a/libss_core.so"
mkdir -p "$BINDGEN_OUT"
cargo run -p uniffi-bindgen -- generate --library "$SO" --language kotlin --out-dir "$BINDGEN_OUT"
echo "Android libss_core.so and Kotlin bindings generated."
