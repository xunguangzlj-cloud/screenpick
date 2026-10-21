package com.screenpick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

/** 单词本：按收藏当天分组，顶部进复习和编故事。 */
public class WordbookActivity extends Activity {

    private LinearLayout listBox;
    private TextView statLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
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

        // 头部
        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "\u2190", 22f, Ui.TEXT, false);
        back.setPadding(0, 0, Ui.dp(this, 14), Ui.dp(this, 2));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        head.addView(Ui.text(this, "单词本", 26f, Ui.TEXT, true));
        root.addView(head);

        statLine = Ui.text(this, "", 12.5f, Ui.TEXT_SUB, false);
        statLine.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(statLine);

        // 操作
        LinearLayout acts = Ui.row(this);
        acts.setPadding(0, Ui.dp(this, 16), 0, 0);
        acts.addView(bigButton("开始复习", Ui.BRAND, Color.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (BookDb.get(WordbookActivity.this).countUnmastered() == 0) {
                    toast("没有待复习的词，先收藏几个");
                    return;
                }
                startActivity(new Intent(WordbookActivity.this, ReviewActivity.class));
            }
        }), half(true));
        acts.addView(bigButton("编个故事", 0xFFF0F5FF, Ui.BRAND, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (BookDb.get(WordbookActivity.this).countUnmastered() == 0) {
                    toast("先收藏几个还没掌握的词");
                    return;
                }
                startActivity(new Intent(WordbookActivity.this, StoryActivity.class));
            }
        }), half(false));
        root.addView(acts);

        listBox = Ui.column(this);
        root.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return scroll;
    }

    private void refresh() {
        BookDb db = BookDb.get(this);
        int total = db.count();
        int mastered = db.countMastered();
        statLine.setText(total == 0
                ? "还没有收藏。点屏幕上的词，在释义卡里按「收藏」。"
                : "共 " + total + " 词 · 已掌握 " + mastered + " · 待复习 " + (total - mastered));

        listBox.removeAllViews();
        if (total == 0) return;

        Map<String, List<BookDb.Item>> byDay = db.byDay();
        for (Map.Entry<String, List<BookDb.Item>> en : byDay.entrySet()) {
            List<BookDb.Item> items = en.getValue();

            LinearLayout dayHead = Ui.row(this);
            dayHead.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
            dayHead.addView(Ui.text(this, BookDb.dayLabel(en.getKey()), 13.5f, Ui.TEXT, true));
            TextView cnt = Ui.text(this, "  " + items.size() + " 词", 11.5f, Ui.TEXT_SUB, false);
            dayHead.addView(cnt);
            listBox.addView(dayHead);

            LinearLayout card = Ui.column(this);
            card.setBackground(Ui.bg(Ui.SURFACE, 14f, this));
            listBox.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < items.size(); i++) {
                final BookDb.Item it = items.get(i);
                card.addView(row(it));
                if (i < items.size() - 1) {
                    View d = Ui.divider(this);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.7f)));
                    dlp.leftMargin = Ui.dp(this, 14);
                    card.addView(d, dlp);
                }
            }
        }
    }

    private View row(final BookDb.Item it) {
        LinearLayout wrap = Ui.column(this);
        wrap.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 13));
        wrap.setClickable(true);

        LinearLayout top = Ui.row(this);
        TextView w = Ui.text(this, it.word, 16f, it.mastered ? 0xFF9CA3AF : Ui.TEXT, true);
        top.addView(w);

        if (!it.phonetic.isEmpty()) {
            TextView ph = Ui.text(this, " /" + it.phonetic + "/", 11.5f, Ui.TEXT_SUB, false);
            top.addView(ph);
        }
        if (it.isCet6()) top.addView(tagPill("CET-6", 0xFFB54708, 0xFFFFFAEB));
        else if (it.isCet4()) top.addView(tagPill("CET-4", 0xFFB42318, 0xFFFEF3F2));
        if (it.mastered) top.addView(tagPill("已掌握", 0xFF067647, 0xFFECFDF3));

        wrap.addView(top);

        String trans = it.trans.replace("\\n", " ").replace('\n', ' ').trim();
        TextView t = Ui.text(this, trans, 12.5f, 0xFF6B7280, false);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(this, 4), 0, 0);
        wrap.addView(t);

        wrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { WordPop.show(WordbookActivity.this, it.word); }
        });
        wrap.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                confirmRemove(it);
                return true;
            }
        });
        return wrap;
    }

    private void confirmRemove(final BookDb.Item it) {
        new AlertDialog.Builder(this)
                .setTitle(it.word)
                .setItems(new String[]{"标记为已掌握", "从单词本删除"}, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            BookDb.get(WordbookActivity.this).setMastered(it.word, !it.mastered);
                            toast(it.mastered ? "已移回复习队列" : "已标记掌握");
                        } else {
                            BookDb.get(WordbookActivity.this).remove(it.word);
                            toast("已删除");
                        }
                        refresh();
                    }
                })
                .show();
    }

    // ---------- 小部件 ----------

    private TextView bigButton(String label, int bgColor, int fgColor, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14.5f);
        t.setTextColor(fgColor);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 13));
        t.setBackground(Ui.bg(bgColor, 13f, this));
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

    private TextView tagPill(String label, int fg, int bg) {
        TextView t = Ui.badge(this, label, fg, bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
