package com.screenpick.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 英美音标。
 *
 * 本地词库（ECDICT）只有一套 phonetic，看不出英美差异；而 tomato / schedule 这类词的
 * 差别恰恰就在音标上。所以这里向有道词典补一份 usphone / ukphone。
 *
 * 这是"增强"而不是"依赖"：拿不到就沿用本地那一套，查词主流程不受影响。
 */
final class Phonetics {

    static final class Pair {
        final String us;
        final String uk;
        Pair(String us, String uk) { this.us = us; this.uk = uk; }
        boolean same() { return us.equalsIgnoreCase(uk); }
        boolean empty() { return us.isEmpty() && uk.isEmpty(); }
    }

    private Phonetics() {}

    /** 阻塞调用，必须在后台线程执行。失败抛异常，由调用方降级。 */
    static Pair fetch(String word) throws Exception {
        String url = "https://dict.youdao.com/jsonapi?q=" + Http.enc(word);
        String body = Http.get(url, 9000);
        JSONObject root = new JSONObject(body);

        JSONObject ec = root.optJSONObject("ec");
        if (ec == null) return new Pair("", "");
        JSONArray words = ec.optJSONArray("word");
        if (words == null || words.length() == 0) return new Pair("", "");

        JSONObject w = words.optJSONObject(0);
        if (w == null) return new Pair("", "");
        String us = w.optString("usphone", "").trim();
        String uk = w.optString("ukphone", "").trim();
        return new Pair(us, uk);
    }
}
