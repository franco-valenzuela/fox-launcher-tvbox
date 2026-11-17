# Changelog

## 2.2.0

The prompt no longer uses root. The APK did not change: it is the 2.1.0 file.

**The prompt**

- **No root, no system files.** Everything the prompt does is a setting or an app turned off, and a factory reset from Settings undoes all of it.
- The fixes that need root moved out of the prompt into a manual guide, [extras that need root](docs/root/ROOT-FIXES.md): compressed memory (zram), the compile loop at every power-on, the advertising block for Android older than 9 (iptables), turning the HDMI output off and on, and freeing memory when the box is idle. The start-up script template moved with them to `docs/root/boot-hook.sh`.
- The fixes are now A to F: factory apps, animations, advertising addresses (Android 9 and newer), clock, Fox Launcher, picture size. The picture size change shows after the next restart.
- `tools/privacy-scan` now also fails if `PROMPT.md` mentions root.

## 2.1.0

**Fox Launcher**

- **The time an app was used is no longer lost when the box is switched off.** Android only counts a session when the app leaves the screen, so a film watched until the box was unplugged counted as "less than 1 minute". The launcher now writes the open session down every minute and settles it at the next start. At most one minute is lost.
- **The name of the app in focus sits in its own framed box, centred**, with the usage line under it. It no longer hangs under the clock.

**The prompt**

- **New fix H: make the picture fill the screen.** Some boxes shrink everything they draw and leave a black border on all four sides. The prompt now checks for it, sets the picture back to full size, and explains that the change shows after a restart or after the HDMI output is turned off and on.
- The findings and the undo guide describe the same problem as it was on the test box.

## 2.0.0

First public release.

**The prompt**

- One prompt that connects to the box, saves a copy, checks it, reports in plain words and applies only the fixes the owner accepts.
- Fixes: turn off factory apps, compressed memory (zram), the compile loop at every power-on, animations, advertising addresses, the clock, and Fox Launcher.
- Safety rules: never uninstall, protected Android packages, a copy before every edit, a measurement before and after, an undo guide at the end.

**Fox Launcher**

- Clock with seconds and the date.
- Home row with up to five apps chosen by the owner. With one or two apps the cards stretch.
- List of all other apps, most used first.
- Name and usage of the app in focus: last opened, last closed, total time.
- Background in the colour of the app in focus.
- Menu on a half-second hold of OK: open, add to Home, replace, move, app info, hide, uninstall.
- Edit mode from the pencil: add, grab, move, take out.
- Key hints at the bottom that follow the focus.
- Buttons for Wi-Fi, free memory and the device settings. Free memory shown in colour when it is low.
- Spanish and English.
- No Internet permission. About 37 KB.

**Known limits**

- Tested on one box (RK3229, Android 7.1.2, 1 GB).
- English launcher texts were not seen on a real box.
- `launcher/build.ps1` is tested on Windows only.
