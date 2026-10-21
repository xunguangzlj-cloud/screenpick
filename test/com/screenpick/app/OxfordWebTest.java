package com.screenpick.app;

/**
 * 牛津官网抓取的离线测试（纯 JVM，不需要 Android 设备）。
 *
 * 跑法：
 *   javac -encoding UTF-8 -d <out> src/com/screenpick/app/Http.java \
 *         src/com/screenpick/app/OxfordWeb.java test/com/screenpick/app/OxfordWebTest.java
 *   java -cp <out> com.screenpick.app.OxfordWebTest evidence abandon stack zzzzz
 *
 * 最后一个刻意用不存在的词，验证 notFound 分支不会误报"解析失败"。
 */
public class OxfordWebTest {

    public static void main(String[] args) {
        String[] words = args.length > 0
                ? args
                : new String[]{"evidence", "abandon", "stack", "context", "zzzzznotaword"};

        int ok = 0, notFound = 0, fail = 0;
        for (String w : words) {
            OxfordWeb.Def d = OxfordWeb.fetch(w);
            System.out.println("=== " + w + " ===");
            if (d.ok()) {
                ok++;
                System.out.println("  词性=" + d.pos + "  音标=" + d.ipa);
                for (int i = 0; i < d.defs.size(); i++) {
                    System.out.println("   " + (i + 1) + ". " + d.defs.get(i));
                }
            } else if (d.notFound) {
                notFound++;
                System.out.println("  未收录（notFound 分支，符合预期）");
            } else {
                fail++;
                System.out.println("  失败: " + d.error);
            }
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        }

        System.out.println();
        System.out.printf("结果：成功 %d / 未收录 %d / 失败 %d（共 %d）%n",
                ok, notFound, fail, words.length);
        if (fail > 0) System.exit(1);
    }
}
