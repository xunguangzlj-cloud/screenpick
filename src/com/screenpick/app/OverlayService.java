package com.screenpick.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 悬浮球 + 面板的全部宿主。
 *
 * 刻意不用 Activity 展示译文：从 Service 后台启动 Activity 在 Android 10+ 会被系统拦下，
 * 而 SYSTEM_ALERT_WINDOW 覆盖层不受这条限制，且体验上更接近"工具"而不是"应用"。
 */
public class OverlayService extends Service implements PanelView.Host {

    public static final String ACTION_START = "com.screenpick.action.START";
    public static final String ACTION_STOP = "com.screenpick.action.STOP";

    private static final String CHANNEL_ID = "screenpick_bubble";
    private static final int NOTIFY_ID = 0x5C01;

    private static volatile OverlayService instance;

    public static OverlayService get() {
        return instance;
    }

    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams bubbleLp;
    private PanelView panel;
    private boolean panelAttached = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        startForegroundInternal();
        initTts();
        addBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null) addBubble();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        removePanel();
        removeBubble();
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        io.shutdownNow();
        Pronounce.release();
        super.onDestroy();
    }

    // ================= 前台通知 =================

    private void startForegroundInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "拾词悬浮球", NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                ch.setDescription("保持悬浮球与读屏服务运行");
                nm.createNotificationChannel(ch);
            }
        }
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            n = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("拾词正在运行")
                    .setContentText("点击悬浮球翻译屏幕上的英文")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
        } else {
            n = new Notification.Builder(this)
                    .setContentTitle("拾词正在运行")
                    .setContentIntent(pi)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .build();
        }
        startForeground(NOTIFY_ID, n);
    }

    // ================= 悬浮球 =================

    private void addBubble() {
        if (bubble != null) return;

        FrameLayout holder = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{0xFF2F80ED, 0xFF1F6FEB});
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(Ui.dp(this, 1.5f), 0x33FFFFFF);
        holder.setBackground(bg);
        holder.setAlpha(0.94f);

        TextView label = new TextView(this);
        label.setText("A");
        label.setTextColor(Color.WHITE);
        label.setTextSize(19f);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        holder.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int size = Ui.dp(this, 52);
        bubbleLp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 72);
        bubbleLp.y = Math.round(getResources().getDisplayMetrics().heightPixels * 0.34f);

        holder.setOnTouchListener(new BubbleTouch());

        bubble = holder;
        try {
            wm.addView(bubble, bubbleLp);
        } catch (Exception e) {
            bubble = null;
            toast("悬浮窗权限未开启");
            stopSelf();
        }
    }

    private void removeBubble() {
        if (bubble == null) return;
        try { wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = null;
    }

    private void showBubble(boolean show) {
        if (bubble == null) {
            if (show) addBubble();
            return;
        }
        bubble.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** 拖动与点击的区分：位移超过阈值算拖动，否则按时长区分单击 / 长按。 */
    private final class BubbleTouch implements View.OnTouchListener {
        private float startX, startY;
        private int originX, originY;
        private boolean dragging;
        private long downAt;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX();
                    startY = e.getRawY();
                    originX = bubbleLp.x;
                    originY = bubbleLp.y;
                    dragging = false;
                    downAt = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - startX;
                    float dy = e.getRawY() - startY;
                    if (!dragging && Math.hypot(dx, dy) > Ui.dp(OverlayService.this, 6)) dragging = true;
                    if (dragging) {
                        int w = getResources().getDisplayMetrics().widthPixels;
                        int h = getResources().getDisplayMetrics().heightPixels;
                        int size = bubbleLp.width;
                        bubbleLp.x = Math.max(0, Math.min(w - size, originX + (int) dx));
                        bubbleLp.y = Math.max(0, Math.min(h - size, originY + (int) dy));
                        try { wm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) {}
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!dragging) {
                        long held = System.currentTimeMillis() - downAt;
                        if (held > 550) openMain();
                        else translateScreen();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    // ================= 面板生命周期 =================

    private void showPanel() {
        if (panel == null) panel = new PanelView(this, this);
        if (!panelAttached) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            try {
                wm.addView(panel.root, lp);
                panelAttached = true;
            } catch (Exception e) {
                toast("面板添加失败：" + e.getMessage());
                return;
            }
        }
        panel.root.setVisibility(View.VISIBLE);
        showBubble(false);
    }

    private void removePanel() {
        if (panel == null || !panelAttached) return;
        try { wm.removeView(panel.root); } catch (Exception ignored) {}
        panelAttached = false;
        showBubble(true);
    }

    private void hidePanel() {
        if (panel != null) panel.root.setVisibility(View.GONE);
        showBubble(true);
    }

    // ================= Host 回调 =================

    @Override
    public void onClose() {
        hidePanel();
    }

    @Override
    public void onCopy(String text) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("screenpick", text));
        toast("已复制：" + (text.length() > 24 ? text.substring(0, 24) + "…" : text));
    }

    @Override
    public void onSpeak(String text) {
        Pronounce.us(this, text);
    }

    @Override
    public void onRetry() {
        translateScreen();
    }

    @Override
    public void onWordTap(String word, String sentence) {
        requestWordCard(word, sentence);
    }

    /**
     * 补一份英美音标。纯粹是增强：本地那套已经先显示了，
     * 这里失败就静默放弃，不让网络问题影响查词。
     */
    private void fetchPhoneticsAsync(final String word) {
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Phonetics.Pair p = Phonetics.fetch(word);
                    if (p.empty()) return;
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (panel != null) panel.updatePhonetics(p, word);
                        }
                    });
                } catch (Exception ignored) {
                    // 拿不到就沿用本地音标
                }
            }
        });
    }

    @Override
    public void onOxfordWeb(final String word) {
        if (panel == null) return;
        panel.showOxfordLoading();
        io.execute(new Runnable() {
            @Override public void run() {
                final OxfordWeb.Def d = OxfordWeb.fetch(word);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (panel != null) panel.showOxford(d);
                    }
                });
            }
        });
    }

    @Override
    public void onAnalyzeWord(final String word, final String sentence) {
        if (Prefs.apiKey(this).isEmpty()) {
            toast("先在设置里填 API Key 才能用 AI 记忆");
            return;
        }
        if (panel == null) return;
        panel.showAnalysisLoading();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Translator.WordAnalysis a =
                            Translator.analyzeWord(OverlayService.this, word, sentence);
                    main.post(new Runnable() {
                        @Override public void run() { panel.showAnalysis(a); }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            panel.showAnalysisError("分析失败：" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onSaveWord(final String word) {
        io.execute(new Runnable() {
            @Override public void run() {
                Dict.Entry e = Dict.get(OverlayService.this).lookup(word);
                final boolean added = BookDb.get(OverlayService.this).save(
                        word,
                        e == null ? "" : e.phonetic,
                        e == null ? "" : e.trans,
                        e == null ? "" : e.tag);
                main.post(new Runnable() {
                    @Override public void run() {
                        toast(added ? "已收藏到单词本" : "这个词已经在单词本里了");
                        if (panel != null) panel.markSaved();
                    }
                });
            }
        });
    }

    @Override
    public boolean isSaved(String word) {
        try {
            return BookDb.get(this).isSaved(word);
        } catch (Exception e) {
            return false;
        }
    }

    // ================= 业务 =================

    /** 点悬浮球：抓当前屏幕 → 翻译 → 出面板。 */
    public void translateScreen() {
        ScreenReaderService sr = ScreenReaderService.get();
        if (sr == null) {
            toast("先在设置里开启「拾词 · 屏幕取词」无障碍服务");
            openMain();
            return;
        }
        List<String> paras = sr.grabScreen();
        if (paras.isEmpty()) {
            toast("这一屏没读到英文文本（图片/视频里的文字需要 OCR，暂不支持）");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : paras) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        final String source = sb.toString();
        if (source.length() < 8) {
            toast("读到的文本太短了");
            return;
        }

        showPanel();
        panel.showLoading(source);

        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Translator.Result r = Translator.translate(OverlayService.this, source);
                    final java.util.List<Dict.Entry> cet =
                            Dict.get(OverlayService.this).scanCet(source, 6);
                    main.post(new Runnable() {
                        @Override public void run() { panel.showResult(source, r, cet); }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            panel.showError("翻译失败：" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    /** 选中单词时由无障碍服务调用，也由面板里的点词调用。 */
    public void requestWordCard(final String word, final String sentence) {
        main.post(new Runnable() {
            @Override public void run() {
                showPanel();
                panel.showWordLoading(word);
                io.execute(new Runnable() {
                    @Override public void run() {
                        final Dict.Entry e = Dict.get(OverlayService.this).lookup(word);
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (e != null) {
                                    panel.showWord(word, sentence, e);
                                    fetchPhoneticsAsync(e.word);
                                } else {
                                    panel.showWordMissing(word, sentence, !Prefs.apiKey(OverlayService.this).isEmpty());
                                }
                                if (Prefs.copyOnTap(OverlayService.this)) {
                                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("screenpick", word));
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        tts.setLanguage(Locale.US);
                        tts.setSpeechRate(0.92f);
                    }
                }
            });
        } catch (Exception ignored) {
            tts = null;
        }
    }

    private void toast(String msg) {
        try { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
    }
}
