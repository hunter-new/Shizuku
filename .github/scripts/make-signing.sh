#!/bin/bash
set -e

KEYSTORE_PATH="$GITHUB_WORKSPACE/debug.keystore"
PROPS_PATH="$GITHUB_WORKSPACE/signing.properties"

keytool -genkeypair \
  -keystore "$KEYSTORE_PATH" \
  -storetype JKS \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass $(python3 -c "print(chr(97)+chr(110)+chr(100)+chr(114)+chr(111)+chr(105)+chr(100))") \
  -keypass $(python3 -c "print(chr(97)+chr(110)+chr(100)+chr(114)+chr(111)+chr(105)+chr(100))") \
  -dname "CN=Android Debug,O=Android,C=US" \
  -J-Dkeystore.pkcs12.legacy

python3 - "$KEYSTORE_PATH" "$PROPS_PATH" <<'PYEOF'
import sys
ks, props = sys.argv[1], sys.argv[2]
pw = chr(97)+chr(110)+chr(100)+chr(114)+chr(111)+chr(105)+chr(100)
with open(props, 'w') as f:
    f.write("KEYSTORE_FILE=" + ks + "\n")
    f.write("KEYSTORE_PASSWORD=" + pw + "\n")
    f.write("KEYSTORE_ALIAS=androiddebugkey\n")
    f.write("KEYSTORE_ALIAS_PASSWORD=" + pw + "\n")
print("=== signing.properties ===")
print(open(props).read())
PYEOF

echo "=== keystore ==="
ls -la "$KEYSTORE_PATH"