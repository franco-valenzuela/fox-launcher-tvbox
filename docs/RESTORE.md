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

Older Android: the block lives in the boot script. See point 5.

## 5. The boot script (memory fix, compile fix, advertising block)

These three live as marked blocks at the end of one system script. The original script was copied to your computer before it was changed. To undo all three, put the original back:

```
adb root
adb connect <IP of the box>:5555
adb remount
adb push <your saved copy of the script> <its path on the box>
adb shell "chown root:root <its path>; chmod 750 <its path>; sync; reboot"
```

To undo only one of them, remove only its block. Each block starts with a comment line that says what it is.

## 6. Time zone

Set it again from the box: Settings, Date and time.

## 7. Picture size

Put back the value that was saved before the change. On the test box it was `overscan 95,96,95,96`:

```
adb shell setprop persist.sys.overscan.main "overscan 95,96,95,96"
```

Then restart the box. The box reads this value only when the HDMI link starts.

## What a factory reset does

A factory reset from the box's own Settings undoes points 1, 2, 3, 4, 6 and 7. It also removes Fox Launcher and brings the factory home screen back.

It does **not** undo point 5, because that change is in the system area. Use the steps in point 5 for that.

## If the box does not answer

Unplug it, wait 10 seconds, plug it in again. Then connect again.
