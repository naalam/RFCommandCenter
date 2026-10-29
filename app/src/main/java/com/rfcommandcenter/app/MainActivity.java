package com.rfcommandcenter.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(12, 17, 28), panel = Color.rgb(23, 33, 48), accent = Color.rgb(55, 211, 181);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, JSONObject> observations = new LinkedHashMap<>();
    private LinearLayout root, results;
    private TextView status, stats;
    private WifiManager wifi;
    private BluetoothLeScanner bleScanner;
    private boolean scanningBle = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            try {
                String address = result.getDevice().getAddress();
                String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                JSONObject entry = new JSONObject();
                entry.put("type", "BLE"); entry.put("identifier", address);
                entry.put("name", name == null || name.trim().isEmpty() ? "Unnamed BLE device" : name);
                entry.put("rssi", result.getRssi());
                entry.put("timestamp", dateFormat.format(new Date()));
                if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null)
                    entry.put("service_uuids", result.getScanRecord().getServiceUuids().toString());
                observations.put("BLE:" + address, entry);
                if (!renderPending) {
                    renderPending = true;
                    handler.postDelayed(() -> { renderPending = false; renderObservations(); }, 550);
                }
            } catch (Exception ignored) { }
        }
        @Override public void onScanFailed(int errorCode) {
            scanningBle = false;
            updateStatus("BLE scan failed (code " + errorCode + "). Check permissions and Bluetooth settings.");
        }
    };
    private boolean renderPending;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        buildUi();
        updateStatus("Ready • Scans are passive discovery only");
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setPadding(4, 8, 4, 8);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private Button button(String s, View.OnClickListener action) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(34, 111, 112)));
        b.setOnClickListener(action); return b;
    }
    private void buildUi() {
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(bg);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20, 20, 20, 24);
        sc.addView(root); setContentView(sc);
        root.addView(text("◉ RF COMMAND CENTER", 24, accent, true));
        root.addView(text("Local-only wireless & BLE assessment  •  v3.0", 13, 0xffafc4d3, false));
        status = text("", 13, Color.WHITE, false); root.addView(status);
        stats = text("0 observations", 15, accent, true); root.addView(stats);
        root.addView(button("Open V3 Scoped Vulnerability Assessment", v -> startActivity(new Intent(this, VulnerabilityActivity.class))));
        root.addView(button("Open V2 BLE GATT Inspector", v -> startActivity(new Intent(this, AssessmentActivity.class))));
        root.addView(button("1  Scan nearby Wi-Fi", v -> scanWifi()));
        root.addView(button("2  Start BLE scan (12 seconds)", v -> startBle()));
        root.addView(button("3  Stop BLE scan", v -> stopBle()));
        root.addView(button("Export observations as JSON", v -> exportJson()));
        root.addView(button("Clear observations", v -> { observations.clear(); renderObservations(); updateStatus("Observations cleared"); }));
        root.addView(text("OBSERVED DEVICES", 17, Color.WHITE, true));
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        root.addView(text("Wi-Fi scans depend on Android location services and OS throttling. BLE devices nearby may not disclose names and may rotate identifiers. For authorized assessment and inventory only.", 12, 0xffa9b5c6, false));
        renderObservations();
    }
    private void updateStatus(String message) { if (status != null) status.setText(message); }
    private boolean permitted(String... permissions) {
        ArrayList<String> missing = new ArrayList<>();
        for (String p : permissions) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]), 50); updateStatus("Grant requested permissions, then run scan again"); return false; }
        return true;
    }
    private void scanWifi() {
        if (!permitted(Manifest.permission.ACCESS_FINE_LOCATION)) return;
        if (wifi == null || !wifi.isWifiEnabled()) { updateStatus("Enable Wi-Fi and retry"); return; }
        if (!((android.location.LocationManager)getSystemService(LOCATION_SERVICE)).isLocationEnabled()) {
            updateStatus("Turn on Android Location Services for Wi-Fi scan results");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return;
        }
        try {
            boolean requested = wifi.startScan();
            // Results may be cached; Android throttles scan requests.
            updateStatus(requested ? "Wi-Fi scan requested; displaying latest results in 3 seconds" : "Wi-Fi scan throttled; showing cached results");
            handler.postDelayed(this::readWifi, requested ? 3000 : 300);
        } catch (SecurityException e) { updateStatus("Wi-Fi permission denied"); }
    }
    private void readWifi() {
        try {
            java.util.List<android.net.wifi.ScanResult> scans = wifi.getScanResults();
            for (android.net.wifi.ScanResult scan : scans) {
                JSONObject e = new JSONObject();
                e.put("type", "Wi-Fi"); e.put("identifier", scan.BSSID);
                e.put("name", scan.SSID == null || scan.SSID.isEmpty() ? "Hidden SSID" : scan.SSID);
                e.put("rssi", scan.level); e.put("frequency_mhz", scan.frequency);
                e.put("security_advertisement", scan.capabilities);
                e.put("assessment", classifyWifi(scan.capabilities));
                e.put("timestamp", dateFormat.format(new Date()));
                observations.put("Wi-Fi:" + scan.BSSID, e);
            }
            updateStatus("Latest Wi-Fi results: " + scans.size() + " access points (may be cached)");
            renderObservations();
        } catch (Exception e) { updateStatus("Wi-Fi read failed: " + e.getClass().getSimpleName()); }
    }
    private void startBle() {
        ArrayList<String> required = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            required.add(Manifest.permission.BLUETOOTH_SCAN);
            required.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!permitted(required.toArray(new String[0]))) return;
        BluetoothManager bm = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { updateStatus("Enable Bluetooth and retry"); return; }
        if (scanningBle) { updateStatus("BLE scan already running"); return; }
        try {
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) { updateStatus("BLE scanning unavailable on this build"); return; }
            bleScanner.startScan(bleCallback);
            scanningBle = true; updateStatus("Scanning BLE advertisements for 12 seconds...");
            handler.postDelayed(this::stopBle, 12000);
        } catch (SecurityException e) { updateStatus("Bluetooth permission denied"); }
    }
    private void stopBle() {
        if (!scanningBle) return;
        try { if (bleScanner != null) bleScanner.stopScan(bleCallback); } catch (Exception ignored) { }
        scanningBle = false; updateStatus("BLE scan stopped; " + observations.size() + " total observed identifiers");
        renderObservations();
    }
    private void renderObservations() {
        if (results == null) return;
        results.removeAllViews();
        int w = 0, b = 0;
        for (JSONObject e : observations.values()) if ("Wi-Fi".equals(e.optString("type"))) w++; else b++;
        stats.setText("Wi-Fi: " + w + "   BLE: " + b + "   •   Unique observations: " + observations.size());
        ArrayList<JSONObject> items = new ArrayList<>(observations.values());
        Collections.sort(items, (a, c) -> Integer.compare(c.optInt("rssi", -200), a.optInt("rssi", -200)));
        for (JSONObject e : items) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(14, 10, 14, 10); row.setBackgroundColor(panel);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 7;
            results.addView(row, lp);
            row.addView(text(e.optString("type") + "  •  " + e.optString("name"), 15, Color.WHITE, true));
            row.addView(text(e.optString("identifier") + "   •   " + e.optInt("rssi") + " dBm", 12, accent, false));
            if (e.has("security_advertisement")) row.addView(text(e.optString("security_advertisement") + "  •  " + e.optInt("frequency_mhz") + " MHz", 11, 0xffb6c4d4, false));
            if (e.has("assessment")) row.addView(text(e.optString("assessment"), 11, 0xffffd18c, false));
            if (e.has("service_uuids")) row.addView(text("Advertised services: " + e.optString("service_uuids"), 11, 0xffb6c4d4, false));
        }
        if (items.isEmpty()) results.addView(text("No observations yet. Select Wi-Fi or BLE scan above.", 14, 0xffb6c4d4, false));
    }
    private String classifyWifi(String caps) {
        if (caps == null) return "Unknown security";
        String s = caps.toUpperCase(Locale.US);
        if (s.contains("WEP")) return "HIGH: WEP is obsolete; use WPA2-AES or WPA3";
        if (!s.contains("WPA") && !s.contains("RSN") && !s.contains("SAE") && !s.contains("OWE")) return "REVIEW: Open network; verify intended guest access";
        if (s.contains("WPA-PSK") && !s.contains("WPA2") && !s.contains("RSN")) return "HIGH: Legacy WPA advertised; migrate to WPA2/WPA3";
        if (s.contains("SAE")) return "INFO: WPA3 SAE advertised";
        return "INFO: Security advertised; not proof of secure configuration";
    }
    private void exportJson() {
        try {
            JSONArray array = new JSONArray();
            for (JSONObject o : observations.values()) array.put(o);
            JSONObject doc = new JSONObject(); doc.put("tool", "RF Command Center");
            doc.put("export_time", dateFormat.format(new Date())); doc.put("observations", array);
            Intent share = new Intent(Intent.ACTION_SEND); share.setType("application/json");
            share.putExtra(Intent.EXTRA_TEXT, doc.toString(2));
            startActivity(Intent.createChooser(share, "Export wireless inventory"));
        } catch (Exception e) { Toast.makeText(this, "Export error", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onDestroy() { stopBle(); super.onDestroy(); }
}
