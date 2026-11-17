# Fox Launcher TV Box: blocks to add at the END of a script that the box already runs as root at
# every start. On the test box that script is /system/bin/install-recovery.sh, started by the
# line "service flash_recovery /system/bin/install-recovery.sh" in /init.rc.
#
# This file is a template, not something to run on its own. Copy only the blocks the owner chose.
# Before you touch the box's script: save a copy of it on the computer, check the new version
# with "sh -n", and compare its hash after you write it. See ROOT-FIXES.md in this folder.
#
# To undo a block, delete it from the script. To undo everything, put the saved copy back.

# --- Block 1: compressed memory (zram) ------------------------------------------------------
# For boxes with 2 GB of memory or less whose swap never starts. Use an algorithm that
# /sys/block/zram0/comp_algorithm really lists; the test box only has lzo. The size is in bytes:
# about 40% of the real memory (419430400 = 400 MB for a 1 GB box).
if ! grep -q zram0 /proc/swaps; then
  echo 1 > /sys/block/zram0/reset
  echo lzo > /sys/block/zram0/comp_algorithm
  echo 419430400 > /sys/block/zram0/disksize
  mkswap /dev/block/zram0
  swapon /dev/block/zram0
fi

# --- Block 2: let the app compiler finish ---------------------------------------------------
# For boxes whose log shows "dex2oat did not finish after ... seconds" at every start. Without
# the time limit the work finishes once and is kept. Two threads keep its memory use down.
setprop dalvik.vm.dex2oat-flags --no-watch-dog
setprop dalvik.vm.dex2oat-threads 2

# --- Block 3: advertising block for Android older than 9 ------------------------------------
# Sends every address lookup (port 53) to AdGuard DNS, whatever Wi-Fi the box joins. Android 9
# and newer have a "Private DNS" setting instead; use that there and leave this block out.
# It waits until the network service has its tables ready. Lookups over IPv6 are not redirected.
n=0
until iptables -t nat -L OUTPUT -n >/dev/null 2>&1 || [ $n -ge 30 ]; do sleep 2; n=$((n+1)); done
sleep 20
for proto in udp tcp; do
  iptables -t nat -C OUTPUT -p $proto --dport 53 -j DNAT --to-destination 94.140.14.14:53 2>/dev/null || iptables -t nat -A OUTPUT -p $proto --dport 53 -j DNAT --to-destination 94.140.14.14:53
done

# --- Block 4: free memory when the box is idle ----------------------------------------------
# Optional, and it must be the LAST block: it never returns. Once a minute it checks three
# things: the remote was not used, no sound is playing, and less than 40 MB is free. When all
# three hold it asks Android to drop the background processes it can spare ("am kill-all").
# The app on screen and anything that is playing are never touched. It acts once per idle
# period, so a box left alone is not swept every minute.
# "rk_pwm_irq" is the infrared receiver of Rockchip boxes; on another box use the line of
# /proc/interrupts that grows when a remote key is pressed.
last=""
done=0
while true; do
  sleep 60
  now=$(grep rk_pwm_irq /proc/interrupts)
  if [ "$now" != "$last" ] || grep -q RUNNING /proc/asound/card*/pcm*p/sub*/status 2>/dev/null; then
    done=0
  elif [ $done -eq 0 ]; then
    set -- $(grep MemAvailable /proc/meminfo)
    if [ "$2" -lt 40960 ]; then am kill-all; fi
    done=1
  fi
  last=$now
done
