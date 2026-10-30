package com.rfcommandcenter.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public class OperationsActivity extends Activity {
    private TextView output;
    private EditText engagementFilter;
    private JSONArray findings = new JSONArray();
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(0xff020b08);
        scroll.addView(root);setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("RF COMMAND CENTER / V7\nOPERATIONS & REPORTING");
        title.setTextColor(0xff20ff80);title.setTextSize(22);
        root.addView(title);
        TextView disclaimer = new TextView(this);
        disclaimer.setText("Local evidence review and explicit export. No unattended remote commands, exploitation, or automatic CVE confirmation.");
        disclaimer.setTextColor(Color.LTGRAY);root.addView(disclaimer);
        engagementFilter = new EditText(this);
        engagementFilter.setSingleLine(true);
        engagementFilter.setHint("Engagement name filter (optional)");
        engagementFilter.setTextColor(Color.WHITE);
        engagementFilter.setHintTextColor(0xff99bba7);
        root.addView(engagementFilter);
        button(root,"REFRESH FINDINGS",this::refresh);
        button(root,"EXPORT EXECUTIVE SUMMARY",()->share(false));
        button(root,"EXPORT TECHNICAL JSON",()->share(true));
        output = new TextView(this);output.setTextColor(Color.WHITE);output.setTextSize(14);
        root.addView(output);
        refresh();
    }
    private void button(LinearLayout root,String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());root.addView(b);
    }
    private JSONArray filtered() {
        JSONArray out=new JSONArray();
        String filter=engagementFilter.getText().toString().trim().toLowerCase(Locale.US);
        for(int i=0;i<findings.length();i++) {
            JSONObject f=findings.optJSONObject(i);
            if(f==null)continue;
            if(filter.isEmpty()||f.optString("engagement").toLowerCase(Locale.US).contains(filter))
                out.put(f);
        }
        return out;
    }
    private void refresh() {
        // Activity-specific preferences match EngagementActivity's getPreferences(MODE_PRIVATE).
        try {
            android.content.SharedPreferences prefs=getSharedPreferences("EngagementActivity",MODE_PRIVATE);
            findings=new JSONArray(prefs.getString("engagement_findings","[]"));
        }catch(Exception e){findings=new JSONArray();}
        if(output==null)return;
        JSONArray items=filtered();
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(String s:new String[]{"CRITICAL","HIGH","MEDIUM","LOW","INFO"})counts.put(s,0);
        int pending=0,validated=0;
        for(int i=0;i<items.length();i++){
            JSONObject f=items.optJSONObject(i);if(f==null)continue;
            String severity=f.optString("severity","INFO");
            counts.put(severity,counts.containsKey(severity)?counts.get(severity)+1:1);
            if("RETEST PENDING".equals(f.optString("state")))pending++;
            if("VALIDATED".equals(f.optString("state")))validated++;
        }
        StringBuilder b=new StringBuilder("ENGAGEMENT FINDINGS: "+items.length()+"\n");
        for(Map.Entry<String,Integer> e:counts.entrySet())
            b.append(e.getKey()).append(": ").append(e.getValue()).append("   ");
        b.append("\nValidated (operator-marked): ").append(validated);
        b.append("\nRetest pending: ").append(pending);
        b.append("\n\nFindings are operator-entered; severity and validation are not independently verified.\n");
        for(int i=Math.max(0,items.length()-25);i<items.length();i++){
            JSONObject f=items.optJSONObject(i);if(f==null)continue;
            b.append("\n[").append(f.optString("severity")).append(" / ")
             .append(f.optString("state")).append("] ").append(f.optString("title"))
             .append("\nTarget: ").append(f.optString("target"))
             .append("\nRemediation: ").append(f.optString("remediation")).append("\n");
        }
        output.setText(b.toString());
    }
    private void share(boolean technical) {
        refresh();
        JSONArray items=filtered();
        try {
            String body;
            if(technical) {
                JSONObject doc=new JSONObject();
                doc.put("schema","rfcc-v7-technical-report");
                doc.put("generated_at",System.currentTimeMillis());
                doc.put("source","Operator-entered V6 engagement findings");
                doc.put("findings",items);
                doc.put("warning","No independent CVE or severity validation");
                body=doc.toString(2);
            }else {
                int retest=0,validated=0;
                StringBuilder b=new StringBuilder("RF COMMAND CENTER / V7\nEXECUTIVE ENGAGEMENT SUMMARY\n\n");
                b.append("Findings: ").append(items.length()).append("\n");
                for(int i=0;i<items.length();i++){
                    JSONObject f=items.optJSONObject(i);if(f==null)continue;
                    if("RETEST PENDING".equals(f.optString("state")))retest++;
                    if("VALIDATED".equals(f.optString("state")))validated++;
                }
                b.append("Validated (operator-marked): ").append(validated)
                 .append("\nRetest pending: ").append(retest)
                 .append("\n\nDetailed observations:\n");
                for(int i=0;i<items.length();i++){
                    JSONObject f=items.optJSONObject(i);if(f==null)continue;
                    b.append("\n- ").append(f.optString("title"))
                     .append(" (").append(f.optString("severity")).append(")")
                     .append("\n  Target: ").append(f.optString("target"))
                     .append("\n  Remediation: ").append(f.optString("remediation")).append("\n");
                }
                b.append("\nNote: This is an operator-authored summary, not an independently validated assessment.");
                body=b.toString();
            }
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType(technical?"application/json":"text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,body);
            startActivity(Intent.createChooser(intent,"Export assessment report"));
        }catch(Exception e){output.setText("Report export failed: "+e.getMessage());}
    }
}