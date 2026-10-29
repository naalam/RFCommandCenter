# RF Command Center 5.0 — Integrated V4.2 + V5

This branch builds on V4.1 and preserves the Wi-Fi scanner, BLE scanner, authorized BLE GATT inspector, and scoped TCP inventory.

## New capabilities
- Matrix-themed RF dashboard with searchable and filterable inventory.
- Tap an observed identifier for a detailed metadata view and a route to advisory research.
- In-memory inventory capped at 400 identifiers to limit pressure on 2 GB devices.
- Explicit save/load of the **last** scan snapshot in private app preferences; saved results are historical, not live.
- Verified manufacturer, model, firmware and evidence-reference records, stored locally (maximum 150 records).
- Open the official NVD keyword search in a browser to research possible CVEs.
- Export device-identity records as JSON. Existing TCP and scan JSON exports remain available.
- Existing BLE GATT single-characteristic read-status test is still gated by an authorization confirmation and does not save characteristic values.

## Interpretation
The app does **not** automatically identify BLE firmware, correlate exact affected versions, validate CVEs, or determine whether nearby devices are exploitable. A device name, MAC address, service UUID or open port is not proof of a vulnerability. NVD keyword results must be independently checked against official advisories, exact firmware, and vendor mitigations.

## Limitations and next steps
- Snapshot storage retains only the latest manually saved snapshot; it is not a time-series database.
- No packet injection, deauthentication, unauthorized pairing or exploitation.
- Full V5.5–V7 functionality (USB capture drivers, remote analysis orchestration, full engagement workflows, enterprise report generation) is not implemented.
- GitHub Actions uses ephemeral debug signing; configure a persistent keystore before expecting seamless upgrades.
- Validate build and runtime permissions on the target Android 13 tablet before merging.
