package com.screenpick.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆故事：把没斩过的词交给 AI 编成一篇短故事。
 * 故事里的单词都能点开查释义——这是"用旧词记新词"和"用新词串旧词"的交叉点。
 */
public class StoryActivity extends Activity {

    private static final int MAX_WORDS = 8;

    private LinearLayout content;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
        generate();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF7F8FA);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 18), pad, Ui.dp(this, 30));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "\u2190", 22f, Ui.TEXT, false);
        back.setPadding(0, 0, Ui.dp(this, 14), Ui.dp(this, 2));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        head.addView(Ui.text(this, "记忆故事", 26f, Ui.TEXT, true));
        root.addView(head);

        content = Ui.column(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return scroll;
    }

    private void generate() {
        if (loading) return;
        loading = true;
        content.removeAllViews();

        List<BookDb.Item> items = BookDb.get(this).unmastered();
        if (items.isEmpty()) {
            content.addView(hintText("单词本里还没有没掌握的词。"));
            loading = false;
            return;
        }
        final List<String> words = new ArrayList<>();
        for (int i = 0; i < items.size() && i < MAX_WORDS; i++) words.add(items.get(i).word);

        // 加载态
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Ui.dp(this, 60), 0, 0);
        ProgressBar pb = new ProgressBar(this);
        box.addView(pb);
        TextView tip = Ui.text(this, "正在用这 " + words.size() + " 个词编故事…", 13f, Ui.TEXT_SUB, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, Ui.dp(this, 14), 0, 0);
        box.addView(tip);

        StringBuilder list = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) list.append("、");
            list.append(words.get(i));
        }
        TextView wl = Ui.text(this, list.toString(), 11.5f, 0xFF9CA3AF, false);
        wl.setGravity(Gravity.CENTER);
        wl.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0);
        box.addView(wl);
        content.addView(box);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Translator.Story st = Translator.makeStory(StoryActivity.this, words);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            render(st);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            renderError(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void render(final Translator.Story st) {
        content.removeAllViews();

        if (!st.title.isEmpty()) {
            TextView t = Ui.text(this, st.title, 19f, Ui.TEXT, true);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            content.addView(t);
        }

        LinearLayout enCard = Ui.column(this);
        enCard.setBackground(Ui.bg(Ui.SURFACE, 16f, this));
        int p = Ui.dp(this, 16);
        enCard.setPadding(p, p, p, p);
        content.addView(enCard);

        TextView enLabel = Ui.text(this, "英文（点单词查释义）", 11.5f, Ui.TEXT_SUB, true);
        enCard.addView(enLabel);

        TextView en = Ui.text(this, WordPop.linkify(this, st.en), 16f, 0xFF0F172A, false);
        WordPop.styleLink(en);
        en.setPadding(0, Ui.dp(this, 8), 0, 0);
        enCard.addView(en);

        if (!st.zh.isEmpty()) {
            LinearLayout zhCard = Ui.column(this);
            zhCard.setBackground(Ui.bg(Ui.SURFACE, 16f, this));
            zhCard.setPadding(p, p, p, p);
            LinearLayout.LayoutParams zhLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            zhLp.topMargin = Ui.dp(this, 12);
            content.addView(zhCard, zhLp);

            zhCard.addView(Ui.text(this, "中文", 11.5f, Ui.TEXT_SUB, true));
            TextView zh = Ui.text(this, st.zh, 14f, 0xFF374151, false);
            zh.setPadding(0, Ui.dp(this, 8), 0, 0);
            zh.setTextIsSelectable(true);
            zhCard.addView(zh);
        }

        LinearLayout acts = Ui.row(this);
        acts.setPadding(0, Ui.dp(this, 18), 0, 0);
        acts.addView(btn("换一个故事", 0xFFF0F5FF, Ui.BRAND, new View.OnClickListener() {
            @Override public void onClick(View v) { generate(); }
        }), half(true));
        acts.addView(btn("复制全文", 0xFFF9FAFB, 0xFF374151, new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(Activity.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText(
                            "story", st.en + "\n\n" + st.zh));
                    Toast.makeText(StoryActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                }
            }
        }), half(false));
        content.addView(acts);

        TextView note = Ui.text(this,
                "故事里【】括起来的就是你收藏的词。读一遍，比背十遍有用。",
                11.5f, 0xFF9CA3AF, false);
        note.setPadding(0, Ui.dp(this, 14), 0, 0);
        content.addView(note);
    }

    private void renderError(String msg) {
        content.removeAllViews();
        TextView t = Ui.text(this, "故事生成失败：" + msg, 14f, 0xFFB42318, false);
        t.setPadding(0, Ui.dp(this, 30), 0, 0);
        content.addView(t);

        TextView hint = Ui.text(this,
                Prefs.apiKey(this).isEmpty()
                        ? "先在设置里填一个 API Key，故事需要 AI 生成。"
                        : "可能是网络或额度问题，稍后再试。",
                12.5f, Ui.TEXT_SUB, false);
        hint.setPadding(0, Ui.dp(this, 10), 0, 0);
        content.addView(hint);

        LinearLayout acts = Ui.row(this);
        acts.setPadding(0, Ui.dp(this, 16), 0, 0);
        acts.addView(btn("重试", 0xFFF0F5FF, Ui.BRAND, new View.OnClickListener() {
            @Override public void onClick(View v) { generate(); }
        }), half(true));
        content.addView(acts);
    }

    private TextView hintText(String s) {
        TextView t = Ui.text(this, s, 14f, Ui.TEXT_SUB, false);
        t.setPadding(0, Ui.dp(this, 30), 0, 0);
        return t;
    }

    private TextView btn(String label, int bg, int fg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 13));
        t.setBackground(Ui.bg(bg, 13f, this));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams half(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        if (left) p.rightMargin = Ui.dp(this, 5);
        else p.leftMargin = Ui.dp(this, 5);
        return p;
    }
}
