#!/bin/bash
set -e

mkdir -p ~/.android

# Generate a legacy-compatible JKS debug keystore at the standard Android location
# AGP's signingConfigs.debug.storeFile points here, so signing.gradle else-branch works
keytool -genkeypair \
  -keystore ~/.android/debug.keystore \
  -storetype JKS \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US" \
  -J-Dkeystore.pkcs12.legacy

echo '=== debug.keystore created ==='
ls -la ~/.android/debug.keystore

# Remove any stale signing.properties so signing.gradle uses else branch -> debug keystore
rm -f "$GITHUB_WORKSPACE/signing.properties"
