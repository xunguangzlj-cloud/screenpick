package com.screenpick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 普通页面里的"点词看释义"和常用弹窗。 */
final class WordPop {

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'\u2019-]{0,28}");

    private WordPop() {}

    /** 把一段英文变成可点单词的富文本；点击弹词条。 */
    static CharSequence linkify(final Activity act, String text) {
        SpannableString ss = new SpannableString(text);
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            final String w = m.group();
            if (w.length() < 2) continue;
            ss.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    show(act, w);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    /** 查词并弹窗。本地没有词条就提示一句，不联网（联网查词留给悬浮面板）。 */
    static void show(final Activity act, String raw) {
        String word = raw == null ? "" : raw.trim();
        if (word.isEmpty()) return;

        Dict.Entry e = null;
        try {
            e = Dict.get(act).lookup(word);
        } catch (Exception ignored) {}

        LinearLayout box = Ui.column(act);
        int p = Ui.dp(act, 20);
        box.setPadding(p, Ui.dp(act, 16), p, Ui.dp(act, 8));

        if (e == null) {
            box.addView(Ui.text(act, word, 22f, Ui.TEXT, true));
            TextView t = Ui.text(act, "内置词库里没有这个词。", 13.5f, Ui.TEXT_SUB, false);
            t.setPadding(0, Ui.dp(act, 8), 0, 0);
            box.addView(t);
            copyOn(act, box, word);
            new AlertDialog.Builder(act).setView(box).setPositiveButton("关闭", null).show();
            return;
        }

        LinearLayout head = Ui.row(act);
        head.addView(Ui.text(act, e.word, 22f, Ui.TEXT, true));
        box.addView(head);

        final LinearLayout phoneBox = Ui.column(act);
        phoneBox.setPadding(0, Ui.dp(act, 4), 0, 0);
        box.addView(phoneBox);
        loadPhonetics(act, e.word, phoneBox, e.phonetic, e.phonetic);

        LinearLayout badges = Ui.row(act);
        badges.setPadding(0, Ui.dp(act, 8), 0, 0);
        int n = 0;
        if (e.isCet4()) { badges.addView(pill(act, "CET-4", 0xFFB42318, 0xFFFEF3F2)); n++; }
        if (e.isCet6()) { badges.addView(pill(act, "CET-6", 0xFFB54708, 0xFFFFFAEB)); n++; }
        if (e.star > 0) { badges.addView(pill(act, "柯林斯 " + e.star + "星", 0xFF026AA2, 0xFFF0F9FF)); n++; }
        if (n > 0) box.addView(badges);

        View meaning = Meaning.renderEntry(act, e, 15f);
        meaning.setPadding(0, Ui.dp(act, 12), 0, 0);
        box.addView(meaning);

        LinearLayout acts = Ui.row(act);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(act, 14);
        acts.addView(soundButton(act, "美音", e.word, true), halfLp(act, true));
        acts.addView(soundButton(act, "英音", e.word, false), halfLp(act, false));
        box.addView(acts, alp);

        // 牛津官网原文：点击才取，结果追加在弹窗底部
        final ScrollView sv = new ScrollView(act);
        final LinearLayout oxBox = Ui.column(act);
        final TextView oxBtn = new TextView(act);
        final String oxWord = e.word;
        oxBtn.setText("取牛津词典原文释义");
        oxBtn.setTextSize(13f);
        oxBtn.setTextColor(0xFF067647);
        oxBtn.setGravity(android.view.Gravity.CENTER);
        oxBtn.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        GradientDrawable obg = new GradientDrawable();
        obg.setColor(0xFFECFDF3);
        obg.setCornerRadius(Ui.dp(act, 10));
        oxBtn.setBackground(obg);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = Ui.dp(act, 10);
        box.addView(oxBtn, olp);
        box.addView(oxBox);

        oxBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                oxBtn.setText("正在取 Oxford Learner's Dictionaries…");
                oxBtn.setEnabled(false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final OxfordWeb.Def d = OxfordWeb.fetch(oxWord);
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (act.isFinishing()) return;
                                oxBtn.setVisibility(View.GONE);
                                oxBox.addView(renderOxford(act, d));
                                // 结果在弹窗下方，不滚过去用户会以为没反应
                                sv.post(new Runnable() {
                                    @Override public void run() {
                                        sv.fullScroll(View.FOCUS_DOWN);
                                    }
                                });
                            }
                        });
                    }
                }).start();
            }
        });

        sv.addView(box);
        new AlertDialog.Builder(act).setView(sv).setPositiveButton("关闭", null).show();
    }

    /** 牛津官网原文的展示块。 */
    private static View renderOxford(Activity act, OxfordWeb.Def d) {
        LinearLayout box = Ui.column(act);
        TextView title = Ui.text(act, "牛津词典原文", 10.5f, 0xFF067647, true);
        title.setPadding(0, Ui.dp(act, 6), 0, 0);
        box.addView(title);

        if (d.notFound) {
            box.addView(Ui.text(act, "牛津官网没有收录这个词。", 13f, Ui.TEXT_SUB, false));
            return box;
        }
        if (!d.ok()) {
            box.addView(Ui.text(act, d.error, 12.5f, 0xFFB42318, false));
            return box;
        }

        LinearLayout head = Ui.row(act);
        head.setPadding(0, Ui.dp(act, 2), 0, 0);
        if (!d.pos.isEmpty()) head.addView(Ui.text(act, d.pos, 12f, 0xFF067647, true));
        if (!d.ipa.isEmpty()) {
            TextView ipa = Ui.text(act, d.ipa, 12f, Ui.TEXT_SUB, false);
            if (!d.pos.isEmpty()) ipa.setPadding(Ui.dp(act, 8), 0, 0, 0);
            head.addView(ipa);
        }
        box.addView(head);

        for (int i = 0; i < d.defs.size(); i++) {
            TextView one = Ui.text(act, (i + 1) + ". " + d.defs.get(i), 13f, 0xFF374151, false);
            one.setPadding(0, Ui.dp(act, 5), 0, 0);
            one.setTextIsSelectable(true);
            box.addView(one);
        }
        TextView src = Ui.text(act, "来源：oxfordlearnersdictionaries.com", 10f, 0xFF9CA3AF, false);
        src.setPadding(0, Ui.dp(act, 8), 0, 0);
        box.addView(src);
        return box;
    }

    /** 先显示本地音标，在线英美音标到了再替换；拿不到就保持原样。 */
    private static void loadPhonetics(final Activity act, final String word,
                                      final LinearLayout box, String offUs, String offUk) {
        // 离线词表的英美音标有错位风险，先只给一条不带标签的；有道回来再分两套
        String one = (offUs != null && !offUs.isEmpty()) ? offUs : offUk;
        if (one != null && !one.isEmpty()) {
            box.addView(ipaLine(act, null, one, Ui.TEXT_SUB, 0, null));
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Phonetics.Pair p = Phonetics.fetch(word);
                    if (p.empty()) return;
                    act.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (act.isFinishing() || box.getParent() == null) return;
                            box.removeAllViews();
                            if (p.same()) {
                                box.addView(ipaLine(act, null, p.us, Ui.TEXT_SUB, 0, "英美同音"));
                            } else {
                                box.addView(ipaLine(act, "美", p.us, 0xFF374151, 0xFFF0F5FF, null));
                                box.addView(ipaLine(act, "英", p.uk, 0xFF374151, 0xFFF4F3FF, null));
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static View ipaLine(Activity act, String label, String ipa, int color, int tagBg,
                                String suffix) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 2), 0, 0);
        if (label != null) {
            TextView tag = Ui.text(act, label, 10f, Ui.BRAND, true);
            tag.setPadding(Ui.dp(act, 5), Ui.dp(act, 1), Ui.dp(act, 5), Ui.dp(act, 2));
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(tagBg);
            tbg.setCornerRadius(Ui.dp(act, 5));
            tag.setBackground(tbg);
            row.addView(tag);
        }
        TextView v = Ui.text(act, "/" + ipa + "/", 13f, color, false);
        if (label != null) v.setPadding(Ui.dp(act, 7), 0, 0, 0);
        row.addView(v);
        if (suffix != null) {
            TextView sfx = Ui.text(act, suffix, 10f, 0xFF9CA3AF, false);
            sfx.setPadding(Ui.dp(act, 7), 0, 0, 0);
            row.addView(sfx);
        }
        return row;
    }

    private static TextView soundButton(final Activity act, String label,
                                        final String word, final boolean us) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(13f);
        t.setTextColor(Ui.BRAND);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF0F5FF);
        bg.setCornerRadius(Ui.dp(act, 10));
        t.setBackground(bg);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (us) Pronounce.us(act, word);
                else Pronounce.uk(act, word);
            }
        });
        return t;
    }

    private static LinearLayout.LayoutParams halfLp(Activity act, boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        if (left) p.rightMargin = Ui.dp(act, 5);
        else p.leftMargin = Ui.dp(act, 5);
        return p;
    }

    private static void copyOn(final Activity act, LinearLayout box, final String word) {
        TextView btn = new TextView(act);
        btn.setText("复制单词");
        btn.setTextSize(13f);
        btn.setTextColor(Ui.BRAND);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF0F5FF);
        bg.setCornerRadius(Ui.dp(act, 10));
        btn.setBackground(bg);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) act.getSystemService(Activity.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("screenpick", word));
                    Toast.makeText(act, "已复制 " + word, Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(act, 14);
        box.addView(btn, lp);
    }

    private static TextView pill(Activity a, String label, int fg, int bg) {
        TextView t = Ui.badge(a, label, fg, bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 6);
        t.setLayoutParams(lp);
        return t;
    }

    /** 统一一段英文可点文本的观感。 */
    static void styleLink(TextView tv) {
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(0x221F6FEB);
        tv.setLinkTextColor(Color.TRANSPARENT);
    }
}
