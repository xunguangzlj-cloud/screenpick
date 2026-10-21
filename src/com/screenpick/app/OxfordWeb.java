package com.screenpick.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 牛津学习词典（oxfordlearnersdictionaries.com）的在线原文释义。
 *
 * 为什么不用官方 API：Oxford Dictionaries API 要 app_id/app_key 注册申请，且只给英文；
 * 而学习词典网页是公开可读的，能直接拿到词性、音标和编号释义，覆盖面比本地 Oxford5000
 * 词表大得多（词表只有 5836 条，官网几乎覆盖所有常用词）。
 *
 * 定位是「用户主动点击才取」的补充资料，不做自动请求——单次响应约 100KB，
 * 自动拉会让每次查词都变慢，也没必要。
 */
final class OxfordWeb {

    static final class Def {
        public String pos = "";
        public String ipa = "";
        public List<String> defs = new ArrayList<>();
        public boolean notFound = false;
        public String error = "";

        public boolean ok() { return error.isEmpty() && !defs.isEmpty(); }
    }

    private static final Pattern P_TITLE = Pattern.compile("<title>([^<]{0,120})");
    private static final Pattern P_PHON = Pattern.compile("class=\"phon\"[^>]*>([^<]{0,40})");
    private static final Pattern P_POS = Pattern.compile("class=\"pos\"[^>]*>([^<]{0,24})");
    // 释义里常嵌 <a>/<span> 链接，不能遇到第一个 '<' 就停，否则会截成半句
    private static final Pattern P_DEF = Pattern.compile(
            "<span class=\"def\"[^>]*>(.{0,420}?)(?:</span>|<span class=\"def\"]|[0-9]{1,2}[.)]<)",
            Pattern.DOTALL);

    private OxfordWeb() {}

    /** 阻塞方法，必须在后台线程调用。 */
    static Def fetch(String word) {
        Def d = new Def();
        String w = word == null ? "" : word.trim().toLowerCase();
        if (w.isEmpty()) {
            d.error = "单词为空";
            return d;
        }
        // 多词短语/带空格的不查，官网路径规则不同
        if (!w.matches("[a-z][a-z'\\-]{0,40}")) {
            d.error = "这个词不是单词形态，牛津官网不按单词页收录";
            return d;
        }

        String url = "https://www.oxfordlearnersdictionaries.com/definition/english/"
                + Http.enc(w);
        String html;
        try {
            html = Http.getWithHeaders(url, 20000,
                    "Accept-Language", "en-US,en;q=0.9");
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("404")) {
                d.notFound = true;
                return d;
            }
            d.error = "取不到牛津页面：" + brief(msg);
            return d;
        }
        if (html == null || html.isEmpty()) {
            d.error = "牛津返回空页面";
            return d;
        }

        Matcher t = P_TITLE.matcher(html);
        String title = t.find() ? t.group(1) : "";
        if (title.toLowerCase().contains("no exact matches")
                || title.toLowerCase().contains("not found")) {
            d.notFound = true;
            return d;
        }

        Matcher ph = P_PHON.matcher(html);
        if (ph.find()) d.ipa = decode(ph.group(1)).trim();

        Matcher po = P_POS.matcher(html);
        if (po.find()) d.pos = decode(po.group(1)).trim();

        Matcher dm = P_DEF.matcher(html);
        while (dm.find() && d.defs.size() < 4) {
            String s = decode(dm.group(1)).trim();
            if (!s.isEmpty() && !d.defs.contains(s)) d.defs.add(s);
        }

        if (d.defs.isEmpty()) d.error = "页面里没解析出释义（结构可能变了）";
        return d;
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ").replaceAll("<[^>]+>", "").trim();
    }

    private static String brief(String s) {
        if (s == null) return "未知错误";
        s = s.replace('\n', ' ').trim();
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
