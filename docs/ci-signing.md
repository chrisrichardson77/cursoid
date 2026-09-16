# Signing keys for CI

The [build workflow](../.github/workflows/build.yml) signs release artifacts with keys supplied as
repository secrets. Without them, every push still tests, lints and builds a debug APK — only
tagged releases need the keys.

Keys are deliberately not in the repository, so they have to be generated once and stored. Note
that the key used for the APKs committed under `dist/` no longer exists: it lived on an ephemeral
build machine that has since been wiped. A build signed with a new key cannot update those installs
in place, so uninstall an older Cursoid once before installing a CI-built one.

## Current state

The four sideload secrets are already set on this repository, so tagging a release works today. That
key is an ECDSA P-256 pair generated for CI, and it exists only inside GitHub Actions secrets —
there is no copy anywhere else, and secrets cannot be read back out. Releases signed with it carry
this certificate:

```
CN=Cursoid, O=Cursoid, C=GB
SHA-256  876a017a2e386498dbc214e64d1ed416758fb28f500f25b2e38db83ea9920328
```

That is checkable against any downloaded APK with `apksigner verify --print-certs`. If you would
rather hold the key yourself, generate a new one below and overwrite the secrets; the only cost is
uninstalling Cursoid once, because the signature will no longer match. The Play upload key is not
configured, and is worth generating on your own machine rather than in CI so a published app never
depends on a key you cannot reach.

## Generate the keys

Two keys, because Play will not accept the same one that signs sideloads. Run this on a machine you
control, not in CI:

```bash
# Sideload key: ECDSA P-256, small signatures, fine for direct installs.
keytool -genkeypair -v \
  -keystore cursoid-sideload.p12 -storetype PKCS12 \
  -alias cursoid -keyalg EC -groupname secp256r1 -validity 10000 \
  -dname "CN=Cursoid, O=Cursoid, C=GB"

# Play upload key: Play rejects EC, so this one is RSA 2048.
keytool -genkeypair -v \
  -keystore cursoid-upload.p12 -storetype PKCS12 \
  -alias cursoid-upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Cursoid, O=Cursoid, C=GB"
```

Back both up somewhere durable. Losing the sideload key only costs an uninstall, but losing the
Play upload key after publishing means asking Google to reset it.

## Store them as secrets

The workflow reads keystores as base64 so they survive being environment variables:

```bash
gh secret set SIGNING_KEYSTORE_BASE64 < <(base64 -w0 cursoid-sideload.p12)
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS      # cursoid
gh secret set SIGNING_KEY_PASSWORD
```

On macOS, `base64 -w0` is `base64` with no flag — the GNU line-wrap switch does not exist there.

The Play upload key is optional. Add it only when you want tagged builds to also produce an `.aab`:

```bash
gh secret set UPLOAD_KEYSTORE_BASE64 < <(base64 -w0 cursoid-upload.p12)
gh secret set UPLOAD_STORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS       # cursoid-upload
gh secret set UPLOAD_KEY_PASSWORD
```

| Secret | Required | Used for |
| --- | --- | --- |
| `SIGNING_KEYSTORE_BASE64` | yes, for tagged releases | Signing the release APK |
| `SIGNING_STORE_PASSWORD` | yes | Keystore password |
| `SIGNING_KEY_ALIAS` | yes | Key alias inside the keystore |
| `SIGNING_KEY_PASSWORD` | yes | Key password |
| `UPLOAD_KEYSTORE_BASE64` | no | Also build a Play `.aab` |
| `UPLOAD_STORE_PASSWORD` | no | Upload keystore password |
| `UPLOAD_KEY_ALIAS` | no | Upload key alias |
| `UPLOAD_KEY_PASSWORD` | no | Upload key password |

These map onto the `CURSOID_*` and `CURSOID_UPLOAD_*` environment variables that
[`app/build.gradle.kts`](../app/build.gradle.kts) already reads, which is the same mechanism a local
`keystore.properties` uses.

## Cut a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

That runs the tests, builds a signed APK, and publishes a GitHub Release with the APK attached, so
the phone can download it from the Releases page directly. Keep the tag in step with `versionName`
in `app/build.gradle.kts`, and raise `versionCode` for anything you expect to install over a
previous build.
