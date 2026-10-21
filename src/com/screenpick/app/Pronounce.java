package com.screenpick.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * 单词发音。
 *
 * 优先用有道词典的真人发音音频（type=2 美音 / type=1 英音）——比系统 TTS 好太多；
 * 网络不通或该词没有音频时，才回退到本地 TTS，保证"点了一定有声音"。
 */
public final class Pronounce {

    private static final String TAG = "ScreenPick/TTS";

    private static MediaPlayer player;
    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static String pending;

    private Pronounce() {}

    public static void us(Context c, String word) { play(c, word, 2); }

    public static void uk(Context c, String word) { play(c, word, 1); }

    private static void play(Context c, final String word, final int type) {
        if (word == null || word.trim().isEmpty()) return;
        final Context app = c.getApplicationContext();
        stopPlayer();

        String url = "https://dict.youdao.com/dictvoice?type=" + type + "&audio=" + Http.enc(word.trim());
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(url);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) { m.start(); }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { releasePlayer(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.w(TAG, "音频源失败(" + what + "/" + extra + ")，回退本地 TTS");
                    releasePlayer();
                    speakFallback(app, word);
                    return true;
                }
            });
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            speakFallback(app, word);
        }
    }

    // ---------- 兜底 TTS ----------

    private static void ensureTts(final Context app) {
        if (tts != null) return;
        tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(0.92f);
                    ttsReady = true;
                    if (pending != null) {
                        String w = pending;
                        pending = null;
                        tts.speak(w, TextToSpeech.QUEUE_FLUSH, null, "sp");
                    }
                } else {
                    Log.w(TAG, "TTS 初始化失败: " + status);
                }
            }
        });
    }

    private static void speakFallback(Context app, String word) {
        ensureTts(app);
        if (ttsReady && tts != null) {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "sp");
        } else {
            pending = word;
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
