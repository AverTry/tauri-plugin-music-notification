#!/bin/bash
# Build and sign the example Android APK.
# Usage:
#   ./build-android.sh
#   ./build-android.sh --target aarch64
#   ./build-android.sh --target armv7

set -e

# Configuration
ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/android}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-33.0.1}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
KEYSTORE="$ANDROID_HOME/release.keystore"
KEY_ALIAS="release"
KEY_STOREPASS="123456"
KEY_KEYPASS="123456"

# Project paths
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/"
SRC_TAURI="$FRONTEND_DIR/src-tauri"
APK_DIR="$SRC_TAURI/gen/android/app/build/outputs/apk"
ALIGNED_APK="/tmp/app-aligned.apk"
SIGNED_APK="/tmp/app-signed.apk"

echo "=== Music Notification Example Android Build Script ==="
echo "Project root: $PROJECT_ROOT"
echo "Android build tools: $BUILD_TOOLS"
echo

# Step 1: Build unsigned APK
echo "[1/5] Building APK with Tauri..."
cd "$FRONTEND_DIR"
if [ "$#" -eq 0 ]; then
    echo "  No target specified, building with Tauri defaults..."
    npx tauri android build
else
    echo "  Building with custom options: $*"
    npx tauri android build "$@"
fi

# Step 2: Locate the unsigned APK
echo
echo "[2/5] Locating built APK..."
UNSIGNED_APK="$(find "$APK_DIR" -name '*-release-unsigned.apk' | head -n 1)"
if [ -z "$UNSIGNED_APK" ]; then
    echo "ERROR: Could not find unsigned APK in $APK_DIR"
    exit 1
fi
echo "  Found: $UNSIGNED_APK"

# Step 3: Generate keystore if needed
echo
echo "[3/5] Checking keystore..."
if [ ! -f "$KEYSTORE" ]; then
    echo "  Generating new keystore..."
    keytool -genkey -v -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KEY_STOREPASS" -keypass "$KEY_KEYPASS" \
        -dname "CN=Kaulan,O=Kaulan,C=US"
else
    echo "  Using existing keystore: $KEYSTORE"
fi

rm -f $ALIGNED_APK $SIGNED_APK

# Step 4: Zipalign
echo
echo "[4/5] Zipaligning APK..."
"$BUILD_TOOLS/zipalign" -v -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

# Step 5: Sign APK
echo
echo "[5/5] Signing APK..."
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$KEY_STOREPASS" \
    --key-pass "pass:$KEY_KEYPASS" \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

# Done
echo
echo "=== Build Complete ==="
echo "Signed APK: $SIGNED_APK"
echo
echo "To install, run:"
echo "  adb install $SIGNED_APK"
echo

read -r -p "Install to device now? [y/N] " INSTALL
if [ "$INSTALL" = "y" ] || [ "$INSTALL" = "Y" ]; then
    adb install "$SIGNED_APK"
    echo "Installation complete!"
fi
