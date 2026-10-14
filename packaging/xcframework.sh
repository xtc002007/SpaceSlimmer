#!/usr/bin/env bash
# Build SsCore.xcframework from rust/crates/ss-core (macOS + Xcode only).
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: iOS XCFramework builds require macOS with Xcode."
  echo "Install rustup targets aarch64-apple-ios and aarch64-apple-ios-sim, then re-run on a Mac."
  echo "See ios/README.md."
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST="$ROOT/rust"
OUT_DIR="$ROOT/ios/CoreIOS"
XCFRAMEWORK="$OUT_DIR/SsCore.xcframework"
GEN_DIR="$OUT_DIR/Generated"
IOS_TARGET="aarch64-apple-ios"
SIM_TARGET="aarch64-apple-ios-sim"

cd "$RUST"

rustup target add "$IOS_TARGET" "$SIM_TARGET" >/dev/null 2>&1 || true

echo "Building ss-core for $IOS_TARGET (release)..."
cargo build -p ss-core --release --target "$IOS_TARGET"
echo "Building ss-core for $SIM_TARGET (release)..."
cargo build -p ss-core --release --target "$SIM_TARGET"

IOS_LIB="$RUST/target/$IOS_TARGET/release/libss_core.a"
SIM_LIB="$RUST/target/$SIM_TARGET/release/libss_core.a"

if [[ ! -f "$IOS_LIB" ]]; then
  echo "error: expected static library at $IOS_LIB"
  exit 1
fi
if [[ ! -f "$SIM_LIB" ]]; then
  echo "error: expected static library at $SIM_LIB"
  exit 1
fi

mkdir -p "$GEN_DIR"
echo "Generating Swift bindings..."
cargo run -p uniffi-bindgen -- generate \
  --library "$IOS_LIB" \
  --language swift \
  --out-dir "$GEN_DIR"

rm -rf "$XCFRAMEWORK"
echo "Creating XCFramework..."
xcodebuild -create-xcframework \
  -library "$IOS_LIB" -headers "$GEN_DIR" \
  -library "$SIM_LIB" -headers "$GEN_DIR" \
  -output "$XCFRAMEWORK"

echo "Wrote $XCFRAMEWORK"
