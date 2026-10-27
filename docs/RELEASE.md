# Release, signing key, auto-update

## The signing key (one time)

Android only installs an update signed with the same key as the installed app: **never change it**,
never commit it.

```bash
keytool -genkeypair -v -keystore cerbere.jks -alias cerbere \
  -keyalg RSA -keysize 4096 -validity 36500 -storetype JKS
base64 -w0 cerbere.jks > cerbere.jks.b64        # macOS: base64 -i cerbere.jks -o cerbere.jks.b64
```

GitHub → Settings → Environments → `release` (deployment branches: `main` only), secrets:

| Secret | Value |
|---|---|
| `CERBERE_KEYSTORE_BASE64` | content of `cerbere.jks.b64` |
| `CERBERE_KEYSTORE_PASSWORD` | keystore password |
| `CERBERE_KEY_PASSWORD` | key password (the same if keytool's default was kept) |

Keep `cerbere.jks` and both passwords somewhere safe (password manager): losing them means
uninstalling the app to install a build signed with a new key.

## CI

`.github/workflows/build-apk.yml`: unit tests, `assembleRelease` signed with the key above, upload
as an artifact, then the rolling `latest` release (deleted and recreated, title `Cerbère build N`,
N = run number = versionCode). On `main` a missing key fails the build; pull requests never see the
key and never publish.

## Auto-update

`suite/SuiteUpdater` reads the `latest` release, compares its build number with the installed
versionCode, downloads the APK (size and SHA-256 checked, package name verified) and opens Android's
installer from the update banner.
