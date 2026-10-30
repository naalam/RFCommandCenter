package com.rfcommandcenter.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.widget.*;
import org.json.*;
import java.util.*;

public class EngagementActivity extends Activity {
    private EditText engagement, owner, targets, authorization, finding, evidence, remediation;
    private Spinner severity, state;
    private TextView output;
    private final ArrayList<JSONObject> findings = new ArrayList<>();
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        root.setBackgroundColor(0xff020b08);
        scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);
        title.setText("RF COMMAND CENTER / V6\nENGAGEMENT OPERATIONS");
        title.setTextColor(0xff20ff80);title.setTextSize(22);root.addView(title);
        engagement=field(root,"Engagement name (required)");
        owner=field(root,"Assessment owner (required)");
        targets=field(root,"Approved target identifier or IP (required)");
        authorization=field(root,"Authorization / rules of engagement reference (required)");
        finding=field(root,"Finding title / observation (required)");
        evidence=field(root,"Evidence summary / reproducible observation");
        remediation=field(root,"Remediation / recommended verification");
        severity=spinner(root,new String[]{"INFO","LOW","MEDIUM","HIGH","CRITICAL"});
        state=spinner(root,new String[]{"OBSERVED","POTENTIALLY AFFECTED","VALIDATED","RETEST PENDING","REMEDIATED"});
        button(root,"SAVE ENGAGEMENT FINDING",()->save());
        button(root,"VIEW SAVED FINDINGS",()->show());
        button(root,"EXPORT ENGAGEMENT REPORT",()->export());
        output=new TextView(this);output.setTextColor(Color.WHITE);output.setTextSize(13);
        root.addView(output);
        load();show();
    }
    private EditText field(LinearLayout root,String hint) {
        EditText e=new EditText(this);e.setSingleLine(false);e.setMaxLines(3);
        e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff8eae9b);
        root.addView(e);return e;
    }
    private Spinner spinner(LinearLayout root,String[] values) {
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
        s.setAdapter(adapter);root.addView(s);return s;
    }
    private void button(LinearLayout root,String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());root.addView(b);
    }
    private String value(EditText e){return e.getText().toString().trim();}
    private void save() {
        if(value(engagement).isEmpty()||value(owner).isEmpty()||value(targets).isEmpty()||
           value(authorization).isEmpty()||value(finding).isEmpty()){
            output.setText("Complete engagement, owner, authorized target, authorization reference, and finding.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Confirm assessment scope")
          .setMessage("Record this finding only for a target explicitly covered by your authorization. This screen records evidence; it does not initiate scans.")
          .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->persistFinding()).show();
    }
    private void persistFinding() {
        try {
            JSONObject r=new JSONObject();
            r.put("id",UUID.randomUUID().toString());
            r.put("engagement",value(engagement));r.put("owner",value(owner));
            r.put("target",value(targets));r.put("authorization",value(authorization));
            r.put("title",value(finding));r.put("evidence",value(evidence));
            r.put("remediation",value(remediation));
            r.put("severity",severity.getSelectedItem().toString());
            r.put("state",state.getSelectedItem().toString());
            r.put("timestamp",System.currentTimeMillis());
            findings.add(r);
            if(findings.size()>200)findings.remove(0);
            store();show();
        }catch(Exception ex){output.setText("Could not save finding.");}
    }
    private void load() {
        try {
            JSONArray a=new JSONArray(getPreferences(MODE_PRIVATE).getString("engagement_findings","[]"));
            for(int i=0;i<a.length();i++)findings.add(a.getJSONObject(i));
        }catch(Exception ignored){}
    }
    private void store() {
        JSONArray a=new JSONArray();for(JSONObject f:findings)a.put(f);
        getPreferences(MODE_PRIVATE).edit().putString("engagement_findings",a.toString()).apply();
    }
    private void show() {
        if(output==null)return;
        StringBuilder b=new StringBuilder("SAVED ENGAGEMENT FINDINGS: "+findings.size()+"\n");
        for(int i=Math.max(0,findings.size()-30);i<findings.size();i++) {
            JSONObject f=findings.get(i);
            b.append("\n").append(f.optString("severity")).append(" / ").append(f.optString("state"))
              .append("\n").append(f.optString("title"))
              .append("\nTarget: ").append(f.optString("target"))
              .append("\nEvidence: ").append(f.optString("evidence")).append("\n");
        }
        output.setText(b.toString());
    }
    private void export() {
        try {
            JSONArray a=new JSONArray();for(JSONObject f:findings)a.put(f);
            JSONObject report=new JSONObject();
            report.put("schema","rfcc-v6-engagement-findings");
            report.put("generated_at",System.currentTimeMillis());
            report.put("limitations","Statuses and severity are operator-entered, not automatically validated.");
            report.put("findings",a);
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TEXT,report.toString(2));
            startActivity(Intent.createChooser(intent,"Export engagement report"));
        }catch(Exception ex){output.setText("Export failed.");}
    }
}