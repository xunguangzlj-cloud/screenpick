package com.screenpick.app;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 词形还原回归测试（纯 JVM，不依赖 Android）。
 *
 * 跑法：
 *   javac -d <out> test/com/screenpick/app/MorphTest.java src/com/screenpick/app/Morphology.java
 *   java -cp <out> com.screenpick.app.MorphTest words.txt
 *
 * words.txt 每行一个词库里的词（小写）。测试思路：给一个变形词，
 * Morphology.candidates() 必须能把原形还原出来（原形在词库里即算命中）。
 */
public class MorphTest {

    private static final String[] CASES = {
            // 现在分词
            "running", "writing", "making", "stopping", "studying", "lying", "beginning",
            "getting", "planning", "sitting", "using", "having", "coming", "taking",
            "reading", "working", "trying", "carrying", "dying", "seeing",
            // 过去式 / 过去分词
            "studied", "carried", "played", "tried", "stopped", "planned", "needed",
            "created", "used", "moved", "hoped", "admitted", "occurred", "preferred",
            "developed", "increased", "considered", "established", "produced",
            // 复数 / 三单
            "studies", "cities", "boxes", "watches", "wishes", "babies", "knives",
            "leaves", "lives", "wives", "halves", "shelves", "methods", "results",
            "examples", "problems", "systems", "processes", "analyses", "policies",
            "companies", "opportunities", "abilities", "activities", "communities",
            // 比较级 / 最高级
            "easier", "happier", "bigger", "larger", "better", "greater", "higher",
            "strongest", "longest", "highest", "biggest", "easiest", "happiest",
            // 副词
            "quickly", "slowly", "carefully", "easily", "happily", "basically",
            "specifically", "automatically", "effectively", "generally",
    };

    public static void main(String[] args) throws Exception {
        String dictPath = args.length > 0 ? args[0] : "words.txt";
        Set<String> words = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new FileReader(dictPath))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) words.add(line);
            }
        }
        System.out.println("词库规模: " + words.size() + " 词");
        System.out.println("测试用例: " + CASES.length + " 个");

        List<String> miss = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (String w : CASES) {
            List<String> cands = Morphology.candidates(w);
            String hit = null;
            for (String c : cands) {
                if (words.contains(c)) { hit = c; break; }
            }
            if (hit == null) {
                miss.add(w);
            } else if (!hit.equals(w)) {
                wrong.add(w + " -> " + hit);
            }
        }

        int ok = CASES.length - miss.size();
        System.out.printf("命中: %d/%d = %.1f%%%n", ok, CASES.length, ok * 100.0 / CASES.length);
        System.out.println("还原示例: " + wrong.subList(0, Math.min(12, wrong.size())));
        System.out.println("未命中: " + miss);
    }
}
