# Extras that need root

These fixes are **not part of the prompt**. The prompt never uses root. This page is for people who know their way around `adb`, own a box whose firmware allows `adb root` (many cheap boxes ship debug firmware that does), and want to go further by hand.

Read this before you start:

- Every fix here changes the system area of the box. A factory reset from Settings does **not** undo it.
- Some of them add a block to a script that the box runs as root at every start. A mistake in that script can stop the box from starting properly.
- Changing system files can end your warranty.
- You do this at your own risk. There is no warranty. See the [licence](../../LICENSE).

The same safety rules as the prompt apply:

1. Only on a box you own.
2. Measure before and after every change.
3. Save a copy of every file on your computer before you change it.
4. Never uninstall anything.

The blocks for the start-up script are in [`boot-hook.sh`](boot-hook.sh). It is a template: copy only the blocks you need. Why each problem happens on the test box is explained in [what we found inside the box](../FINDINGS.md).

## Before you start: the start-up script

Fixes 1, 2, 3 and 5 can be made permanent by adding a block to a script that the system already runs as root at every start. On the test box that is `/system/bin/install-recovery.sh`, started by the line `service flash_recovery /system/bin/install-recovery.sh` in `/init.rc`.

1. `adb root`, then connect again.
2. Find such a script in `/init.rc` and the files it imports. If there is none, stop here. Do not rebuild the boot image and do not invent another hook.
3. Copy the script to your computer with `adb pull`. Keep this copy: it is your way back.
4. Add the block you want at the end of your copy. Check it with `sh -n`.
5. `adb remount`, then `adb push` the new version, and set its owner and mode as they were (`chown root:root`, `chmod 750` on the test box).
6. Compare the hash on the box with the hash of the file you sent. Then restart.

Undo: put the saved copy back.

```
adb root
adb connect <IP of the box>:5555
adb remount
adb push <your saved copy of the script> <its path on the box>
adb shell "chown root:root <its path>; chmod 750 <its path>; sync; reboot"
```

To undo only one fix, remove only its block. Each block starts with a comment line that says what it is.

## 1. Compressed memory (zram)

**When:** `/sys/block/zram0` exists, `/proc/swaps` is empty, and the real memory is 2 GB or less. On the test box the factory script asked for `lz4`, the kernel only had `lzo`, and swap never started (`dmesg`: `zram: Cannot initialise lz4`).

1. Read `/sys/block/zram0/comp_algorithm` and pick an algorithm it lists.
2. Try it for this session only: write `1` to `reset`, set the algorithm, set `disksize` to about 40% of the real memory, then `mkswap /dev/block/zram0` and `swapon /dev/block/zram0`.
3. Two minutes later, check the IOW figure in the first line of `top -n 1`. On the test box it went from 59% to 2%.
4. To keep it, add block 1 of `boot-hook.sh` to the start-up script.

## 2. Stop the compile loop

**When:** `logcat -d` shows `dex2oat did not finish after ... seconds` or `Failed to dexopt` for the same package after every restart. The compiler has a fixed time limit, the box is too slow to meet it, the work is thrown away, and it starts again at the next power-on.

1. `setprop dalvik.vm.dex2oat-flags --no-watch-dog`. With 1 GB of memory or less, also `setprop dalvik.vm.dex2oat-threads 2`.
2. Free memory first (fix 1 helps), then compile once: `cmd package compile -m interpret-only -f <package>`. Wait for `Success`. It can take several minutes.
3. To keep it, add block 2 of `boot-hook.sh` to the start-up script.
4. Restart. Two minutes later there must be no `dex2oat` process and no new `Failed to dexopt` line.

## 3. Advertising block on Android older than 9

Android 9 and newer have a "Private DNS" setting, and the prompt uses it without root. Older Android has nothing like it.

This redirects every address lookup (port 53, UDP and TCP) to AdGuard DNS at `94.140.14.14`, whatever Wi-Fi the box joins. It sends every address the box looks up to a service from another company, and it does not remove the advertising inside YouTube.

1. Add block 3 of `boot-hook.sh` to the start-up script. It waits until the network service has its tables ready before it adds the rules.
2. Restart. An advertising address such as `doubleclick.net` must resolve to `0.0.0.0` or `127.0.0.1`, and a normal site must still resolve. Lookups over IPv6 are not redirected.

Do **not** do this by giving the Wi-Fi a fixed IP address. On the test box, saving that form hung the Wi-Fi until the box was unplugged.

## 4. Show the full picture without a restart

The prompt sets `persist.sys.overscan.main` to `overscan 100,100,100,100`, and the change shows at the next power-on. With root you can see it at once by turning the HDMI output off and on:

```
echo 0 > /sys/class/display/HDMI/enable
echo 1 > /sys/class/display/HDMI/enable
```

The screen goes black for a few seconds. Do not do it while someone is watching something.

## 5. Free memory when the box is idle

Optional. Android already drops background apps when memory runs short, so the gain is small. On the test box, just after a start, it freed 2 MB. It helps more after hours of use. An app left in the background opens from zero the next time.

Once a minute the block checks three things: the remote was not used, no sound is playing, and less than 40 MB is free. When all three hold, it runs `am kill-all` once. The app on screen and anything that is playing are never touched.

1. Find the line in `/proc/interrupts` that grows when you press a key on the remote. On Rockchip boxes it is `rk_pwm_irq`. Use it in the block.
2. Run `am kill-all` by hand and compare `MemAvailable` in `/proc/meminfo` before and after.
3. Add block 4 of `boot-hook.sh` as the **last** block of the start-up script, because it never returns.
4. After a restart, the service that runs the script must show as `running`.

Undo: delete the block from the script and restart.
