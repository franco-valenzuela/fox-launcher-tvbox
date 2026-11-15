# Check that an APK is the official one

An APK is the file that installs an app. Anyone can make a file with the same name. These two checks tell you whether the file you have is the real Fox Launcher from this project.

Your AI assistant can do both for you. Ask: *"Verify foxlauncher.apk as docs/VERIFY.md says."*

## Check 1. The file was not changed

Every release page lists a long code called **SHA-256**. It is like a fingerprint of the file.

Windows (PowerShell):

```
Get-FileHash foxlauncher.apk -Algorithm SHA256
```

macOS or Linux:

```
shasum -a 256 foxlauncher.apk
```

The code you get must be the same as the one on the [release page](https://github.com/franco-valenzuela/fox-launcher-tvbox/releases). If one character is different, do not install the file.

## Check 2. The file was signed by this project

Every official APK is signed with the same certificate. Its fingerprint is:

```
SHA-256  D8:8E:1C:FC:F6:D3:EB:79:2F:FA:E8:B9:0F:59:38:59:E4:AE:BB:0A:8F:9F:6F:FC:F3:5C:BB:6A:58:53:9B:86
```

To see the certificate of a file, use `apksigner`, which comes with the Android build tools:

```
apksigner verify --print-certs foxlauncher.apk
```

Look at the line `Signer #1 certificate SHA-256 digest`. It shows the same letters and numbers without the `:` marks. They must match.

An "update" with a different certificate is a warning sign. Android itself refuses to install it over the official one.

## If you build it yourself

An APK you build from the source code is signed with **your own** key, not with the project's. That is normal. Its certificate will be different, and so will its SHA-256. See [BUILD.md](BUILD.md).

## What the app asks for

You can read this in [`launcher/AndroidManifest.xml`](../launcher/AndroidManifest.xml). Three permissions, and no Internet. The list and the reasons are in [SECURITY.md](../SECURITY.md).
