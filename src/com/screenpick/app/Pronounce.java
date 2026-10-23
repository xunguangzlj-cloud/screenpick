package com.screenpick.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * 单词发音。
 *
 * 三级降级，每一级失败都要让用户看见，不能静默：
 *   1. 有道真人音频 —— 先下载到 cache 再播本地文件。
 *      之前是拿 URL 直接喂给 MediaPlayer 流式播放，在部分 ROM 上 prepare 失败且不留痕迹；
 *      改成落盘后，失败能捕获、第二次点击也更快。
 *   2. 系统 TTS —— 媒体音源取不到时的兜底
 *   3. Toast —— TTS 引擎都没装的话，明确告诉用户为什么没声音
 */
public final class Pronounce {

    private static final String TAG = "ScreenPick/TTS";

    private static MediaPlayer player;
    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static String pending;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Pronounce() {}

    public static void us(Context c, String word) { play(c, word, 2); }

    public static void uk(Context c, String word) { play(c, word, 1); }

    private static void play(final Context c, final String word, final int type) {
        if (word == null || word.trim().isEmpty()) return;
        final Context app = c.getApplicationContext();
        stopPlayer();
        final String w = word.trim();

        // 媒体音量为 0 时播放是"成功"的，只是听不见——不提示的话用户只会以为功能坏了
        if (!hasVolume(app)) {
            toast(app, "媒体音量为 0，先把音量调上来才能听到发音");
        }

        // 网络下载不能放主线程；面板和弹窗都可能从 UI 线程调用
        new Thread(new Runnable() {
            @Override public void run() {
                File audio = null;
                String why = null;
                try {
                    audio = cacheAudio(app, w, type);
                } catch (Exception e) {
                    why = e.getMessage();
                    Log.w(TAG, "取音频失败(" + w + "): " + why);
                }
                final File f = audio;
                final String reason = why;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (f != null && f.exists() && f.length() > 800) {
                            playFile(app, f, w);
                        } else {
                            Log.w(TAG, "降级到系统 TTS，原因: " + reason);
                            speakFallback(app, w, reason);
                        }
                    }
                });
            }
        }).start();
    }

    /** 下载到 cache；命中缓存直接返回。 */
    private static File cacheAudio(Context c, String word, int type) throws Exception {
        String key = "pron_" + type + "_" + Integer.toHexString((word + type).hashCode()) + ".mp3";
        File f = new File(c.getCacheDir(), key);
        if (f.exists() && f.length() > 800) return f;

        String url = "https://dict.youdao.com/dictvoice?type=" + type + "&audio=" + Http.enc(word);
        byte[] data = Http.getBytes(url, 15000);
        if (data == null || data.length < 800) {
            throw new IllegalStateException("音频太短(" + (data == null ? 0 : data.length) + "B)");
        }
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(data);
        } finally {
            try { out.close(); } catch (Exception ignored) {}
        }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) throw new IllegalStateException("缓存写入失败");
        Log.d(TAG, "音频已缓存 " + f.length() + "B → " + f.getName());
        return f;
    }

    private static void playFile(final Context c, File f, final String word) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) {
                    Log.d(TAG, "开始播放 " + word);
                    m.start();
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { releasePlayer(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.e(TAG, "本地播放失败 " + what + "/" + extra);
                    releasePlayer();
                    speakFallback(c, word, "MediaPlayer " + what + "/" + extra);
                    return true;
                }
            });
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "播放异常", e);
            releasePlayer();
            speakFallback(c, word, e.getMessage());
        }
    }

    // ---------- 兜底 TTS ----------

    private static void ensureTts(final Context app) {
        if (tts != null) return;
        tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int r = tts.setLanguage(Locale.US);
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                    tts.setSpeechRate(0.92f);
                    Log.d(TAG, "TTS 就绪, lang=" + r);
                    if (pending != null) {
                        String w = pending;
                        pending = null;
                        tts.speak(w, TextToSpeech.QUEUE_FLUSH, null, "sp");
                    }
                } else {
                    Log.e(TAG, "TTS 初始化失败 status=" + status);
                }
            }
        });
    }

    private static void speakFallback(Context app, String word, String reason) {
        ensureTts(app);
        if (ttsReady && tts != null) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "sp");
            return;
        }
        pending = word;
        // 3 秒还没就绪，基本可以判定这台设备没有可用的语音引擎
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                if (!ttsReady) {
                    toast(app, "没有声音：真人音频取不到，系统语音引擎也不可用"
                            + (reason == null ? "" : "（" + brief(reason) + "）"));
                }
            }
        }, 3000);
    }

    private static boolean hasVolume(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            return am == null || am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static String brief(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static void toast(Context c, String msg) {
        try {
            Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }

    // ---------- 生命周期 ----------

    private static void stopPlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
            }
            releasePlayer();
        }
    }

    private static void releasePlayer() {
        if (player == null) return;
        try { player.release(); } catch (Exception ignored) {}
        player = null;
    }

    /** 在 Service / Activity 销毁时调用，释放音频与 TTS 引擎。 */
    public static void release() {
        stopPlayer();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
            ttsReady = false;
        }
        pending = null;
    }
}
