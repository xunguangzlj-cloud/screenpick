package com.screenpick.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 单词本。和 assets 里那份只读词库是两回事：
 * 词库是"全世界通用的词典"，单词本是"我收藏过的词"，
 * 所以这里会快照一份释义——以后词库升级也不会让收藏记录变成空壳。
 */
public class BookDb extends SQLiteOpenHelper {

    private static final String NAME = "wordbook.db";
    private static final int VERSION = 1;

    public static final class Item {
        public long id;
        public String word = "";
        public String phonetic = "";
        public String trans = "";
        public String tag = "";
        public String day = "";
        public long addedAt;
        public boolean mastered;
        public int reviews;

        public boolean isCet4() { return tag != null && tag.contains("cet4"); }
        public boolean isCet6() { return tag != null && tag.contains("cet6"); }
    }

    private static volatile BookDb INSTANCE;

    public static BookDb get(Context c) {
        if (INSTANCE == null) {
            synchronized (BookDb.class) {
                if (INSTANCE == null) INSTANCE = new BookDb(c.getApplicationContext());
            }
        }
        return INSTANCE;
    }

    private BookDb(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE word(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "word TEXT UNIQUE NOT NULL," +
                "phonetic TEXT," +
                "trans TEXT," +
                "tag TEXT," +
                "day TEXT," +
                "added_at INTEGER," +
                "mastered INTEGER DEFAULT 0," +
                "reviews INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_day ON word(day)");
        db.execSQL("CREATE INDEX idx_mastered ON word(mastered)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 目前只有 v1；将来加字段在这里做迁移
    }

    // ---------- 写 ----------

    /** @return true = 新收藏成功，false = 这个词已经在单词本里了 */
    public boolean save(String word, String phonetic, String trans, String tag) {
        if (word == null || word.trim().isEmpty()) return false;
        String w = word.trim();
        if (isSaved(w)) return false;

        ContentValues v = new ContentValues();
        v.put("word", w);
        v.put("phonetic", phonetic == null ? "" : phonetic);
        v.put("trans", trans == null ? "" : trans);
        v.put("tag", tag == null ? "" : tag);
        v.put("day", today());
        v.put("added_at", System.currentTimeMillis());
        v.put("mastered", 0);
        v.put("reviews", 0);
        return getWritableDatabase().insert("word", null, v) > 0;
    }

    public void remove(String word) {
        getWritableDatabase().delete("word", "word=?", new String[]{word});
    }

    public void setMastered(String word, boolean mastered) {
        ContentValues v = new ContentValues();
        v.put("mastered", mastered ? 1 : 0);
        getWritableDatabase().update("word", v, "word=?", new String[]{word});
    }

    public void bumpReview(String word) {
        getWritableDatabase().execSQL(
                "UPDATE word SET reviews = reviews + 1 WHERE word = ?", new Object[]{word});
    }

    /** 把"斩"过的词放回来（单词本里可以撤销）。 */
    public void resetAllMastered() {
        ContentValues v = new ContentValues();
        v.put("mastered", 0);
        getWritableDatabase().update("word", v, "mastered=1", null);
    }

    // ---------- 读 ----------

    public boolean isSaved(String word) {
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery(
                    "SELECT 1 FROM word WHERE word=? LIMIT 1", new String[]{word});
            return cur.moveToFirst();
        } finally {
            if (cur != null) cur.close();
        }
    }

    public int count() {
        return scalar("SELECT COUNT(*) FROM word");
    }

    public int countMastered() {
        return scalar("SELECT COUNT(*) FROM word WHERE mastered=1");
    }

    public int countUnmastered() {
        return scalar("SELECT COUNT(*) FROM word WHERE mastered=0");
    }

    private int scalar(String sql) {
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery(sql, null);
            return cur.moveToFirst() ? cur.getInt(0) : 0;
        } finally {
            if (cur != null) cur.close();
        }
    }

    /** 全部收藏，新的在前。 */
    public List<Item> all() {
        return query("SELECT * FROM word ORDER BY added_at DESC");
    }

    /** 复习队列：还没被"斩"掉的词，先来后到。 */
    public List<Item> unmastered() {
        return query("SELECT * FROM word WHERE mastered=0 ORDER BY reviews ASC, added_at ASC");
    }

    /** 按天分组，天数从新到旧。 */
    public LinkedHashMap<String, List<Item>> byDay() {
        LinkedHashMap<String, List<Item>> map = new LinkedHashMap<>();
        for (Item it : all()) {
            List<Item> list = map.get(it.day);
            if (list == null) {
                list = new ArrayList<>();
                map.put(it.day, list);
            }
            list.add(it);
        }
        return map;
    }

    private List<Item> query(String sql) {
        List<Item> out = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery(sql, null);
            while (cur.moveToNext()) {
                Item it = new Item();
                it.id = cur.getLong(cur.getColumnIndexOrThrow("id"));
                it.word = str(cur, "word");
                it.phonetic = str(cur, "phonetic");
                it.trans = str(cur, "trans");
                it.tag = str(cur, "tag");
                it.day = str(cur, "day");
                it.addedAt = cur.getLong(cur.getColumnIndexOrThrow("added_at"));
                it.mastered = cur.getInt(cur.getColumnIndexOrThrow("mastered")) == 1;
                it.reviews = cur.getInt(cur.getColumnIndexOrThrow("reviews"));
                out.add(it);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndexOrThrow(col);
        String s = c.getString(i);
        return s == null ? "" : s;
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 把 "2026-10-21" 变成"今天 / 昨天 / 10月19日"。 */
    public static String dayLabel(String day) {
        String today = today();
        if (today.equals(day)) return "今天";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            Date d = f.parse(day);
            long diff = (System.currentTimeMillis() - d.getTime()) / 86400000L;
            if (diff == 1) return "昨天";
            if (diff == 2) return "前天";
            String[] p = day.split("-");
            if (p.length == 3) return Integer.parseInt(p[1]) + "月" + Integer.parseInt(p[2]) + "日";
        } catch (Exception ignored) {
        }
        return day;
    }
}
