package com.screenpick.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 悬浮面板的全部 UI。纯代码构建，不依赖任何布局文件——面板是工具的一部分，
 * 它必须在任意应用之上、任意分辨率下都能用，布局文件带来的耦合大于收益。
 *
 * 三个视图：翻译（trans）/ 词条（word）/ 词条内的 AI 记忆分析区。
 */
final class PanelView {

    interface Host {
        void onClose();
        void onCopy(String text);
        void onSpeak(String text);
        void onWordTap(String word, String sentence);
        void onRetry();
        /** 展开 AI 记忆素材（词根、例句、情景、配图）。 */
        void onAnalyzeWord(String word, String sentence);
        /** 收藏进单词本。 */
        void onSaveWord(String word);
        /** 去牛津官网取英文原文释义。 */
        void onOxfordWeb(String word);
        boolean isSaved(String word);
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'\u2019-]{0,28}");

    private final Context c;
    private final Host host;
    final FrameLayout root;

    private final TextView title;
    private final LinearLayout transContent;
    private final LinearLayout wordContent;
    private final ScrollView transScroll;
    private final ScrollView wordScroll;

    private String currentSource = "";
    private boolean hasTranslation = false;
    private String lastTranslated = "";

    // 当前词条上下文，供分析区和收藏按钮使用
    private String curWord = "";
    private String curSentence = "";
    private Dict.Entry curEntry;
    private LinearLayout analysisBox;
    /** 操作栏引用。收藏后要原地换成"已收藏"状态——不能用 instanceof 去找，
     *  变体行也是横向滚动容器，会认错（这个 bug 已经发生过一次）。 */
    private View actionsBar;
    /** 音标区容器；在线英美音标回来后就地替换内容。 */
    private LinearLayout phoneBox;

    PanelView(Context c, Host host) {
        this.c = c;
        this.host = host;

        root = new FrameLayout(c);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onClose(); }
        });

        LinearLayout sheet = Ui.column(c);
        sheet.setBackground(Ui.bg(Ui.SURFACE, 22f, c));
        sheet.setClickable(true); // 吃掉点击，避免点到卡片空白就关闭
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(c.getResources().getDisplayMetrics().heightPixels * 0.72f));
        sheetLp.gravity = Gravity.BOTTOM;
        root.addView(sheet, sheetLp);

        // 头部
        LinearLayout header = Ui.row(c);
        header.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 10));
        title = Ui.text(c, "", 13f, Ui.TEXT_SUB, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.weight = 1;
        header.addView(title, tlp);
        TextView close = Ui.text(c, "\u2715", 17f, Ui.TEXT_SUB, false);
        close.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onClose(); }
        });
        header.addView(close);
        sheet.addView(header);

        // 内容区
        FrameLayout body = new FrameLayout(c);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        bodyLp.weight = 1;
        sheet.addView(body, bodyLp);

        transScroll = new ScrollView(c);
        transScroll.setFillViewport(true);
        transContent = Ui.column(c);
        transContent.setPadding(Ui.dp(c, 18), Ui.dp(c, 2), Ui.dp(c, 18), Ui.dp(c, 18));
        transScroll.addView(transContent);
        body.addView(transScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wordScroll = new ScrollView(c);
        wordScroll.setFillViewport(true);
        wordContent = Ui.column(c);
        wordContent.setPadding(Ui.dp(c, 18), Ui.dp(c, 2), Ui.dp(c, 18), Ui.dp(c, 18));
        wordScroll.addView(wordContent);
        body.addView(wordScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wordScroll.setVisibility(View.GONE);
    }

    // ================= 翻译视图 =================

    void showLoading(String source) {
        currentSource = source;
        hasTranslation = true;
        switchToTrans();
        title.setText("屏幕英文 · 正在翻译");
        transContent.removeAllViews();

        transContent.addView(Ui.text(c, "识别到 " + source.length() + " 个字符，正在翻译…",
                13f, Ui.TEXT_SUB, false));
        TextView src = Ui.text(c, source, Prefs.fontSize(c), 0xFF9CA3AF, false);
        src.setPadding(0, Ui.dp(c, 12), 0, 0);
        transContent.addView(src);
    }

    void showResult(String source, Translator.Result r) {
        showResult(source, r, null);
    }

    void showResult(String source, Translator.Result r, List<Dict.Entry> cetWords) {
        currentSource = source;
        hasTranslation = true;
        switchToTrans();
        title.setText("屏幕英文 · " + Math.max(1, r.text.split("\n").length) + " 段 · " + r.engine);
        lastTranslated = r.text;
        transContent.removeAllViews();

        transContent.addView(Ui.text(c, "原文（点任意单词查词）", 11.5f, Ui.TEXT_SUB, true));

        TextView srcView = Ui.text(c, buildClickable(source), Prefs.fontSize(c), Ui.TEXT, false);
        srcView.setMovementMethod(LinkMovementMethod.getInstance());
        srcView.setHighlightColor(0x221F6FEB);
        transContent.addView(srcView, Ui.lpm(c, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        View div = Ui.divider(c);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 0.7f)));
        dp.topMargin = Ui.dp(c, 16);
        dp.bottomMargin = Ui.dp(c, 14);
        transContent.addView(div, dp);

        transContent.addView(Ui.text(c, "译文", 11.5f, Ui.TEXT_SUB, true));
        TextView trView = Ui.text(c, r.text, Prefs.fontSize(c), 0xFF0F172A, false);
        trView.setTextIsSelectable(true);
        transContent.addView(trView, Ui.lpm(c, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        if (cetWords != null && !cetWords.isEmpty()) {
            View d2 = Ui.divider(c);
            LinearLayout.LayoutParams d2lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 0.7f)));
            d2lp.topMargin = Ui.dp(c, 18);
            d2lp.bottomMargin = Ui.dp(c, 12);
            transContent.addView(d2, d2lp);

            transContent.addView(Ui.text(c, "本屏出现的四六级词（点一下查释义）", 11.5f, Ui.TEXT_SUB, true));
            LinearLayout tags = Ui.row(c);
            tags.setPadding(0, Ui.dp(c, 8), 0, 0);
            for (final Dict.Entry e : cetWords) {
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.rightMargin = Ui.dp(c, 6);
                tags.addView(wordPill(e.word), blp);
            }
            transContent.addView(tags);
        }

        LinearLayout actions = Ui.row(c);
        actions.setPadding(0, Ui.dp(c, 18), 0, 0);
        actions.addView(chip("复制译文", new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onCopy(lastTranslated); }
        }));
        actions.addView(space(8));
        actions.addView(chip("重新抓取", new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onRetry(); }
        }));
        transContent.addView(actions);
    }

    void showError(String message) {
        hasTranslation = true;
        switchToTrans();
        title.setText("屏幕英文 · 出错了");
        transContent.removeAllViews();

        transContent.addView(Ui.text(c, message, 14f, 0xFFB42318, false));
        LinearLayout actions = Ui.row(c);
        actions.setPadding(0, Ui.dp(c, 16), 0, 0);
        actions.addView(chip("重试", new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onRetry(); }
        }));
        transContent.addView(actions);
    }

    // ================= 词条视图 =================

    void showWordLoading(String word) {
        switchToWord();
        title.setText("查词");
        wordContent.removeAllViews();
        wordContent.addView(Ui.text(c, word, 24f, Ui.TEXT, true));
        TextView t = Ui.text(c, "查询中…", 13f, Ui.TEXT_SUB, false);
        t.setPadding(0, Ui.dp(c, 10), 0, 0);
        wordContent.addView(t);
    }

    void showWord(String word, String sentence, Dict.Entry e) {
        switchToWord();
        title.setText("查词");
        curWord = e.word;
        curSentence = sentence == null ? "" : sentence;
        curEntry = e;
        wordContent.removeAllViews();

        // 单词 + 发音按钮
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(c, e.word, 26f, Ui.TEXT, true));
        head.addView(speakButton("美音", e.word, true));
        head.addView(speakButton("英音", e.word, false));
        wordContent.addView(head);

        // 音标区：先用本地那一套占位，在线英美音标到了再换成两行
        phoneBox = Ui.column(c);
        phoneBox.setPadding(0, Ui.dp(c, 6), 0, 0);
        wordContent.addView(phoneBox);
        // 离线只给一条（ECDICT 音标）；在线有道回来后再分英美两套
        renderPhonetics(new Phonetics.Pair(e.phonetic, e.phonetic), true);

        if (e.lemmaOf != null) {
            TextView n = Ui.text(c, "「" + e.lemmaOf + "」是 " + e.word + " 的变形", 12f, Ui.TEXT_SUB, false);
            n.setPadding(0, Ui.dp(c, 4), 0, 0);
            wordContent.addView(n);
        }

        // 考纲徽章
        LinearLayout badges = Ui.row(c);
        badges.setPadding(0, Ui.dp(c, 10), 0, 0);
        int n = 0;
        if (e.isCet4()) { badges.addView(badgePill("CET-4", 0xFFB42318, 0xFFFEF3F2)); n++; }
        if (e.isCet6()) { badges.addView(badgePill("CET-6", 0xFFB54708, 0xFFFFFAEB)); n++; }
        if (e.isKy())   { badges.addView(badgePill("考研", 0xFF6941C6, 0xFFF4F3FF)); n++; }
        if (e.star > 0) { badges.addView(badgePill("柯林斯 " + stars(e.star), 0xFF026AA2, 0xFFF0F9FF)); n++; }
        if (e.ox3000 == 1) { badges.addView(badgePill("牛津3000", 0xFF067647, 0xFFECFDF3)); n++; }
        if (n > 0) wordContent.addView(badges);

        // 释义：考试向释义优先，专业义项沉底
        View meaning = Meaning.renderEntry(c, e, Prefs.fontSize(c) + 1);
        meaning.setPadding(0, Ui.dp(c, 14), 0, 0);
        wordContent.addView(meaning);

        // 词形变化：让学生看到这个词在句子里会长什么样，并能直接点进变形词
        java.util.List<String[]> variants = e.variants();
        if (!variants.isEmpty()) {
            TextView vl = Ui.text(c, "词形变化", 11.5f, Ui.TEXT_SUB, true);
            vl.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 6));
            wordContent.addView(vl);

            LinearLayout vrow = Ui.row(c);
            vrow.setGravity(Gravity.CENTER_VERTICAL);
            for (String[] v : variants) {
                LinearLayout one = Ui.column(c);
                one.setPadding(0, 0, Ui.dp(c, 10), Ui.dp(c, 6));
                TextView tag = Ui.text(c, v[0], 10f, 0xFF9CA3AF, false);
                one.addView(tag);
                TextView form = Ui.text(c, v[1], 13f, Ui.BRAND, true);
                form.setPadding(0, Ui.dp(c, 1), 0, 0);
                one.addView(form);
                one.setClickable(true);
                one.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        PanelView.this.host.onWordTap(v[1], null);
                    }
                });
                vrow.addView(one);
            }
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(vrow);
            wordContent.addView(hs);
        }

        // 上下文例句
        if (!curSentence.isEmpty()) {
            View d = Ui.divider(c);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 0.7f)));
            dlp.topMargin = Ui.dp(c, 16);
            dlp.bottomMargin = Ui.dp(c, 12);
            wordContent.addView(d, dlp);
            wordContent.addView(Ui.text(c, "它在句子里是这样用的", 11.5f, Ui.TEXT_SUB, true));
            TextView sc = Ui.text(c, curSentence, 13.5f, 0xFF374151, false);
            sc.setPadding(0, Ui.dp(c, 6), 0, 0);
            sc.setTextIsSelectable(true);
            wordContent.addView(sc);
        }

        // 操作栏（可横向滚动，防止小屏挤爆）
        actionsBar = buildWordActions();
        wordContent.addView(actionsBar);

        // AI 记忆分析区（点按钮后填充）
        analysisBox = Ui.column(c);
        wordContent.addView(analysisBox);
    }

    /** 在线英美音标回来了：有差异就上下两行，没差异就标一句"英美同音"。 */
    void updatePhonetics(Phonetics.Pair p, String forWord) {
        if (phoneBox == null || p == null || p.empty()) return;
        // 回调到达时用户可能已经翻到别的词了，对不上就丢掉
        if (forWord == null || !forWord.equalsIgnoreCase(curWord)) return;
        renderPhonetics(p, false);
    }

    private void renderPhonetics(Phonetics.Pair p, boolean local) {
        if (phoneBox == null) return;
        phoneBox.removeAllViews();
        if (p.empty()) return;

        if (local) {
            // 离线词表的英美音标存在错位（实测 context 的美英是反的），
            // 所以这里只给一条、不贴"美/英"标签；等有道回来再分两套。
            String one = p.us.isEmpty() ? p.uk : p.us;
            if (!one.isEmpty()) {
                LinearLayout row = Ui.row(c);
                row.addView(Ui.text(c, "/" + one + "/", 13f, Ui.TEXT_SUB, false));
                phoneBox.addView(row);
            }
            return;
        }

        if (p.same()) {
            LinearLayout row = Ui.row(c);
            row.addView(Ui.text(c, "/" + p.us + "/", 13f, Ui.TEXT_SUB, false));
            if (!local) {
                TextView tag = Ui.text(c, "英美同音", 10f, 0xFF9CA3AF, false);
                tag.setPadding(Ui.dp(c, 8), 0, 0, 0);
                row.addView(tag);
            }
            phoneBox.addView(row);
        } else {
            phoneBox.addView(phonemeRow("美", p.us, Ui.BRAND, 0xFFF0F5FF));
            phoneBox.addView(phonemeRow("英", p.uk, 0xFF6941C6, 0xFFF4F3FF));
        }
    }

    private View phonemeRow(String label, String ipa, int fg, int bg) {
        LinearLayout row = Ui.row(c);
        row.setPadding(0, Ui.dp(c, 3), 0, 0);
        TextView tag = Ui.text(c, label, 10f, fg, true);
        tag.setPadding(Ui.dp(c, 5), Ui.dp(c, 1), Ui.dp(c, 5), Ui.dp(c, 2));
        tag.setBackground(Ui.bg(bg, 5f, c));
        row.addView(tag);
        TextView v = Ui.text(c, "/" + ipa + "/", 13.5f, 0xFF374151, false);
        v.setPadding(Ui.dp(c, 7), 0, 0, 0);
        v.setTextIsSelectable(true);
        row.addView(v);
        return row;
    }

    /** 真人发音按钮：美音 / 英音。音频走在线词典，失败自动回退本地 TTS。 */
    private TextView speakButton(String label, final String word, final boolean us) {
        TextView t = new TextView(c);
        t.setText("\u25B6 " + label);
        t.setTextSize(11.5f);
        t.setTextColor(us ? Ui.BRAND : 0xFF6941C6);
        t.setPadding(Ui.dp(c, 9), Ui.dp(c, 3), Ui.dp(c, 9), Ui.dp(c, 4));
        t.setBackground(Ui.bg(us ? 0xFFF0F5FF : 0xFFF4F3FF, 7f, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(c, 8);
        lp.bottomMargin = Ui.dp(c, 3);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (us) Pronounce.us(c, word);
                else Pronounce.uk(c, word);
            }
        });
        return t;
    }

    private View buildWordActions() {
        final boolean saved = host.isSaved(curWord);
        LinearLayout actions = Ui.row(c);
        actions.setPadding(0, Ui.dp(c, 18), 0, 0);

        actions.addView(chip(saved ? "\u5df2\u6536\u85cf \u2713" : "\u6536\u85cf",
                0xFF067647, 0xFFECFDF3, saved ? null : new View.OnClickListener() {
            @Override public void onClick(View v) {
                PanelView.this.host.onSaveWord(curWord);
            }
        }));
        actions.addView(space(8));
        actions.addView(chip("AI 记忆", new View.OnClickListener() {
            @Override public void onClick(View v) {
                PanelView.this.host.onAnalyzeWord(curWord, curSentence);
            }
        }));
        actions.addView(space(8));
        actions.addView(chip("牛津原文", new View.OnClickListener() {
            @Override public void onClick(View v) {
                PanelView.this.host.onOxfordWeb(curWord);
            }
        }));
        actions.addView(space(8));
        actions.addView(chip("复制", new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onCopy(curWord); }
        }));
        actions.addView(space(8));
        actions.addView(chip("返回", new View.OnClickListener() {
            @Override public void onClick(View v) { switchToTrans(); }
        }));

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(actions);
        return hs;
    }

    /** 收藏成功后原地换掉操作栏，不整页重绘（避免滚动位置跳动）。 */
    void markSaved() {
        if (actionsBar == null) return;
        android.view.ViewParent parent = actionsBar.getParent();
        if (!(parent instanceof LinearLayout)) return;
        LinearLayout box = (LinearLayout) parent;
        int idx = box.indexOfChild(actionsBar);
        if (idx < 0) return;
        box.removeViewAt(idx);
        actionsBar = buildWordActions();
        box.addView(actionsBar, idx);
    }

    // ---------- 牛津官网原文 ----------

    void showOxfordLoading() {
        if (analysisBox == null) return;
        analysisBox.removeAllViews();
        analysisBox.addView(sectionDivider());
        analysisBox.addView(sectionTitle("牛津词典原文"));
        analysisBox.addView(Ui.text(c, "正在取 Oxford Learner's Dictionaries…", 12.5f, Ui.TEXT_SUB, false));
    }

    void showOxford(OxfordWeb.Def d) {
        if (analysisBox == null) return;
        analysisBox.removeAllViews();
        analysisBox.addView(sectionDivider());
        analysisBox.addView(sectionTitle("牛津词典原文"));

        if (d.notFound) {
            analysisBox.addView(Ui.text(c, "牛津官网没有收录这个词。", 13f, Ui.TEXT_SUB, false));
            return;
        }
        if (!d.ok()) {
            analysisBox.addView(Ui.text(c, d.error, 13f, 0xFFB42318, false));
            return;
        }

        LinearLayout head = Ui.row(c);
        if (!d.pos.isEmpty()) {
            head.addView(Ui.text(c, d.pos, 12.5f, 0xFF067647, true));
        }
        if (!d.ipa.isEmpty()) {
            TextView ipa = Ui.text(c, d.ipa, 12.5f, Ui.TEXT_SUB, false);
            if (!d.pos.isEmpty()) ipa.setPadding(Ui.dp(c, 8), 0, 0, 0);
            head.addView(ipa);
        }
        analysisBox.addView(head);

        for (int i = 0; i < d.defs.size(); i++) {
            LinearLayout one = Ui.row(c);
            one.setGravity(Gravity.TOP);
            one.setPadding(0, Ui.dp(c, 8), 0, 0);
            TextView num = Ui.text(c, String.valueOf(i + 1), 10.5f, 0xFF9CA3AF, true);
            num.setPadding(Ui.dp(c, 4), Ui.dp(c, 1), Ui.dp(c, 4), Ui.dp(c, 2));
            num.setBackground(Ui.bg(0xFFF3F4F6, 5f, c));
            one.addView(num);
            TextView def = Ui.text(c, d.defs.get(i), 13.5f, 0xFF374151, false);
            def.setPadding(Ui.dp(c, 8), 0, 0, 0);
            def.setTextIsSelectable(true);
            one.addView(def, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            analysisBox.addView(one);
        }

        TextView note = Ui.text(c, "来源：oxfordlearnersdictionaries.com", 10f, 0xFF9CA3AF, false);
        note.setPadding(0, Ui.dp(c, 12), 0, 0);
        analysisBox.addView(note);
    }

    // ---------- AI 记忆分析 ----------

    void showAnalysisLoading() {
        if (analysisBox == null) return;
        analysisBox.removeAllViews();
        analysisBox.addView(sectionDivider());
        analysisBox.addView(sectionTitle("AI 记忆"));
        analysisBox.addView(Ui.text(c, "正在拆解这个词…", 13f, Ui.TEXT_SUB, false));
    }

    void showAnalysisError(String msg) {
        if (analysisBox == null) return;
        analysisBox.removeAllViews();
        analysisBox.addView(sectionDivider());
        analysisBox.addView(sectionTitle("AI 记忆"));
        analysisBox.addView(Ui.text(c, msg, 13f, 0xFFB42318, false));
    }

    void showAnalysis(Translator.WordAnalysis a) {
        if (analysisBox == null) return;
        analysisBox.removeAllViews();
        analysisBox.addView(sectionDivider());
        analysisBox.addView(sectionTitle("AI 记忆"));

        // 配图：先占位，网络加载成功后替换
        String url = ImageLoader.sceneUrl(a.imagePrompt, curWord);
        if (!url.isEmpty()) {
            final ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.bg(0xFFF3F4F6, 14f, c));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 150));
            ilp.topMargin = Ui.dp(c, 10);
            analysisBox.addView(iv, ilp);

            final TextView imgTip = Ui.text(c, "配图生成中…", 11f, 0xFF9CA3AF, false);
            imgTip.setPadding(0, Ui.dp(c, 6), 0, 0);
            analysisBox.addView(imgTip);

            ImageLoader.load(url, iv, new ImageLoader.Callback() {
                @Override public void onResult(boolean ok) {
                    if (ok) imgTip.setVisibility(View.GONE);
                    else imgTip.setText("配图没加载出来（网络或服务限流），不影响记忆");
                }
            });
        }

        if (!a.roots.isEmpty()) block("词根词缀", a.roots);
        if (!a.memory.isEmpty()) block("怎么记", a.memory);

        // 谐音单独放一块，并且明确标注它不是真实发音——用错了会带偏口语
        if (!a.homophone.isEmpty()) {
            LinearLayout hb = Ui.column(c);
            hb.setBackground(Ui.bg(0xFFFFFBEB, 10f, c));
            hb.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = Ui.dp(c, 14);
            analysisBox.addView(hb, hlp);
            hb.addView(Ui.text(c, "谐音助记 · 只帮记忆，不是真实发音", 10.5f, 0xFFB45309, true));
            TextView ht = Ui.text(c, a.homophone, 13.5f, 0xFF78350F, false);
            ht.setPadding(0, Ui.dp(c, 4), 0, 0);
            ht.setTextIsSelectable(true);
            hb.addView(ht);
        }

        if (!a.scene.isEmpty()) block("情景", "「" + a.scene + "」");

        if (!a.examples.isEmpty()) {
            analysisBox.addView(blockLabel("例句"));
            for (String[] ex : a.examples) {
                LinearLayout one = Ui.column(c);
                one.setPadding(0, Ui.dp(c, 8), 0, 0);
                TextView en = Ui.text(c, buildClickable(ex[0]), 13.5f, 0xFF0F172A, false);
                en.setMovementMethod(LinkMovementMethod.getInstance());
                en.setHighlightColor(0x221F6FEB);
                one.addView(en);
                if (ex.length > 1 && !ex[1].isEmpty()) {
                    TextView cn = Ui.text(c, ex[1], 12.5f, Ui.TEXT_SUB, false);
                    cn.setPadding(0, Ui.dp(c, 3), 0, 0);
                    one.addView(cn);
                }
                analysisBox.addView(one);
            }
        }

        LinearLayout acts = Ui.row(c);
        acts.setPadding(0, Ui.dp(c, 16), 0, 0);
        acts.addView(chip("再看看", new View.OnClickListener() {
            @Override public void onClick(View v) {
                PanelView.this.host.onAnalyzeWord(curWord, curSentence);
            }
        }));
        analysisBox.addView(acts);
    }

    private void block(String label, String body) {
        analysisBox.addView(blockLabel(label));
        TextView t = Ui.text(c, body, 13.5f, 0xFF374151, false);
        t.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        t.setTextIsSelectable(true);
        analysisBox.addView(t);
    }

    private TextView blockLabel(String label) {
        TextView t = Ui.text(c, label, 11.5f, Ui.BRAND, true);
        t.setPadding(0, Ui.dp(c, 14), 0, 0);
        return t;
    }

    private View sectionDivider() {
        View d = Ui.divider(c);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 0.7f)));
        dlp.topMargin = Ui.dp(c, 20);
        dlp.bottomMargin = Ui.dp(c, 10);
        d.setLayoutParams(dlp);
        return d;
    }

    private TextView sectionTitle(String s) {
        return Ui.text(c, s, 12f, Ui.TEXT_SUB, true);
    }

    void showWordMissing(String word, String sentence, boolean canOnline) {
        switchToWord();
        title.setText("查词");
        curWord = word;
        curSentence = sentence == null ? "" : sentence;
        curEntry = null;
        wordContent.removeAllViews();

        wordContent.addView(Ui.text(c, word, 26f, Ui.TEXT, true));
        TextView t = Ui.text(c, "内置词库（四六级 + 高频 11228 词）里没有这个词。", 13f, Ui.TEXT_SUB, false);
        t.setPadding(0, Ui.dp(c, 10), 0, 0);
        wordContent.addView(t);

        LinearLayout actions = Ui.row(c);
        actions.setPadding(0, Ui.dp(c, 16), 0, 0);
        if (canOnline) {
            actions.addView(chip("AI 记忆", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    PanelView.this.host.onAnalyzeWord(word, sentence);
                }
            }));
            actions.addView(space(8));
        }
        actions.addView(chip("收藏", 0xFF067647, 0xFFECFDF3, new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onSaveWord(word); }
        }));
        actions.addView(space(8));
        actions.addView(chip("复制", new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onCopy(word); }
        }));
        actions.addView(space(8));
        actions.addView(chip("返回", new View.OnClickListener() {
            @Override public void onClick(View v) { switchToTrans(); }
        }));
        actionsBar = actions;
        wordContent.addView(actionsBar);

        analysisBox = Ui.column(c);
        wordContent.addView(analysisBox);
    }

    // ================= 工具 =================

    private void switchToTrans() {
        if (!hasTranslation) {
            host.onClose();
            return;
        }
        transScroll.setVisibility(View.VISIBLE);
        wordScroll.setVisibility(View.GONE);
        transScroll.scrollTo(0, 0);
    }

    private void switchToWord() {
        transScroll.setVisibility(View.GONE);
        wordScroll.setVisibility(View.VISIBLE);
        wordScroll.scrollTo(0, 0);
    }

    private CharSequence buildClickable(String text) {
        SpannableString ss = new SpannableString(text);
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            final String w = m.group();
            if (w.length() < 2) continue;
            ss.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    PanelView.this.host.onWordTap(w, null);
                }
                @Override public void updateDrawState(TextPaint ds) {
                    ds.setUnderlineText(false);
                    ds.setColor(0xFF14181F);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    private TextView wordPill(final String word) {
        TextView b = new TextView(c);
        b.setText(word);
        b.setTextSize(12.5f);
        b.setTextColor(Ui.BRAND);
        b.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 6));
        b.setBackground(Ui.bg(0xFFF0F5FF, 8f, c));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PanelView.this.host.onWordTap(word, null); }
        });
        return b;
    }

    private TextView chip(String label, View.OnClickListener l) {
        return chip(label, 0xFF374151, 0xFFF9FAFB, l);
    }

    private TextView chip(String label, int fg, int bg, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(13f);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 9));
        t.setBackground(Ui.bgStroke(bg, 20f, Ui.LINE, 1f, c));
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    private TextView badgePill(String label, int fg, int bg) {
        TextView t = Ui.badge(c, label, fg, bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = Ui.dp(c, 6);
        t.setLayoutParams(p);
        return t;
    }

    private View space(int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, dp), 1));
        return v;
    }

    private String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('\u2605');
        return sb.toString();
    }
}
