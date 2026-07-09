# Deploying to a physical device from WSL2

WSL2 is a virtual machine with its own kernel. It does **not** see USB devices attached to
Windows. Your phone is plugged into Windows; Ubuntu knows nothing about it.

There are three ways around that. They are not equally good.

---

## Option A — Windows runs the adb server, WSL runs the client *(recommended)*

The device stays with Windows, where the USB driver already lives. WSL talks to the Windows adb
server over TCP.

Why this one: the **client** reads the APK from disk and pushes it through the socket. That means
`/home/martin/vorwahlguard/app/build/.../app-release.apk` stays a valid path. Every other
approach forces you to translate paths with `wslpath -w`.

### One-time setup

**1. Windows side.** Install Android platform-tools (or reuse Android Studio's copy under
`%LOCALAPPDATA%\Android\Sdk\platform-tools`). Note the version:

```powershell
adb version
```

**2. WSL side.** Do *not* `apt install adb` — the Ubuntu package lags several versions behind and
a protocol mismatch makes the client kill the remote server. Download the same platform-tools
release Google ships for Linux, or symlink to the Windows binary's version. Verify:

```bash
adb version   # must match the Windows output
```

**3. Networking.** Check `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
networkingMode=mirrored
```

With `mirrored`, WSL and Windows share `localhost` and this all becomes trivial. Without it
(NAT, the default on older setups), WSL reaches Windows through the default gateway.

> `networkingMode=bridged` breaks usbipd entirely. If you ever go the Option C route, do not use
> bridged.

### Every session

```powershell
# Windows, once per boot. -a binds the server to all interfaces.
adb kill-server
adb -a -P 5037 nodaemon server
```

`-a` exposes the adb server to your local network. Add a Windows Firewall rule that allows TCP
5037 **only from the WSL subnet**, not from "any". An open adb server on a LAN is a remote shell
on your phone.

With `networkingMode=mirrored` you can skip `-a` entirely and bind to loopback.

```bash
# WSL
source scripts/adb-env.sh
adb devices
```

`scripts/adb-env.sh` detects mirrored vs. NAT and exports `ADB_SERVER_SOCKET` accordingly.

Once `adb devices` lists your phone, Claude Code can deploy with `/deploy`.

---

## Option B — Wireless debugging

No cable, no daemon juggling. Android 11+ has native wireless debugging with a pairing code.

```powershell
# Windows, phone on USB, once
adb tcpip 5555
```

```bash
# WSL, phone and PC on the same network
adb connect 192.168.1.42:5555
adb devices
```

Unplug the cable. This survives reboots of WSL and needs no Windows-side server.

The catch: WSL's NAT breaks mDNS discovery, so `adb pair` with the QR-code flow from Android's
*Wireless debugging* screen does not find the device from inside WSL. Do the pairing step on the
Windows side, then connect from WSL.

Also: an open port 5555 on your phone is an unauthenticated shell for anyone on that network.
Turn wireless debugging off when you are done. Do not do this on café wifi.

---

## Option C — usbipd-win, real USB passthrough

Only if you actually need the device *inside* Linux — `fastboot`, `lsusb`, a udev rule. For
`adb install` it buys you nothing and costs you the list below.

```powershell
winget install --interactive --exact dorssel.usbipd-win
usbipd list
usbipd bind --busid 1-4          # admin PowerShell, once per device
usbipd attach --wsl=Ubuntu --busid 1-4
```

```bash
sudo apt install linux-tools-virtual hwdata
sudo update-alternatives --install /usr/local/bin/usbip usbip /usr/lib/linux-tools/*/usbip 20
lsusb
```

Then a udev rule, or adb sees the device as `no permissions`:

```bash
# /etc/udev/rules.d/51-android.rules — idVendor from your lsusb output
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"
```

```bash
sudo udevadm control --reload-rules && sudo udevadm trigger
adb kill-server && adb devices
```

What you have signed up for:

- WSL kernel must be ≥ 5.10.60.1
- While attached, **Windows loses the device**. No Android Studio, no MTP file transfer.
- Every `wsl --shutdown` detaches it. Re-attach manually.
- Android's adb daemon renegotiates the USB interface on reconnect, and the device tends to
  vanish from `lsusb`. This is a known, unresolved friction point, not a misconfiguration on
  your side.

---

## What Claude Code can and cannot do here

**Cannot:** `usbipd bind` needs an elevated PowerShell on the Windows host. Claude Code runs in
WSL. The one-time setup is yours.

**Can:** everything after `adb devices` returns your phone. Build, uninstall the old signature,
install, read logcat. See `/deploy`.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| `adb server version doesn't match this client` | WSL adb ≠ Windows adb. Same platform-tools version on both sides. |
| `adb devices` empty, no error | `ADB_SERVER_SOCKET` unset, or the Windows firewall dropped 5037. |
| `no permissions (user in plugdev group…)` | Option C without a udev rule. |
| Device disappears after `wsl --shutdown` | Option C. Re-attach. Or switch to Option A. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Debug build installed, release build has a different signature. `adb uninstall io.janda.vorwahlguard` first. |
| App installs, but no longer screens calls | Uninstalling revoked `ROLE_CALL_SCREENING`. Grant it again in onboarding. |
