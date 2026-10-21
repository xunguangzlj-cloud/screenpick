package com.screenpick.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 词形还原：把 running / studies / carried 这类变形词还原成词典里的原形。
 *
 * 刻意保持纯 Java（不依赖 android.*），这样能脱离设备直接跑测试——
 * 它是"点了词却查不到"和"查错词"的最大来源，必须有回归覆盖。
 *
 * 候选顺序是按「还原正确的概率」排的，不是按规则书写的顺序：
 * 例如 using 必须还原成 use 而不是 us，所以「词干 + e」要排在「直接去 ing」前面。
 */
final class Morphology {

    private Morphology() {}

    /** 按命中概率从高到低给出候选原形（第一个永远是原词本身）。 */
    static List<String> candidates(String word) {
        List<String> out = new ArrayList<>();
        if (word == null) return out;
        String w = word.toLowerCase();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(w);
        int n = w.length();

        // ---- 复数 / 第三人称单数 ----
        if (n > 4 && w.endsWith("ies")) set.add(w.substring(0, n - 3) + "y");
        if (n > 4 && w.endsWith("ves")) {
            String b = w.substring(0, n - 3);
            set.add(b + "f");
            set.add(b + "fe");
        }
        if (n > 3 && w.endsWith("es")) {
            set.add(w.substring(0, n - 1));   // uses  -> use
            set.add(w.substring(0, n - 2));   // boxes -> box
        }
        if (n > 3 && w.endsWith("s")
                && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) {
            set.add(w.substring(0, n - 1));
        }

        // ---- 过去式 / 过去分词 ----
        if (n > 4 && w.endsWith("ied")) set.add(w.substring(0, n - 3) + "y");
        if (n > 3 && w.endsWith("ed")) {
            set.add(w.substring(0, n - 1));   // used  -> use
            set.add(w.substring(0, n - 2));   // walked-> walk
            String d = dedouble(w.substring(0, n - 2));
            if (d != null) set.add(d);        // stopped -> stop
        }

        // ---- 现在分词 ----
        if (n >= 5 && w.endsWith("ying")) set.add(w.substring(0, n - 4) + "ie"); // lying -> lie
        if (n > 4 && w.endsWith("ing")) {
            String base = w.substring(0, n - 3);
            set.add(base + "e");              // using -> use
            set.add(base);                    // walking -> walk
            String d = dedouble(base);
            if (d != null) set.add(d);        // stopping -> stop
        }

        // ---- 比较级 / 最高级 ----
        if (n > 4 && w.endsWith("ier")) set.add(w.substring(0, n - 3) + "y");
        if (n > 5 && w.endsWith("iest")) set.add(w.substring(0, n - 4) + "y");
        if (n > 4 && w.endsWith("est")) {
            String b = w.substring(0, n - 3);
            set.add(b);
            set.add(b + "e");                 // larger 的同族：largest -> large
            String d = dedouble(b);
            if (d != null) set.add(d);        // biggest -> big
        }
        if (n > 4 && w.endsWith("er")) {
            String b = w.substring(0, n - 2);
            set.add(b);
            set.add(b + "e");                 // larger -> large
            String d = dedouble(b);
            if (d != null) set.add(d);        // bigger -> big
        }

        // ---- 副词 ----
        if (n > 5 && w.endsWith("ally")) set.add(w.substring(0, n - 4)); // basically -> basic
        if (n > 4 && w.endsWith("ly")) set.add(w.substring(0, n - 2));

        out.addAll(set);
        return out;
    }

    /** 去掉词尾重复的辅音（stopping 的 "stopp" -> "stop"）；不是双写辅音就返回 null。 */
    private static String dedouble(String s) {
        int n = s.length();
        if (n < 3) return null;
        char last = s.charAt(n - 1);
        char prev = s.charAt(n - 2);
        if (last != prev) return null;
        if ("aeiou".indexOf(last) >= 0) return null;
        return s.substring(0, n - 1);
    }
}
