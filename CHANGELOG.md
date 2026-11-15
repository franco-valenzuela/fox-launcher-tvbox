# Changelog

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
