# Build Fox Launcher from the source code

You do not need this to use the project: each release has a ready APK. Build it yourself if you want to check the code first, or to change something.

Your AI assistant can do all of this. Ask: *"Build Fox Launcher as docs/BUILD.md says."*

## On Windows (tested)

1. Get the tools. This downloads a Java kit (JDK 17), the Android build-tools 34 and the Android 7.1 platform, about 320 MB, into a folder `.toolchain` inside the project. It checks each download.

   ```
   powershell -ExecutionPolicy Bypass -File tools\get-toolchain.ps1
   ```

2. Build.

   ```
   powershell -ExecutionPolicy Bypass -File launcher\build.ps1
   ```

3. The APK is `launcher\build\foxlauncher.apk`. The script prints its SHA-256.

4. Install it on the box.

   ```
   adb install -r launcher\build\foxlauncher.apk
   ```

The first build makes a signing key just for you, in `launcher\debug.keystore`. Keep that file: Android only accepts an update that is signed with the same key as the app already installed. Do not share it and do not put it in a repository.

## On macOS or Linux (not tested by the author)

`build.ps1` uses the Windows versions of the tools. The steps are the same on any system, with the tools for your system:

| Step | Tool | What it does |
|---|---|---|
| 1 | `aapt2 compile` | Compiles the `res` folder |
| 2 | `aapt2 link` | Makes the base APK from the manifest and the compiled resources. Minimum and target SDK 25 |
| 3 | `javac` | Compiles `launcher/src` as Java 8 against the `android.jar` of platform 25 |
| 4 | `d8` | Turns the classes into `classes.dex`, minimum API 25 |
| 5 | `aapt add` | Puts `classes.dex` into the APK |
| 6 | `zipalign` | Aligns the APK |
| 7 | `apksigner sign` | Signs it with your key |

There is no Gradle and no Android Studio project. The whole launcher is six Java files, one manifest and one icon.

## Sign with your own key

Set two environment variables before you run `build.ps1`:

- `FOX_KEYSTORE`: the path of your keystore file.
- `FOX_KEYSTORE_PASS_FILE`: the path of a text file that holds its password.

The key alias must be `debug`.
