package com.screenpick.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 全部配置项集中在这里，避免散落的 getSharedPreferences。 */
public final class Prefs {

    public static final String ENGINE_LLM = "llm";
    public static final String ENGINE_FREE = "free";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("screenpick", Context.MODE_PRIVATE);
    }

    public static String engine(Context c) {
        return sp(c).getString("engine", ENGINE_LLM);
    }

    public static void setEngine(Context c, String v) {
        sp(c).edit().putString("engine", v).apply();
    }

    public static String apiBase(Context c) {
        return sp(c).getString("apiBase", "https://api.deepseek.com/v1");
    }

    public static void setApiBase(Context c, String v) {
        sp(c).edit().putString("apiBase", v.trim()).apply();
    }

    public static String apiKey(Context c) {
        return sp(c).getString("apiKey", "");
    }

    public static void setApiKey(Context c, String v) {
        sp(c).edit().putString("apiKey", v.trim()).apply();
    }

    public static String model(Context c) {
        return sp(c).getString("model", "deepseek-chat");
    }

    public static void setModel(Context c, String v) {
        sp(c).edit().putString("model", v.trim().isEmpty() ? "deepseek-chat" : v.trim()).apply();
    }

    /** 在任意 App 里选中英文单词时，直接弹词条。 */
    /**
     * 「选中即查词」开关的进程内缓存。
     * onAccessibilityEvent 是高频回调，每次都读 SharedPreferences 会成为负担；
     * 缓存放在 Prefs 内部而不是让 Prefs 去引用 Service，避免两个类互相依赖
     * （那种循环引用会让 d8 在打包阶段抛 NullPointerException）。
     */
    private static Boolean sLookup;

    public static boolean selectLookup(Context c) {
        Boolean v = sLookup;
        if (v == null) {
            v = sp(c).getBoolean("selectLookup", true);
            sLookup = v;
        }
        return v;
    }

    public static void setSelectLookup(Context c, boolean v) {
        sp(c).edit().putBoolean("selectLookup", v).apply();
        sLookup = v;
    }

    /** 点词时是否自动写入剪贴板。 */
    public static boolean copyOnTap(Context c) {
        return sp(c).getBoolean("copyOnTap", true);
    }

    public static void setCopyOnTap(Context c, boolean v) {
        sp(c).edit().putBoolean("copyOnTap", v).apply();
    }

    public static int fontSize(Context c) {
        return sp(c).getInt("fontSize", 16);
    }

    public static void setFontSize(Context c, int v) {
        sp(c).edit().putInt("fontSize", v).apply();
    }

    /** 首次引导是否已经做过——只在第一次装完后把用户送去无障碍开关页。 */
    public static boolean guided(Context c) {
        return sp(c).getBoolean("guided", false);
    }

    public static void setGuided(Context c, boolean v) {
        sp(c).edit().putBoolean("guided", v).apply();
    }

    public static boolean bubbleVisible(Context c) {
        return sp(c).getBoolean("bubbleVisible", true);
    }

    public static void setBubbleVisible(Context c, boolean v) {
        sp(c).edit().putBoolean("bubbleVisible", v).apply();
    }
}
