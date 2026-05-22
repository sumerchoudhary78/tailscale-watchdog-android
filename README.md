# Tailscale Watchdog

Foreground-service watchdog that monitors Android's VPN state and reconnects Tailscale when it drops. Built for OEMs that aggressively kill background services (Realme/ColorOS, Xiaomi/MIUI, Oppo, Vivo).

## Why

Android's built-in "Always-on VPN" gets overruled by aggressive battery management on some OEMs. Tailscale stops, the VPN tunnel dies, and the device becomes unreachable. This app:

- Runs a persistent foreground service with an ongoing notification (harder to kill).
- Listens for VPN drops via `ConnectivityManager.NetworkCallback`.
- Triggers Tailscale to reconnect via broadcast intent + activity launch.
- Auto-starts on boot and on package replace.
- (Optional, via follow-up PR) Uses an `AccessibilityService` to tap the Connect button as a last-resort UI fallback.

## Requirements

- Android 8.0+ (API 26)
- Tailscale app installed (`com.tailscale.ipn`)

## Setup checklist on Realme / ColorOS

The app alone is not enough on aggressive OEMs. Do all of this:

1. Settings → Apps → Tailscale Watchdog → Battery → **Unrestricted**.
2. Phone Manager → Privacy permissions → Startup Manager → enable **Tailscale Watchdog** (and Tailscale).
3. Recents screen → swipe down on the Tailscale Watchdog card → tap the **lock** icon. Same for Tailscale.
4. Settings → Network & internet → VPN → Tailscale → gear → **Always-on VPN ON** + **Block connections without VPN ON**.
5. Disable any "Smart memory cleanup" or aggressive cleaner toggles.

## Build

```sh
# First-time only: generate the gradle wrapper
gradle wrapper --gradle-version 8.7

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio (Iguana or newer).

## Permissions used

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Long-running watchdog service |
| `RECEIVE_BOOT_COMPLETED` | Restart watchdog after reboot |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |
| `ACCESS_NETWORK_STATE` | Read VPN network status |
| `QUERY_ALL_PACKAGES` | Detect Tailscale install + send launch intent |

## Architecture

```
MainActivity        - one-time setup UI, start/stop watchdog
WatchdogService     - foreground service; registers NetworkCallback for VPN transport
BootReceiver        - ACTION_BOOT_COMPLETED / MY_PACKAGE_REPLACED -> start service
TailscaleReconnector- broadcast intent + launch-intent strategies
```

Follow-up PRs:
- `feat/accessibility-fallback` - last-resort UI tap if intent reconnect fails.

## Honest ceiling

Nothing is 100% reliable on Realme/ColorOS. Combine: (1) settings hardening, (2) lock both Tailscale and Watchdog in Recents, (3) this app. If the OS reaps everything under memory pressure, even the watchdog can be killed. Keeping the phone plugged in helps because some OEMs are less aggressive while charging.
