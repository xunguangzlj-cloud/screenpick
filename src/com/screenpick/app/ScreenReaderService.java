package com.screenpick.app;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.List;

/**
 * 无障碍服务：唯一的读屏通道。
 *
 * 它只做两件事——按请求把当前窗口的文本树交出去；在别的 App 里选中英文时主动通知悬浮层。
 * 不做任何后台遍历、不缓存内容。
 */
public class ScreenReaderService extends AccessibilityService {

    private static volatile ScreenReaderService instance;

    private String lastSelection = "";
    private long lastSelectionAt = 0;



    public static ScreenReaderService get() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return;
        if (!Prefs.selectLookup(this)) return;
        if (getPackageName().contentEquals(String.valueOf(event.getPackageName()))) return;

        AccessibilityNodeInfo src = null;
        try {
            src = event.getSource();
            if (src == null) return;
            CharSequence text = src.getText();
            if (text == null || text.length() == 0) return;
            int s = src.getTextSelectionStart();
            int e = src.getTextSelectionEnd();
            if (s < 0 || e <= s || e > text.length()) return;
            if (e - s > 40) return;
            String sel = text.subSequence(s, e).toString().trim();
            if (!sel.matches("[A-Za-z][A-Za-z'\u2019-]{0,30}")) return;

            long now = SystemClock.uptimeMillis();
            if (sel.equalsIgnoreCase(lastSelection) && now - lastSelectionAt < 2000) return;
            lastSelection = sel;
            lastSelectionAt = now;

            OverlayService os = OverlayService.get();
            if (os != null) os.requestWordCard(sel, null);
        } catch (Exception ignored) {
            // 选中事件在部分输入法里会异常，忽略即可
        } finally {
            if (src != null) {
                try { src.recycle(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onInterrupt() {
        // 无需处理
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /** 按阅读顺序取出当前屏幕的段落。 */
    public List<String> grabScreen() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return Collections.emptyList();
            return TextGrabber.grab(root);
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            if (root != null) {
                try { root.recycle(); } catch (Exception ignored) {}
            }
        }
    }
}
