package com.screenpick.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 设置页：引擎、Key、交互开关。保存即生效，无需重启。 */
public class SettingsActivity extends Activity {

    private RadioGroup engineGroup;
    private EditText baseInput, modelInput, keyInput;
    private Switch selectSwitch, copySwitch;
    private SeekBar sizeBar;
    private TextView sizeLabel;
    private TextView testResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF7F8FA);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 30));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.text(this, "设置", 26f, Ui.TEXT, true));

        // ---- 引擎 ----
        LinearLayout engineCard = card();
        root.addView(engineCard, topMargin(16));
        engineCard.addView(Ui.text(this, "翻译引擎", 15f, Ui.TEXT, true));
        engineCard.addView(Ui.text(this,
                "AI 引擎按句翻译、术语保留，质量明显高于机翻，适合精读；免费引擎不需要 Key，但长句和术语会差一些。",
                12f, Ui.TEXT_SUB, false), topMargin(6));

        engineGroup = new RadioGroup(this);
        engineGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton rbLlm = radio("AI 引擎（OpenAI 兼容接口，推荐）", Prefs.ENGINE_LLM);
        RadioButton rbFree = radio("免费引擎（Google / MyMemory，无需 Key）", Prefs.ENGINE_FREE);
        engineGroup.addView(rbLlm);
        engineGroup.addView(rbFree);
        engineCard.addView(engineGroup, topMargin(8));

        // ---- 接口 ----
        LinearLayout apiCard = card();
        root.addView(apiCard, topMargin(12));
        apiCard.addView(Ui.text(this, "接口配置", 15f, Ui.TEXT, true));
        apiCard.addView(Ui.text(this,
                "默认指向 DeepSeek。任何 OpenAI 兼容服务都行：OpenAI、通义、Kimi、硅基流动…改地址和模型即可。",
                12f, Ui.TEXT_SUB, false), topMargin(6));

        baseInput = input("接口地址", Prefs.apiBase(this), false);
        apiCard.addView(baseInput, topMargin(12));
        modelInput = input("模型名", Prefs.model(this), false);
        apiCard.addView(modelInput, topMargin(8));
        keyInput = input("API Key（sk-…）", Prefs.apiKey(this), true);
        apiCard.addView(keyInput, topMargin(8));

        TextView testBtn = ghost("测试连通性", new View.OnClickListener() {
            @Override public void onClick(View v) { runTest(); }
        });
        apiCard.addView(testBtn, topMargin(12));
        testResult = Ui.text(this, "", 12f, Ui.TEXT_SUB, false);
        testResult.setPadding(0, Ui.dp(this, 8), 0, 0);
        apiCard.addView(testResult);

        // ---- 交互 ----
        LinearLayout behave = card();
        root.addView(behave, topMargin(12));
        behave.addView(Ui.text(this, "交互", 15f, Ui.TEXT, true));

        selectSwitch = switchRow(behave, "选中英文即查词",
                "不用点悬浮球，在任意 App 里长按选中一个单词就弹释义", Prefs.selectLookup(this));
        copySwitch = switchRow(behave, "点词自动复制",
                "点单词时顺手把它放进剪贴板，方便粘到别处", Prefs.copyOnTap(this));

        // ---- 字号 ----
        LinearLayout sizeCard = card();
        root.addView(sizeCard, topMargin(12));
        LinearLayout sizeHead = Ui.row(this);
        sizeHead.addView(Ui.text(this, "正文字号", 15f, Ui.TEXT, true), weighted());
        sizeLabel = Ui.text(this, Prefs.fontSize(this) + "sp", 13f, Ui.TEXT_SUB, false);
        sizeHead.addView(sizeLabel);
        sizeCard.addView(sizeHead);

        sizeBar = new SeekBar(this);
        sizeBar.setMax(12);
        sizeBar.setProgress(Prefs.fontSize(this) - 12);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                sizeLabel.setText((p + 12) + "sp");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sizeCard.addView(sizeBar, topMargin(8));

        // ---- 保存 ----
        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextSize(15.5f);
        save.setTextColor(Color.WHITE);
        save.setGravity(Gravity.CENTER);
        save.setTypeface(save.getTypeface(), android.graphics.Typeface.BOLD);
        save.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        save.setBackground(Ui.bg(Ui.BRAND, 14f, this));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        root.addView(save, topMargin(18));

        return scroll;
    }

    // ---------- 保存 ----------

    private void save() {
        Prefs.setEngine(this, engineGroup.getCheckedRadioButtonId() == 1 ? Prefs.ENGINE_LLM : Prefs.ENGINE_FREE);
        Prefs.setApiBase(this, text(baseInput, "https://api.deepseek.com/v1"));
        Prefs.setModel(this, text(modelInput, "deepseek-chat"));
        Prefs.setApiKey(this, text(keyInput, ""));
        Prefs.setSelectLookup(this, selectSwitch.isChecked());
        Prefs.setCopyOnTap(this, copySwitch.isChecked());
        Prefs.setFontSize(this, sizeBar.getProgress() + 12);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private String text(EditText e, String def) {
        String s = e.getText() == null ? "" : e.getText().toString().trim();
        return s.isEmpty() ? def : s;
    }

    // ---------- 连通性测试 ----------

    private void runTest() {
        save();
        testResult.setTextColor(Ui.TEXT_SUB);
        testResult.setText("正在请求…");
        final String key = Prefs.apiKey(this);
        if (!Prefs.ENGINE_LLM.equals(Prefs.engine(this)) || key.isEmpty()) {
            testResult.setText("当前走免费引擎，不需要测试接口");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final String[] out = new String[2];
                try {
                    Translator.Result r = Translator.translate(SettingsActivity.this,
                            "Reading widely is the most reliable way to enlarge your vocabulary.");
                    out[0] = "成功：" + r.text;
                    out[1] = "ok";
                } catch (Exception e) {
                    out[0] = "失败：" + e.getMessage();
                    out[1] = "err";
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        testResult.setText(out[0]);
                        testResult.setTextColor("ok".equals(out[1]) ? 0xFF067647 : 0xFFB42318);
                    }
                });
            }
        }).start();
    }

    // ---------- 小部件 ----------

    private LinearLayout card() {
        LinearLayout c = Ui.column(this);
        c.setBackground(Ui.bg(Ui.SURFACE, 16f, this));
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private RadioButton radio(String label, String value) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextSize(13.5f);
        rb.setId(Prefs.ENGINE_LLM.equals(value) ? 1 : 2);
        rb.setChecked(Prefs.ENGINE_LLM.equals(value)
                ? Prefs.ENGINE_LLM.equals(Prefs.engine(this))
                : Prefs.ENGINE_FREE.equals(Prefs.engine(this)));
        rb.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        return rb;
    }

    private EditText input(String hint, String value, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(13f);
        e.setSingleLine(true);
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        e.setBackground(Ui.bgStroke(0xFFF9FAFB, 10f, Ui.LINE, 1f, this));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Switch switchRow(LinearLayout parent, String title, String desc, boolean checked) {
        LinearLayout wrap = Ui.column(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, title, 13.5f, Ui.TEXT, false), weighted());
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        top.addView(sw);
        wrap.addView(top);
        TextView d = Ui.text(this, desc, 11.5f, Ui.TEXT_SUB, false);
        d.setPadding(0, Ui.dp(this, 2), Ui.dp(this, 30), 0);
        wrap.addView(d);
        parent.addView(wrap, topMargin(14));
        return sw;
    }

    private TextView ghost(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13f);
        t.setTextColor(Ui.BRAND);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setBackground(Ui.bgStroke(0xFFF0F5FF, 10f, 0xFFBFDBFE, 1f, this));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        return p;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, dp);
        return p;
    }
}
