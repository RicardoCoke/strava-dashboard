#!/bin/sh
# Installs the Wi-Fi watchdog on the Pi. Run on the Pi, next to wifi-watchdog.sh: sudo sh install-wifi-watchdog.sh
# Remove again: sudo systemctl disable --now wifi-watchdog.timer
set -eu

install -m 755 "$(dirname "$0")/wifi-watchdog.sh" /usr/local/bin/wifi-watchdog

cat >/etc/systemd/system/wifi-watchdog.service <<'EOF'
[Unit]
Description=Restart Wi-Fi or reboot when the router stops answering

[Service]
Type=oneshot
ExecStart=/usr/local/bin/wifi-watchdog
EOF

cat >/etc/systemd/system/wifi-watchdog.timer <<'EOF'
[Unit]
Description=Check the Wi-Fi link every 2 minutes

[Timer]
OnBootSec=3min
OnUnitActiveSec=2min

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now wifi-watchdog.timer
systemctl list-timers wifi-watchdog.timer --no-pager
