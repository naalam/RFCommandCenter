package com.rfcommandcenter.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import org.json.*;
import java.util.*;

public class IntelligenceActivity extends Activity {
    private EditText manufacturer, model, firmware, reference;
    private TextView output;
    private final ArrayList<JSONObject> records = new ArrayList<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(0xff020b08);
        sc.addView(root);setContentView(sc);
        TextView title = new TextView(this);
        title.setText("RF INTELLIGENCE / V5\nDevice identity and advisory research");
        title.setTextSize(22);title.setTextColor(0xff20ff80);root.addView(title);
        manufacturer = field(root,"Verified manufacturer (not guessed from BLE name)");
        model = field(root,"Verified product / model");
        firmware = field(root,"Firmware or software version (if known)");
        reference = field(root,"Evidence / authorization reference");
        button(root,"SAVE DEVICE IDENTITY",()->saveRecord());
        button(root,"SEARCH OFFICIAL NVD ADVISORIES",()->searchNvd());
        button(root,"VIEW SAVED DEVICE RECORDS",()->showRecords());
        button(root,"EXPORT IDENTITY & EVIDENCE JSON",()->export());
        output = new TextView(this);
        output.setTextColor(Color.WHITE);output.setTextSize(14);
        root.addView(output);
        load();showRecords();
    }
    private EditText field(LinearLayout root,String hint) {
        EditText e = new EditText(this);e.setSingleLine(true);
        e.setTextColor(Color.WHITE);e.setHintTextColor(0xff8dbfa2);e.setHint(hint);
        root.addView(e);return e;
    }
    private void button(LinearLayout root,String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());root.addView(b);
    }
    private void load() {
        try {
            JSONArray a = new JSONArray(getPreferences(MODE_PRIVATE).getString("records","[]"));
            for(int i=0;i<a.length();i++)records.add(a.getJSONObject(i));
        }catch(Exception ignored){}
    }
    private void persist() {
        JSONArray a=new JSONArray();for(JSONObject r:records)a.put(r);
        getPreferences(MODE_PRIVATE).edit().putString("records",a.toString()).apply();
    }
    private void saveRecord() {
        String maker=manufacturer.getText().toString().trim();
        String product=model.getText().toString().trim();
        String scope=reference.getText().toString().trim();
        if(maker.isEmpty()||product.isEmpty()||scope.length()<4) {
            output.setText("Provide verified manufacturer, model and authorization/evidence reference.");return;
        }
        try {
            JSONObject r=new JSONObject();
            r.put("manufacturer",maker);r.put("model",product);
            r.put("firmware",firmware.getText().toString().trim());
            r.put("reference",scope);r.put("timestamp",System.currentTimeMillis());
            r.put("status","IDENTITY RECORDED - CVE MATCH NOT VERIFIED");
            records.add(r);
            while(records.size()>150)records.remove(0);
            persist();showRecords();
        }catch(Exception e){output.setText("Could not save record.");}
    }
    private void searchNvd() {
        String maker=manufacturer.getText().toString().trim();
        String product=model.getText().toString().trim();
        if(maker.isEmpty()||product.isEmpty()){output.setText("Enter verified manufacturer and model first.");return;}
        String q=maker+" "+product+" "+firmware.getText().toString().trim();
        new AlertDialog.Builder(this).setTitle("External advisory research")
          .setMessage("Open the official NVD keyword search for "+q+"? A search hit is NOT evidence the device is vulnerable; confirm exact affected versions and vendor mitigations.")
          .setNegativeButton("Cancel",null)
          .setPositiveButton("Open NVD",(d,w)->{
              String url="https://nvd.nist.gov/vuln/search/results?query="+Uri.encode(q)+"&search_type=all";
              startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
          }).show();
    }
    private void showRecords() {
        if(output==null)return;
        StringBuilder b=new StringBuilder("DEVICE INTELLIGENCE RECORDS: "+records.size()+"\n");
        for(int i=Math.max(0,records.size()-25);i<records.size();i++) {
            JSONObject r=records.get(i);
            b.append("\n").append(r.optString("manufacturer")).append(" / ").append(r.optString("model"))
              .append(" / firmware ").append(r.optString("firmware","unknown"))
              .append("\n").append(r.optString("status")).append("\n");
        }
        output.setText(b.toString());
    }
    private void export() {
        try {
            JSONObject report=new JSONObject();
            report.put("schema","rfcc-v5-identity-evidence");
            report.put("warning","No automatic CVE matches or exploitability claims");
            JSONArray a=new JSONArray();for(JSONObject r:records)a.put(r);
            report.put("records",a);
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_TEXT,report.toString(2));
            startActivity(Intent.createChooser(share,"Export intelligence report"));
        }catch(Exception e){output.setText("Export failed");}
    }
}