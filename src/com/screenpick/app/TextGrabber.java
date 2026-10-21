package com.screenpick.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 把无障碍节点树还原成"人眼看到的段落"。
 *
 * 这一步是整个工具体验的地基：拿到的不是 OCR 结果，而是应用自己声明的文本，
 * 所以没有识别错误、没有延迟，代价是只对暴露无障碍文本的应用有效。
 */
final class TextGrabber {

    private static final class Piece {
        final String text;
        final Rect box;
        Piece(String t, Rect b) { text = t; box = b; }
    }

    private static final class Line {
        String text;
        int top;
        int bottom;
        Line(String t, int top, int bottom) { this.text = t; this.top = top; this.bottom = bottom; }
        int height() { return Math.max(1, bottom - top); }
    }

    private TextGrabber() {}

    static List<String> grab(AccessibilityNodeInfo root) {
        if (root == null) return new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        collect(root, pieces, 0);
        if (pieces.isEmpty()) return new ArrayList<>();

        // 按阅读顺序排：先纵后横
        Collections.sort(pieces, new Comparator<Piece>() {
            @Override public int compare(Piece a, Piece b) {
                int d = a.box.top - b.box.top;
                if (Math.abs(d) > 3) return d;
                return a.box.left - b.box.left;
            }
        });

        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            StringBuilder sb = new StringBuilder(p.text);
            int top = p.box.top, bottom = p.box.bottom, left = p.box.left, right = p.box.right;
            int j = i + 1;
            while (j < pieces.size()) {
                Piece q = pieces.get(j);
                int minH = Math.min(Math.max(1, p.box.height()), Math.max(1, q.box.height()));
                if (Math.abs(q.box.top - top) >= minH * 0.65) break;
                sb.append(' ').append(q.text);
                top = Math.min(top, q.box.top);
                bottom = Math.max(bottom, q.box.bottom);
                left = Math.min(left, q.box.left);
                right = Math.max(right, q.box.right);
                j++;
            }
            String s = sb.toString().replaceAll("\\s+", " ").trim();
            if (!s.isEmpty()) lines.add(new Line(s, top, bottom));
            i = j - 1;
        }

        // 行 -> 段：行距明显大于行高就断开
        List<String> paras = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int prevBottom = Integer.MIN_VALUE;
        int prevHeight = 0;
        for (Line ln : lines) {
            boolean breakHere;
            if (cur.length() == 0) {
                breakHere = false;
            } else {
                int gap = ln.top - prevBottom;
                int ref = Math.max(prevHeight, ln.height());
                breakHere = gap > ref * 0.95
                        || (endsSentence(cur) && gap > ref * 0.45)
                        || cur.length() > 320;
            }
            if (breakHere) {
                paras.add(cur.toString().trim());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                // 断行处如果上一行以连字符结尾，把它接回去
                if (cur.charAt(cur.length() - 1) == '-') {
                    cur.setLength(cur.length() - 1);
                } else {
                    cur.append(' ');
                }
            }
            cur.append(ln.text);
            prevBottom = ln.bottom;
            prevHeight = ln.height();
        }
        if (cur.length() > 0) paras.add(cur.toString().trim());

        // 滤掉控件文字（"OK" / "Cancel" / 标签栏）
        List<String> out = new ArrayList<>();
        for (String p : paras) {
            String t = p.trim();
            if (looksLikeProse(t)) out.add(t);
        }
        return out;
    }

    private static boolean endsSentence(CharSequence s) {
        if (s.length() == 0) return false;
        char c = s.charAt(s.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '"' || c == '\u201d';
    }

    /** 至少 2 个英文词，或足够长的整句——用来滤掉按钮和标签文字。 */
    private static boolean looksLikeProse(String s) {
        if (s.length() < 6) return false;
        int words = 0, letters = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean letter = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
            if (letter) {
                letters++;
                if (!inWord) { words++; inWord = true; }
            } else {
                inWord = false;
            }
        }
        if (letters < 6) return false;
        return words >= 2 || s.length() >= 18;
    }

    private static void collect(AccessibilityNodeInfo node, List<Piece> out, int depth) {
        if (node == null || depth > 60) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        String merged = null;
        if (text != null && text.length() > 0) merged = text.toString();
        else if (desc != null && desc.length() > 0) merged = desc.toString();

        if (merged != null) {
            merged = merged.replace('\u00a0', ' ').trim();
            if (merged.length() >= 2 && hasLetter(merged)) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0 && node.isVisibleToUser()) {
                    out.add(new Piece(merged, r));
                }
            }
            // 有文字的节点不再向下钻，避免同一句话被父子节点重复采集
            return;
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                collect(child, out, depth + 1);
            } catch (Exception ignored) {
                // 遍历途中节点失效是常态，单点失败不影响整体
            } finally {
                if (child != null) {
                    try { child.recycle(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }
}
