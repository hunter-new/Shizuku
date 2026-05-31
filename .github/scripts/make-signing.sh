#!/bin/bash
set -e

KEYSTORE_PATH="$GITHUB_WORKSPACE/debug.keystore"

keytool -genkeypair \
  -keystore "$KEYSTORE_PATH" \
  -storetype JKS \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US" \
  -J-Dkeystore.pkcs12.legacy

PROPS_PATH="$GITHUB_WORKSPACE/signing.properties"

echo "KEYSTORE_FILE=$KEYSTORE_PATH"    > "$PROPS_PATH"
echo "KEYSTORE_PASSWORD=android"       >> "$PROPS_PATH"
echo "KEYSTORE_ALIAS=androiddebugkey"  >> "$PROPS_PATH"
echo "KEYSTORE_ALIAS_PASSWORD=android" >> "$PROPS_PATH"

echo "=== signing.properties ==="
cat "$PROPS_PATH"
echo "=== keystore ==="
ls -la "$KEYSTORE_PATH"
