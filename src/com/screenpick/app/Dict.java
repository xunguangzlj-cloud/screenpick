package com.screenpick.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 内置词库。
 *
 * 四个数据源融合：
 *   · ECDICT（skywind3000）—— 骨架：11228 词、考纲标签、柯林斯星级、词形变化、WordNet 英英释义
 *   · Oxford5000（qwerty-learner 收录）—— 牛津高阶风格的中文释义，义项顺序最权威
 *   · CET4/CET6/Oxford3000 词表 —— 考试向的简短释义
 *   · 有道词典（在线）—— 英美音标，作为增强覆盖在上层
 *
 * 释义按"权威度"排序展示：牛津 > 考试向简释 > ECDICT 完整释义；
 * 英文释义与专业义项收进折叠区，不让它们占第一眼的位置。
 */
public final class Dict {

    public static final class Entry {
        public final String word;
        public final String phonetic;
        /** 牛津高阶风格释义（最权威，可能为空） */
        public final String oxford;
        /** 考试向简短释义（可能为空） */
        public final String brief;
        /** ECDICT 完整释义（已剔除专业义项） */
        public final String trans;
        /** 专业义项 [医][计][法] 之类，单独剥离 */
        public final String extra;
        /** WordNet 英英释义 */
        public final String ee;
        public final String tag;
        /** 词形变化 "过去式:abandoned|现在分词:abandoning"，空串表示没有 */
        public final String exchange;
        public final int star;
        public final int ox3000;
        public final int frq;
        /** 命中的是变形后的原形时，这里是原始点击的词；否则为 null */
        public final String lemmaOf;

        Entry(Cursor c, String lemmaOf) {
            this.word = s(c, 0);
            this.phonetic = s(c, 1);
            this.brief = s(c, 2);
            this.trans = s(c, 3);
            this.extra = s(c, 4);
            this.oxford = s(c, 5);
            this.ee = s(c, 6);
            this.tag = s(c, 7);
            this.star = c.getInt(8);
            this.ox3000 = c.getInt(9);
            this.frq = c.getInt(10);
            this.exchange = s(c, 11);
            this.lemmaOf = lemmaOf;
        }

        private static String s(Cursor c, int i) {
            String v = c.getString(i);
            return v == null ? "" : v;
        }

        public boolean isCet4() { return tag.contains("cet4"); }
        public boolean isCet6() { return tag.contains("cet6"); }
        public boolean isKy()   { return tag.contains("ky"); }

        /** 最权威的那套释义：牛津优先，其次考试向简释，最后 ECDICT。 */
        public String primaryMeaning() {
            if (!oxford.isEmpty()) return oxford;
            if (!brief.isEmpty()) return brief;
            return trans;
        }

        /** 有没有"更多释义"可展开（英文释义 / 专业义项 / 备选中文释义）。 */
        public boolean hasMore() {
            return !ee.isEmpty() || !extra.isEmpty()
                    || (!oxford.isEmpty() && !brief.isEmpty() && !brief.equals(oxford));
        }

        /** 展开区里的备选中文释义：牛津存在时才需要展示简释和 ECDICT 版。 */
        public String altMeaning() {
            if (oxford.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            if (!brief.isEmpty() && !brief.equals(oxford)) sb.append(brief);
            if (!trans.isEmpty() && !trans.equals(brief) && !trans.equals(oxford)) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(trans);
            }
            return sb.toString();
        }

        /** 把 exchange 拆成 [标签, 词] 的列表。 */
        public List<String[]> variants() {
            List<String[]> out = new ArrayList<>();
            if (exchange.isEmpty()) return out;
            for (String seg : exchange.split("\\|")) {
                int i = seg.indexOf(':');
                if (i > 0) out.add(new String[]{seg.substring(0, i), seg.substring(i + 1)});
            }
            return out;
        }
    }

    private static volatile Dict INSTANCE;
    private final SQLiteDatabase db;

    private Dict(SQLiteDatabase db) { this.db = db; }

    public static Dict get(Context c) {
        if (INSTANCE == null) {
            synchronized (Dict.class) {
                if (INSTANCE == null) INSTANCE = new Dict(open(c.getApplicationContext()));
            }
        }
        return INSTANCE;
    }

    /** 词库结构变化时递增，已装用户会在下次启动自动换成新库。 */
    private static final int ASSET_VERSION = 4;

    private static SQLiteDatabase open(Context app) {
        File f = new File(app.getFilesDir(), "dict.db");
        int installed = app.getSharedPreferences("screenpick", Context.MODE_PRIVATE)
                .getInt("dictAssetVersion", 0);
        if (!f.exists() || f.length() < 1024 || installed != ASSET_VERSION) {
            extract(app, f);
        }
        try {
            return SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
        } catch (Exception e) {
            // 上一次可能复制到一半就被系统杀了（启动超时），留下坏文件。
            // 删掉重来一次，别让一个半截文件把词库永久锁死。
            f.delete();
            extract(app, f);
            return SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
        }
    }

    /** 从 assets 释放词库，先写临时文件再原子改名，避免留下半截文件。 */
    private static void extract(Context app, File target) {
        File tmp = new File(app.getFilesDir(), "dict.db.tmp");
        try (InputStream in = app.getAssets().open("dict.db");
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        } catch (Exception e) {
            throw new RuntimeException("词库释放失败: " + e.getMessage(), e);
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            throw new RuntimeException("词库改名失败");
        }
        app.getSharedPreferences("screenpick", Context.MODE_PRIVATE)
                .edit().putInt("dictAssetVersion", ASSET_VERSION).apply();
    }

    /** 列顺序必须与 Entry(Cursor) 的索引一致。 */
    private static final String COLS =
            "word,phonetic,brief,trans,extra,oxford,ee,tag,star,ox,frq,exch";

    public Entry lookup(String raw) {
        if (raw == null) return null;
        String w = raw.trim().toLowerCase();
        if (w.isEmpty()) return null;
        for (String cand : Morphology.candidates(w)) {
            Entry e = query(cand, cand.equals(w) ? null : w);
            if (e != null) return e;
        }
        return null;
    }

    private Entry query(String lower, String lemmaOf) {
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT " + COLS + " FROM dict WHERE lower=? LIMIT 1",
                    new String[]{lower});
            if (!cur.moveToFirst()) return null;
            return new Entry(cur, lemmaOf);
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /**
     * 扫一遍原文，挑出里面出现的四六级考纲词，按词频升序。
     */
    public List<Entry> scanCet(String text, int limit) {
        List<Entry> found = new ArrayList<>();
        if (text == null || text.isEmpty()) return found;

        ArrayList<String> words = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Za-z][A-Za-z'\u2019-]{2,}")
                .matcher(text);
        while (m.find() && seen.size() < 300) {
            String w = m.group().toLowerCase();
            for (String c : Morphology.candidates(w)) {
                if (seen.add(c)) words.add(c);
            }
        }
        if (words.isEmpty()) return found;

        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM dict WHERE lower IN (");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(") AND (tag LIKE '%cet4%' OR tag LIKE '%cet6%') AND frq > 0 ORDER BY frq ASC LIMIT ")
           .append(Math.max(1, Math.min(limit * 4, 60)));

        Cursor cur = null;
        try {
            cur = db.rawQuery(sql.toString(), words.toArray(new String[0]));
            while (cur.moveToNext() && found.size() < limit) {
                found.add(new Entry(cur, null));
            }
        } catch (Exception ignored) {
            // 词库异常不应该阻断主流程
        } finally {
            if (cur != null) cur.close();
        }
        return found;
    }

    public int size() {
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT COUNT(*) FROM dict", null);
            return cur.moveToFirst() ? cur.getInt(0) : 0;
        } finally {
            if (cur != null) cur.close();
        }
    }
}
