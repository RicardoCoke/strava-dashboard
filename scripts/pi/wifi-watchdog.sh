#!/bin/sh
# Heals a Pi that is running but has dropped off Wi-Fi. Run as root every 2 minutes by wifi-watchdog.timer.
# Router unreachable twice in a row (~4 min): restart NetworkManager. Five times (~10 min): reboot.

fails_file=/run/wifi-watchdog.fails # /run is cleared on boot, so the count starts over
gateway=$(ip route show default | awk '{print $3; exit}')

if [ -n "$gateway" ] && ping -c 3 -W 3 "$gateway" >/dev/null 2>&1; then
  rm -f "$fails_file"
  exit 0
fi

fails=$(($(cat "$fails_file" 2>/dev/null || echo 0) + 1))
echo "$fails" >"$fails_file"
logger -t wifi-watchdog "router unreachable, $fails check(s) in a row"

if [ "$fails" -eq 2 ]; then
  logger -t wifi-watchdog "restarting NetworkManager"
  systemctl restart NetworkManager
# Only after an hour of uptime, so a long router outage means one reboot an hour, not one every 10 minutes.
elif [ "$fails" -ge 5 ] && [ "$(cut -d. -f1 /proc/uptime)" -gt 3600 ]; then
  logger -t wifi-watchdog "rebooting"
  systemctl reboot
fi
