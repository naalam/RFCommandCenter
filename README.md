# RF Command Center v1.0

Native Android 13-focused Wi-Fi and BLE passive discovery tool, optimized for a 32-bit ARM tablet with 2 GB RAM. No native libraries, no Internet permission, no telemetry, no root execution. No injection, deauthentication, pairing attempts, or credential capture.

## Build using GitHub Actions
1. Create an empty GitHub repository (for example `RFCommandCenter`).
2. From the unzipped project directory:
   ```bash
   git init
   git add .
   git commit -m "Initial RF Command Center"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/RFCommandCenter.git
   git push -u origin main
   ```
3. Open GitHub Actions > **Build RF Command Center APK** > **Run workflow**.
4. After a successful run, download **RFCommandCenter-debug-APK** artifact. Unzip, then install with `adb install -r app-debug.apk`.

Alternatively, with GitHub CLI run `gh workflow run android.yml --ref main`, then `gh run list --workflow android.yml`, and `gh run download RUN_ID --name RFCommandCenter-debug-APK --dir ./output`.

## First use
- Enable Wi-Fi, Bluetooth, and Android Location services.
- Approve Nearby Devices and Precise Location permission requests.
- Tap Wi-Fi scan. Note Android may throttle active scans and show cached observations.
- Tap BLE scan; scanning automatically stops after 12 seconds.
- Export JSON through Android's share sheet.

## Scope and limitations
- Passive discovery may list nearby radios, including those not owned by you. Restrict active assessment to authorized targets.
- BLE scan records do not prove a device's vulnerability. Addresses may rotate.
- Wi-Fi security is displayed as beacon-advertised capabilities, not validated configuration.
- No Wi-Fi monitor mode on the verified built-in adapter. USB adapter driver compatibility has not been tested.
- Debug APK is automatically signed with a development/debug key; not suitable for production distribution.
- No persistence beyond current running app session in v1. Export before closing the app.
