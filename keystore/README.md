# CodeX Release Keystore

This directory contains the release keystore for signing APKs.

## Keystore Details

- **File**: `codex-release.jks`
- **Store Password**: `codex2024`
- **Key Alias**: `codex`
- **Key Password**: `codex2024`

## Generate Keystore (if missing)

Run the following command to generate a new keystore:

```bash
keytool -genkey -v \
  -keystore codex-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias codex \
  -storepass codex2024 \
  -keypass codex2024 \
  -dname "CN=CodeX, OU=Mobile Development, O=CodeX, L=Unknown, ST=Unknown, C=US"
```

## Security Note

This keystore is publicly available for this open-source project.
For production apps with sensitive data, keep your keystore private.
