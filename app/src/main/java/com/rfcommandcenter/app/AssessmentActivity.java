package com.rfcommandcenter.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class AssessmentActivity extends Activity {
    private final Map<String, JSONObject> devices = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView output;
    private BluetoothGatt gatt;
    private BluetoothLeScanner scanner;
    private boolean scanning = false;
    private boolean connected = false;
    private String selected;
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            try {
                String address = result.getDevice().getAddress();
                JSONObject item = new JSONObject();
                item.put("address", address);
                item.put("rssi", result.getRssi());
                item.put("name", result.getScanRecord() == null ? "" : result.getScanRecord().getDeviceName());
                item.put("timestamp", System.currentTimeMillis());
                devices.put(address, item);
                render();
            } catch (Exception ignored) {}
        }
        @Override public void onScanFailed(int code) { scanning=false; output.setText("BLE scan failed: "+code); }
    };
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            runOnUiThread(() -> output.append("\\nAccess-control probe: "+c.getUuid()+" status="+status+" (0=read permitted; others may require authentication/encryption). No value saved."));
        }
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                try { g.discoverServices(); } catch (SecurityException ignored) {}
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                runOnUiThread(() -> output.append("\nDisconnected (status "+status+")"));
                try { g.close(); } catch (Exception ignored) {}
                if (gatt == g) gatt=null;
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            StringBuilder sb = new StringBuilder("GATT services (read-only)\n");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : g.getServices()) {
                    sb.append("\nService ").append(service.getUuid()).append("\n");
                    for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                        sb.append("  ").append(c.getUuid()).append(" properties=0x")
                          .append(Integer.toHexString(c.getProperties())).append("\n");
                    }
                }
            } else sb.append("Discovery failed: ").append(status);
            runOnUiThread(() -> output.setText(sb.toString()));
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20,20,20,20);
        layout.setBackgroundColor(0xff101923);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout); setContentView(scroll);
        TextView heading = new TextView(this);
        heading.setText("RF COMMAND CENTER • V2\nAuthorized BLE GATT Inspector");
        heading.setTextSize(21); heading.setTextColor(0xff37d3b5); layout.addView(heading);
        addButton(layout,"Scan BLE (10 seconds)", () -> startScan());
        addButton(layout,"Stop scan", () -> stopScan());
        addButton(layout,"Export scan history", () -> export());
        addButton(layout,"Test first readable characteristic (owned device only)", () -> testRead());
        output = new TextView(this); output.setTextColor(Color.WHITE);
        output.setTextSize(13); output.setPadding(4,15,4,15);
        layout.addView(output);
        render();
    }
    private void addButton(LinearLayout layout, String label, Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run()); layout.addView(b);
    }
    private boolean permissions() {
        ArrayList<String> missing=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT})
            if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) missing.add(p);
        if(!missing.isEmpty()) {requestPermissions(missing.toArray(new String[0]),77); return false;}
        return true;
    }
    private void startScan() {
        if(!permissions()) return;
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null || !adapter.isEnabled()) {output.setText("Enable Bluetooth"); return;}
        try {
            if(scanning) stopScan();
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null) {output.setText("BLE scanner unavailable");return;}
            scanner.startScan(callback); scanning=true;
            handler.postDelayed(this::stopScan,10000);
        } catch(Exception e) {output.setText("Scan error: "+e.getMessage());}
    }
    private void stopScan() {
        if(!scanning) return;
        try {scanner.stopScan(callback);} catch(Exception ignored){}
        scanning=false;
    }
    private void render() {
        if(output==null) return;
        StringBuilder sb=new StringBuilder("Discovered BLE devices: "+devices.size()+"\n");
        for(JSONObject d:devices.values())
            sb.append("\n").append(d.optString("name","Unnamed")).append("  ")
              .append(d.optString("address")).append("  ").append(d.optInt("rssi")).append(" dBm");
        output.setText(sb.toString());
        // Device selection is intentionally explicit and separate from passive scanning.
        if(!devices.isEmpty()) output.setOnClickListener(v -> chooseDevice());
    }
    private void chooseDevice() {
        String[] addresses=devices.keySet().toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
          .setTitle("Select an owned/authorized BLE device")
          .setItems(addresses,(dialog,which)->{
              selected=addresses[which];
              new android.app.AlertDialog.Builder(this)
                .setTitle("Authorized device confirmation")
                .setMessage("Only connect to devices you own or have explicit permission to assess. GATT enumeration is read-only. Connect to "+selected+"?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Connect",(d,w)->connect(selected)).show();
          }).show();
    }
    private void connect(String address) {
        if(!permissions()) return;
        try {
            stopScan();
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            if(gatt!=null) {gatt.disconnect();gatt.close();}
            BluetoothDevice device=manager.getAdapter().getRemoteDevice(address);
            output.setText("Connecting to "+address+"...");
            gatt=device.connectGatt(this,false,gattCallback);
        } catch(Exception e) {output.setText("GATT error: "+e.getMessage());}
    }
    private void testRead() {
        if (!connected || gatt == null) {output.append("\\nConnect to an authorized BLE device first.");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Read-only access check")
          .setMessage("Confirm this device is yours or explicitly in scope. This will attempt ONE GATT read and record only its status, not the returned value.")
          .setNegativeButton("Cancel",null).setPositiveButton("Test",(dialog,which)->{
              try {
                  for(BluetoothGattService s:gatt.getServices()) for(BluetoothGattCharacteristic ch:s.getCharacteristics()) {
                      if((ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)!=0) {
                          boolean started=gatt.readCharacteristic(ch);
                          output.append("\\nRead requested: "+ch.getUuid()+" started="+started);
                          return;
                      }
                  }
                  output.append("\\nNo readable characteristic advertised.");
              } catch(Exception ex) {output.append("\\nRead failed: "+ex.getClass().getSimpleName());}
          }).show();
    }
    private void export() {
        JSONArray array=new JSONArray();
        for(JSONObject item:devices.values()) array.put(item);
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TEXT,array.toString());
        startActivity(Intent.createChooser(intent,"Export BLE observations"));
    }
    @Override protected void onDestroy() {
        stopScan();
        if(gatt!=null) {try {gatt.disconnect();gatt.close();}catch(Exception ignored){}}
        super.onDestroy();
    }
}