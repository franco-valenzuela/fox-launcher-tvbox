# How to undo

Every change the prompt makes can be undone. The assistant writes a file like this one for **your** box, with your exact package names. This page is the model it follows.

You can ask your AI assistant: *"Undo everything using my RESTORE file."* Or run the commands yourself.

All commands start the same way. First connect:

```
adb connect <IP of the box>:5555
```

## 1. Turn factory apps on again

One app:

```
adb shell pm enable <package>
```

All of them: use the list the assistant saved (one package per line) and run the command for each line.

## 2. Go back to the factory home screen

```
adb shell pm enable <factory home package>
adb shell cmd package set-home-activity <factory home package>/<its activity>
adb uninstall com.foxlauncher.home
```

The assistant writes the two real names in your file. They were saved before any change.

## 3. Animations

Put back the three values that were saved. On the test box they were `0.5`:

```
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

## 4. Advertising block

Android 9 or newer:

```
adb shell settings put global private_dns_mode off
```

## 5. Time zone

Set it again from the box: Settings, Date and time.

## 6. Picture size

Put back the value that was saved before the change. On the test box it was `overscan 95,96,95,96`:

```
adb shell setprop persist.sys.overscan.main "overscan 95,96,95,96"
```

Then restart the box. The box reads this value only when the HDMI link starts.

## What a factory reset does

A factory reset from the box's own Settings undoes all the points above. It also removes Fox Launcher and brings the factory home screen back.

It does **not** undo the [extras that need root](root/ROOT-FIXES.md), if you did them by hand. Their undo steps are on that page.

## If the box does not answer

Unplug it, wait 10 seconds, plug it in again. Then connect again.
