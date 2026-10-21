package com.screenpick.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简网络图片加载：内存 LRU + 固定线程池 + 按 tag 防止列表复用错位。
 * 引入 Glide/Picasso 要多 2MB 依赖，这里只需要加载一张配图，不值当。
 */
final class ImageLoader {

    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount(); }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    interface Callback {
        void onResult(boolean ok);
    }

    static void load(final String url, final ImageView iv, final Callback cb) {
        if (url == null || url.isEmpty() || iv == null) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            if (cb != null) cb.onResult(true);
            return;
        }
        iv.setTag(url);
        POOL.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bm = fetch(url);
                if (bm != null) CACHE.put(url, bm);
                iv.post(new Runnable() {
                    @Override public void run() {
                        if (!url.equals(iv.getTag())) return; // 已被别的请求抢占
                        if (bm != null) iv.setImageBitmap(bm);
                        if (cb != null) cb.onResult(bm != null);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String urlStr) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(25000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ScreenPick/1.0");
            if (c.getResponseCode() >= 400) return null;
            in = c.getInputStream();
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    /** 用 AI 生图提示词构造一个免费图片地址（Pollinations，无需 key）。 */
    static String sceneUrl(String prompt, String seed) {
        if (prompt == null) prompt = "";
        String p = prompt.trim();
        if (p.isEmpty()) return "";
        int s = Math.abs(seed == null ? 0 : seed.hashCode()) % 100000;
        return "https://image.pollinations.ai/prompt/" + Http.enc(p)
                + "?width=512&height=384&nologo=true&seed=" + s;
    }
}
