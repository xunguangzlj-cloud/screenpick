package com.screenpick.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 复习卡片。
 *
 * 流程严格按"先想、再看、再决定"：
 *   出英文 → 点卡片翻出释义 → 点「斩」或「跳过」才进入下一个。
 * 「斩」= 写库标记掌握，之后不再出现在任何复习队列里；
 * 「跳过」= 原样放回队尾，本轮还会再遇到，直到被斩或本轮结束。
 */
public class ReviewActivity extends Activity {

    private final List<BookDb.Item> queue = new ArrayList<>();
    private int pos = 0;
    private boolean revealed = false;
    private int killed = 0;
    private int skipped = 0;
    private int total = 0;

    private LinearLayout root;
    private TextView progress;
    private TextView wordView;
    private TextView phoneticView;
    private TextView answerView;
    private TextView hintView;
    private LinearLayout actions;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        queue.addAll(BookDb.get(this).unmastered());
        total = queue.size();

        if (queue.isEmpty()) {
            Toast.makeText(this, "没有待复习的词", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initTts();
        root = Ui.column(this);
        root.setBackgroundColor(0xFFF7F8FA);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, Ui.dp(this, 18), pad, Ui.dp(this, 26));
        setContentView(root);

        buildChrome();
        render();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        Pronounce.release();
        super.onDestroy();
    }

    private void buildChrome() {
        LinearLayout header = Ui.row(this);
        TextView back = Ui.text(this, "\u2715", 18f, Ui.TEXT_SUB, false);
        back.setPadding(0, 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back);
        progress = Ui.text(this, "", 12.5f, Ui.TEXT_SUB, false);
        header.addView(progress);
        root.addView(header);
    }

    private void render() {
        root.removeAllViews();
        buildChrome();
        revealed = false;

        if (pos >= queue.size() || queue.isEmpty()) {
            showSummary();
            return;
        }
        progress.setText("剩余 " + queue.size() + " 个 · 已斩 " + killed);

        BookDb.Item it = queue.get(pos);

        LinearLayout stage = Ui.column(this);
        stage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        stageLp.weight = 1;
        root.addView(stage, stageLp);

        LinearLayout card = Ui.column(this);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.bg(Ui.SURFACE, 20f, this));
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 34), Ui.dp(this, 22), Ui.dp(this, 34));
        stage.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        wordView = Ui.text(this, it.word, 34f, Ui.TEXT, true);
        wordView.setGravity(Gravity.CENTER);
        card.addView(wordView);

        phoneticView = Ui.text(this, it.phonetic.isEmpty() ? "" : "/" + it.phonetic + "/",
                13f, Ui.TEXT_SUB, false);
        phoneticView.setGravity(Gravity.CENTER);
        phoneticView.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(phoneticView);

        TextView speak = Ui.text(this, "\u25B6 \u53d1\u97f3", 12.5f, Ui.BRAND, false);
        speak.setGravity(Gravity.CENTER);
        speak.setPadding(0, Ui.dp(this, 10), 0, 0);
        speak.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Pronounce.us(ReviewActivity.this, queue.get(pos).word);
            }
        });
        card.addView(speak);

        answerView = Ui.text(this, "", 15f, 0xFF0F172A, false);
        answerView.setGravity(Gravity.CENTER);
        answerView.setPadding(0, Ui.dp(this, 16), 0, 0);
        answerView.setVisibility(View.GONE);
        card.addView(answerView);

        hintView = Ui.text(this, "想一下它的中文，点卡片看答案", 12.5f, 0xFF9CA3AF, false);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(0, Ui.dp(this, 18), 0, 0);
        card.addView(hintView);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { reveal(); }
        });

        actions = Ui.row(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.dp(this, 22), 0, 0);
        root.addView(actions);
        drawActions();
    }

    /**
     * 两个按钮始终在场，只是没翻面时压暗且不响应——
     * 让按钮在翻面那一刻"原地亮起"，而不是凭空长出来把卡片顶上去。
     */
    private void drawActions() {
        actions.removeAllViews();

        final TextView killBtn = actionButton("斩", 0xFF067647, 0xFFECFDF3, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!revealed) { nudge(); return; }
                kill();
            }
        });
        final TextView skipBtn = actionButton("跳过", 0xFF6B7280, 0xFFF3F4F6, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!revealed) { nudge(); return; }
                skip();
            }
        });
        killBtn.setAlpha(revealed ? 1f : 0.32f);
        skipBtn.setAlpha(revealed ? 1f : 0.32f);

        actions.addView(killBtn, actionLp(true));
        actions.addView(skipBtn, actionLp(false));
    }

    private void nudge() {
        Toast.makeText(this, "先点卡片看一眼答案", Toast.LENGTH_SHORT).show();
    }

    private void reveal() {
        if (revealed) return;
        revealed = true;
        BookDb.Item it = queue.get(pos);
        String raw = it.trans == null ? "" : it.trans.replace("\\n", "\n").trim();
        String trans = raw.isEmpty() ? "（这个词没有本地释义）" : raw;
        answerView.setText(trans);
        answerView.setVisibility(View.VISIBLE);
        hintView.setText("记得住就「斩」，记不住就「跳过」");
        drawActions();
        Pronounce.us(this, it.word);
    }

    private void kill() {
        if (queue.isEmpty()) return;
        BookDb.Item it = queue.remove(pos);
        BookDb.get(this).setMastered(it.word, true);
        BookDb.get(this).bumpReview(it.word);
        killed++;
        if (queue.isEmpty()) {
            pos = 0;
            showSummary();
            return;
        }
        if (pos >= queue.size()) pos = 0;
        render();
    }

    private void skip() {
        if (queue.isEmpty()) return;
        BookDb.get(this).bumpReview(queue.get(pos).word);
        if (queue.size() == 1) {
            Toast.makeText(this, "就剩这一个了，再想想", Toast.LENGTH_SHORT).show();
            render();
            return;
        }
        BookDb.Item it = queue.remove(pos);
        queue.add(it);
        skipped++;
        if (pos >= queue.size()) pos = 0;
        render();
    }

    private void showSummary() {
        root.removeAllViews();
        buildChrome();
        progress.setText("本轮结束");

        LinearLayout stage = Ui.column(this);
        stage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        stageLp.weight = 1;
        root.addView(stage, stageLp);

        LinearLayout card = Ui.column(this);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.bg(Ui.SURFACE, 20f, this));
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 34), Ui.dp(this, 22), Ui.dp(this, 34));
        stage.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(centerText("这一轮完了", 22f, Ui.TEXT, true));
        card.addView(centerText("斩掉 " + killed + " 个 · 跳过 " + skipped + " 次", 13.5f, Ui.TEXT_SUB, false));

        int left = BookDb.get(this).countUnmastered();
        card.addView(centerText(left == 0
                ? "单词本已经清空，全部掌握"
                : "还有 " + left + " 个词没掌握", 13f,
                left == 0 ? 0xFF067647 : 0xFF6B7280, false));

        LinearLayout acts = Ui.row(this);
        acts.setGravity(Gravity.CENTER);
        acts.setPadding(0, Ui.dp(this, 22), 0, 0);
        if (left > 0) {
            acts.addView(actionButton("再来一轮", 0xFF1F6FEB, 0xFFF0F5FF, new View.OnClickListener() {
                @Override public void onClick(View v) { restart(); }
            }), actionLp(true));
        }
        acts.addView(actionButton("完成", 0xFF6B7280, 0xFFF3F4F6, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }), actionLp(left == 0));
        card.addView(acts);
    }

    private void restart() {
        queue.clear();
        queue.addAll(BookDb.get(this).unmastered());
        pos = 0;
        killed = 0;
        skipped = 0;
        total = queue.size();
        if (queue.isEmpty()) { showSummary(); return; }
        render();
    }

    // ---------- 小部件 ----------

    private TextView centerText(String s, float size, int color, boolean bold) {
        TextView t = Ui.text(this, s, size, color, bold);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        return t;
    }

    private TextView actionButton(String label, int fg, int bg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, Ui.dp(this, 15), 0, Ui.dp(this, 15));
        t.setBackground(Ui.bg(bg, 14f, this));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams actionLp(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        if (left) p.rightMargin = Ui.dp(this, 6);
        else p.leftMargin = Ui.dp(this, 6);
        return p;
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        tts.setLanguage(Locale.US);
                        tts.setSpeechRate(0.9f);
                    }
                }
            });
        } catch (Exception ignored) {
            tts = null;
        }
    }
}
