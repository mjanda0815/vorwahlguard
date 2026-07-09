#!/usr/bin/env bash
#
# Point the WSL adb client at the adb server running on the Windows host.
# Source it, do not execute it:  source scripts/adb-env.sh
#
# Prerequisite on Windows (once per boot):
#   adb kill-server
#   adb -a -P 5037 nodaemon server
#
# With networkingMode=mirrored in .wslconfig the -a flag is unnecessary.

if ! grep -qi microsoft /proc/version 2>/dev/null; then
  echo "Not running under WSL — leaving ADB_SERVER_SOCKET alone." >&2
  return 0 2>/dev/null || exit 0
fi

# Mirrored networking shares the loopback interface with Windows.
if ip addr show lo 2>/dev/null | grep -q 'inet 127.0.0.1' && \
   timeout 1 bash -c 'exec 3<>/dev/tcp/127.0.0.1/5037' 2>/dev/null; then
  HOST_IP="127.0.0.1"
  MODE="mirrored"
else
  # NAT mode: Windows is the default gateway.
  HOST_IP="$(ip route show default 2>/dev/null | awk '{print $3; exit}')"
  MODE="nat"
fi

if [ -z "${HOST_IP}" ]; then
  echo "Could not determine the Windows host IP." >&2
  return 1 2>/dev/null || exit 1
fi

export ADB_SERVER_SOCKET="tcp:${HOST_IP}:5037"
echo "ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET}  (${MODE})"

if ! timeout 2 bash -c "exec 3<>/dev/tcp/${HOST_IP}/5037" 2>/dev/null; then
  cat >&2 <<MSG

Port 5037 on ${HOST_IP} is not reachable. Check, in this order:
  1. Is the adb server running on Windows?     adb -a -P 5037 nodaemon server
  2. Does the Windows firewall allow TCP 5037 from the WSL subnet?
  3. Do 'adb version' on both sides report the same platform-tools release?
     A mismatched client kills the remote server instead of talking to it.

See docs/WSL-ADB.md
MSG
  return 1 2>/dev/null || exit 1
fi

adb devices
