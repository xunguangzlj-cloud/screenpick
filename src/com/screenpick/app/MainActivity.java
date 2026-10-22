package com.screenpick.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 主页：只做三件事——显示权限状态、开悬浮球、进设置。 */
public class MainActivity extends Activity {

    private TextView overlayState;
    private TextView a11yState;
    private TextView bookLine;
    private TextView a11yBtn;
    private boolean autoJumped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        goEdgeToEdge();
        setContentView(build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // 无障碍服务没法由 App 自己打开，第一次装完带用户直达开关页就够。
        // 不能每次都跳：服务被系统解绑时（划掉后台 / 覆盖安装 / force-stop 都会）
        // 用户一打开 App 就被弹进系统设置，会以为软件坏了。
        if (!autoJumped && !Prefs.guided(this) && !isA11yEnabled(this)) {
            autoJumped = true;
            Prefs.setGuided(this, true);
            openA11ySettings();
        }
    }

    private void goEdgeToEdge() {
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF7F8FA);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 22), pad, Ui.dp(this, 30));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题
        TextView title = Ui.text(this, "拾词", 30f, Ui.TEXT, true);
        root.addView(title);
        TextView sub = Ui.text(this, "点悬浮球翻译整屏英文，点单词查四六级释义",
                13.5f, Ui.TEXT_SUB, false);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 20));
        root.addView(sub);

        // 状态卡
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.bg(Ui.SURFACE, 16f, this));
        int cp = Ui.dp(this, 16);
        card.setPadding(cp, cp, cp, cp);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(sectionLabel("运行状态"));

        LinearLayout row1 = Ui.row(this);
        overlayState = Ui.text(this, "", 14f, Ui.TEXT, false);
        row1.addView(overlayState, weighted());
        row1.addView(miniButton("去授权", new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        }));
        card.addView(row1, topMargin(12));

        LinearLayout row2 = Ui.row(this);
        a11yState = Ui.text(this, "", 14f, Ui.TEXT, false);
        row2.addView(a11yState, weighted());
        a11yBtn = miniButton("去开启", new View.OnClickListener() {
            @Override public void onClick(View v) { openA11ySettings(); }
        });
        row2.addView(a11yBtn);
        card.addView(row2, topMargin(10));

        // 没配 Key 时把这件事说清楚：免费通道在国内基本连不上
        if (Prefs.apiKey(this).isEmpty()) {
            LinearLayout tip = Ui.column(this);
            tip.setBackground(Ui.bgStroke(0xFFFFF7ED, 12f, 0xFFFDBA74, 1f, this));
            tip.setPadding(cp, Ui.dp(this, 12), cp, Ui.dp(this, 12));
            tip.addView(Ui.text(this, "还没配 AI 引擎", 13f, 0xFF9A3412, true));
            TextView d = Ui.text(this,
                    "内置的免费通道（Google / MyMemory）在国内经常连不上，翻译质量也一般。"
                            + "去设置里填一个 DeepSeek 或任意 OpenAI 兼容接口的 Key，点这里直接过去。",
                    11.5f, 0xFF9A3412, false);
            d.setPadding(0, Ui.dp(this, 3), 0, 0);
            tip.addView(d);
            tip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                }
            });
            root.addView(tip, topMargin(12));
        }

        // 主操作
        TextView start = primaryButton("开启悬浮球", new View.OnClickListener() {
            @Override public void onClick(View v) { startBubble(); }
        });
        root.addView(start, topMargin(18));

        // 单词本
        LinearLayout bookCard = Ui.row(this);
        bookCard.setBackground(Ui.bg(Ui.SURFACE, 14f, this));
        bookCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        bookCard.setClickable(true);
        LinearLayout bookText = Ui.column(this);
        bookText.addView(Ui.text(this, "我的单词本", 14.5f, Ui.TEXT, true));
        bookLine = Ui.text(this, "", 11.5f, Ui.TEXT_SUB, false);
        bookLine.setPadding(0, Ui.dp(this, 3), 0, 0);
        bookText.addView(bookLine);
        LinearLayout.LayoutParams btlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        btlp.weight = 1;
        bookCard.addView(bookText, btlp);
        bookCard.addView(Ui.text(this, "›", 22f, 0xFFC4C9D1, false));
        bookCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WordbookActivity.class));
            }
        });
        root.addView(bookCard, topMargin(10));

        LinearLayout row3 = Ui.row(this);
        row3.addView(ghostButton("设置", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }), weightHalf());
        row3.addView(ghostButton("停止悬浮球", new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
                toast("已停止");
                refresh();
            }
        }), weightHalf(true));
        root.addView(row3, topMargin(10));

        // 用法
        LinearLayout help = Ui.column(this);
        help.setBackground(Ui.bg(Ui.SURFACE, 16f, this));
        help.setPadding(cp, cp, cp, cp);
        root.addView(help, topMargin(18));
        help.addView(sectionLabel("怎么用"));
        help.addView(bullet("1", "把悬浮球拖到顺手的位置，点一下 → 整屏英文翻译成中文"));
        help.addView(bullet("2", "在译文卡片里点任意英文单词 → 自动复制 + 弹出释义"));
        help.addView(bullet("3", "在别的 App 里长按选中一个英文单词，也会直接弹释义"));
        help.addView(bullet("4", "长按悬浮球 = 回到这个页面；拖动 = 换位置"));

        LinearLayout note = Ui.column(this);
        note.setBackground(Ui.bgStroke(0xFFFFFBEB, 12f, 0xFFFDE68A, 1f, this));
        note.setPadding(cp, Ui.dp(this, 12), cp, Ui.dp(this, 12));
        root.addView(note, topMargin(14));
        TextView nt = Ui.text(this,
                "读屏说明：拾词只读取当前界面上应用自己声明的文字，不截图、不上传屏幕内容。"
                        + "图片、视频、游戏里的文字读不到——那需要 OCR，本版没做。",
                12f, 0xFF92400E, false);
        note.addView(nt);

        // 词库统计绝不能在主线程算：首次启动要把 4MB 的 db 从 assets 复制出来，
        // 在主线程做会直接把 Activity 拖到启动超时（exp_appbootfail）。
        final TextView foot = Ui.text(this, "内置词库 · 读取中…", 11.5f, 0xFF9CA3AF, false);
        foot.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(foot);
        new Thread(new Runnable() {
            @Override public void run() {
                String n;
                try {
                    n = String.valueOf(Dict.get(MainActivity.this).size());
                } catch (Exception e) {
                    n = "—";
                }
                final String text = "内置词库 " + n + " 词（四六级考纲 + 高频）· v1.0";
                runOnUiThread(new Runnable() {
                    @Override public void run() { foot.setText(text); }
                });
            }
        }).start();

        return scroll;
    }

    // ---------- 状态 ----------

    private void refresh() {
        boolean ov = Settings.canDrawOverlays(this);
        overlayState.setText(ov ? "悬浮窗权限  ✓ 已开启" : "悬浮窗权限  ✗ 未开启");
        overlayState.setTextColor(ov ? 0xFF067647 : 0xFFB42318);

        // 开关状态和"服务是否真的在跑"是两件事：
        // 每次覆盖安装 APK 都会杀掉进程，无障碍绑定随之断掉，但系统里的开关仍显示为开。
        // 用户看到的就是设置页那句"此服务出现故障"，而这里必须能识别出来。
        boolean ax = isA11yEnabled(this);
        boolean running = ScreenReaderService.get() != null;
        if (ax && running) {
            a11yState.setText("屏幕取词服务  ✓ 运行中");
            a11yState.setTextColor(0xFF067647);
        } else if (ax && !running) {
            a11yState.setText("屏幕取词服务  ⚠ 开关开着但没运行");
            a11yState.setTextColor(0xFFB54708);
        } else {
            a11yState.setText("屏幕取词服务  ✗ 未开启");
            a11yState.setTextColor(0xFFB42318);
        }
        if (a11yBtn != null) {
            a11yBtn.setText(ax && !running ? "重新绑定" : (ax ? "去查看" : "去开启"));
        }

        if (bookLine != null) {
            try {
                BookDb db = BookDb.get(this);
                int total = db.count();
                bookLine.setText(total == 0
                        ? "还没有收藏的词"
                        : "共 " + total + " 词 · 待复习 " + db.countUnmastered());
            } catch (Exception e) {
                bookLine.setText("");
            }
        }
    }

    public static boolean isA11yEnabled(Context c) {
        String want = c.getPackageName() + "/" + ScreenReaderService.class.getName();
        String flat = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
        sp.setString(flat);
        while (sp.hasNext()) {
            String s = sp.next();
            if (s.equalsIgnoreCase(want)) return true;
            if (s.startsWith(c.getPackageName() + "/")) return true;
        }
        return false;
    }

    private void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    /**
     * 尽量直接跳到「拾词 · 屏幕取词」这个开关本身。
     * 很多人在无障碍的一长串列表里找不到我们的服务，所以能用详情页就绝不丢列表页。
     */
    private void openA11ySettings() {
        try {
            android.content.ComponentName cn =
                    new android.content.ComponentName(this, ScreenReaderService.class);
            Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            i.putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME",
                    cn.flattenToString());
            startActivity(i);
            if (isA11yEnabled(this) && ScreenReaderService.get() == null) {
                toast("把开关关掉，再打开一次，服务才会重新连上");
            }
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("在列表里找到「拾词 · 屏幕取词」并打开");
        } catch (Exception e) {
            toast("请到 系统设置 → 无障碍 手动开启");
        }
    }

    private void startBubble() {
        if (!Settings.canDrawOverlays(this)) {
            toast("先给「显示在其他应用上层」权限");
            openOverlaySettings();
            return;
        }
        if (!isA11yEnabled(this)) {
            toast("还差一步：开启屏幕取词服务");
            openA11ySettings();
        }
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(OverlayService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        toast("悬浮球已开启");
    }

    // ---------- 小部件 ----------

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, s, 11.5f, Ui.TEXT_SUB, true);
        return t;
    }

    private View bullet(String num, String text) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        TextView n = new TextView(this);
        n.setText(num);
        n.setTextSize(10.5f);
        n.setTextColor(Color.WHITE);
        n.setGravity(Gravity.CENTER);
        n.setBackground(Ui.bg(Ui.BRAND, 9f, this));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
        nlp.topMargin = Ui.dp(this, 2);
        nlp.rightMargin = Ui.dp(this, 9);
        r.addView(n, nlp);
        TextView t = Ui.text(this, text, 13f, 0xFF374151, false);
        r.addView(t, weighted());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 9);
        r.setLayoutParams(rlp);
        return r;
    }

    private TextView primaryButton(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15.5f);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        t.setBackground(Ui.bg(Ui.BRAND, 14f, this));
        t.setOnClickListener(l);
        return t;
    }

    private TextView ghostButton(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14f);
        t.setTextColor(Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));
        t.setBackground(Ui.bgStroke(Ui.SURFACE, 12f, Ui.LINE, 1f, this));
        t.setOnClickListener(l);
        return t;
    }

    private TextView miniButton(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12f);
        t.setTextColor(Ui.BRAND);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 6));
        t.setBackground(Ui.bg(0xFFF0F5FF, 8f, this));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        return p;
    }

    private LinearLayout.LayoutParams weightHalf() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        p.rightMargin = Ui.dp(this, 5);
        return p;
    }

    private LinearLayout.LayoutParams weightHalf(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        p.leftMargin = Ui.dp(this, 5);
        return p;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, dp);
        return p;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
