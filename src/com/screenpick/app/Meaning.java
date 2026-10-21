package com.screenpick.app;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 释义排版。
 *
 * 显示顺序按"权威度"排：
 *   1. 牛津高阶释义（义项顺序最贴近母语词典）—— 第一行加粗，其余灰色
 *   2. 没有牛津时退回考试向简释，再退回 ECDICT 完整释义
 *   3. 英文释义 / 专业义项 / 备选中释 全部收进「更多释义」，点开才展开
 *
 * 这样默认视野里只有一条干净的主释义；实测词库里 43% 的词有牛津释义、
 * 99% 有 WordNet 英英释义、37% 带专业义项，全铺开会把卡片塞满。
 */
final class Meaning {

    private Meaning() {}

    /** 词条的统一渲染入口。 */
    static View renderEntry(final Context c, Dict.Entry e, float baseSp) {
        LinearLayout box = Ui.column(c);

        String main = e.primaryMeaning();

        if (!e.oxford.isEmpty()) {
            TextView badge = Ui.text(c, "牛津释义", 10f, 0xFF067647, true);
            badge.setPadding(0, 0, 0, Ui.dp(c, 4));
            box.addView(badge);
        }

        String[] lines = main.split("\n");
        TextView head = Ui.text(c, lines[0], baseSp + 1.5f, 0xFF0F172A, true);
        head.setTextIsSelectable(true);
        head.setLineSpacing(Ui.dp(c, 3), 1f);
        box.addView(head);

        if (lines.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            TextView rest = Ui.text(c, sb.toString(), baseSp - 0.5f, 0xFF6B7280, false);
            rest.setPadding(0, Ui.dp(c, 7), 0, 0);
            rest.setTextIsSelectable(true);
            box.addView(rest);
        }

        if (e.hasMore()) {
            final LinearLayout more = Ui.column(c);
            more.setVisibility(View.GONE);

            String alt = e.altMeaning();
            if (!alt.isEmpty()) addBlock(c, more, "其他释义", alt, baseSp);

            if (!e.ee.isEmpty()) addBlock(c, more, "英文释义", e.ee, baseSp - 0.5f);

            if (!e.extra.isEmpty()) {
                addBlock(c, more, "专业义项", e.extra.replace("\n", "  "), baseSp - 2f);
            }

            final TextView toggle = Ui.text(c, "\u25B8 更多释义", 12f, Ui.BRAND, true);
            toggle.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean show = more.getVisibility() != View.VISIBLE;
                    more.setVisibility(show ? View.VISIBLE : View.GONE);
                    toggle.setText(show ? "\u25BE 收起" : "\u25B8 更多释义");
                }
            });
            box.addView(toggle);
            box.addView(more);
        }

        return box;
    }

    private static void addBlock(Context c, LinearLayout parent, String label, String body, float sp) {
        TextView lb = Ui.text(c, label, 10f, 0xFF9CA3AF, true);
        lb.setPadding(0, Ui.dp(c, 10), 0, 0);
        parent.addView(lb);
        TextView t = Ui.text(c, body, sp, 0xFF6B7280, false);
        t.setPadding(0, Ui.dp(c, 3), 0, 0);
        t.setTextIsSelectable(true);
        parent.addView(t);
    }

    /** 纯文本释义的兜底渲染（没有 Entry 时用，比如单词本里的旧快照）。 */
    static View render(Context c, String trans, float baseSp) {
        LinearLayout box = Ui.column(c);
        String[] parts = split(trans);
        TextView head = Ui.text(c, parts[0], baseSp + 1.5f, 0xFF0F172A, true);
        head.setTextIsSelectable(true);
        box.addView(head);
        if (!parts[1].isEmpty()) {
            TextView rest = Ui.text(c, parts[1], baseSp - 0.5f, 0xFF6B7280, false);
            rest.setPadding(0, Ui.dp(c, 7), 0, 0);
            rest.setTextIsSelectable(true);
            box.addView(rest);
        }
        return box;
    }

    /** @return [主释义, 其余释义] */
    static String[] split(String trans) {
        if (trans == null) return new String[]{"", ""};
        // ECDICT 的多义项之间存的是字面 "\n"，不是真实换行，两种都要处理
        String t = trans.replace("\r", "").replace("\\n", "\n").trim();
        if (t.isEmpty()) return new String[]{"", ""};

        String[] lines = t.split("\n");
        String first = lines[0].trim();
        int cut = firstSeparator(first);
        String head;
        String rest0;
        if (cut > 0) {
            head = first.substring(0, cut).trim();
            rest0 = first.substring(cut + 1).trim();
        } else {
            head = first;
            rest0 = "";
        }
        StringBuilder rest = new StringBuilder(rest0);
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.isEmpty()) continue;
            if (rest.length() > 0) rest.append('\n');
            rest.append(l);
        }
        if (head.isEmpty() && rest.length() > 0) {
            return new String[]{rest.toString(), ""};
        }
        return new String[]{head, rest.toString()};
    }

    private static int firstSeparator(String s) {
        int a = s.indexOf(',');
        int b = s.indexOf('\uFF0C');
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }
}
