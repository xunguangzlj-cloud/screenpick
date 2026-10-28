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
 *
 * 注意：本文件全部使用命名内部类，不用匿名类。
 * d8 8.2.2 处理这里的匿名类时会抛 NullPointerException
 * （Cannot invoke "String.length()" because null），整个 dex 打包中止，
 * 而构建脚本当时把这一步的失败吞掉了，导致一直在装旧包。
 */
final class ImageLoader {

    private ImageLoader() {}

    /**
     * 按条目数限制（12 张 512x384 的图约 9MB），不重写 sizeOf。
     * 原先是 LruCache 的匿名/命名子类，但 d8 8.2.2 解析这类子类会抛
     * NullPointerException 让整个打包中止——绕开的办法就是不建子类。
     */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    interface Callback {
        void onResult(boolean ok);
    }

    static void load(String url, ImageView iv, Callback cb) {
        if (url == null || url.isEmpty() || iv == null) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            if (cb != null) cb.onResult(true);
            return;
        }
        iv.setTag(url);
        POOL.execute(new LoadTask(url, iv, cb));
    }

    /** 后台下载。 */
    private static final class LoadTask implements Runnable {
        private final String url;
        private final ImageView iv;
        private final Callback cb;

        LoadTask(String url, ImageView iv, Callback cb) {
            this.url = url;
            this.iv = iv;
            this.cb = cb;
        }

        @Override public void run() {
            Bitmap bm = fetch(url);
            if (bm != null) CACHE.put(url, bm);
            iv.post(new ApplyTask(url, iv, cb, bm));
        }
    }

    /** 回到主线程贴图；期间若该 ImageView 已被别的请求占用就丢弃。 */
    private static final class ApplyTask implements Runnable {
        private final String url;
        private final ImageView iv;
        private final Callback cb;
        private final Bitmap bm;

        ApplyTask(String url, ImageView iv, Callback cb, Bitmap bm) {
            this.url = url;
            this.iv = iv;
            this.cb = cb;
            this.bm = bm;
        }

        @Override public void run() {
            if (!url.equals(iv.getTag())) return;
            if (bm != null) iv.setImageBitmap(bm);
            if (cb != null) cb.onResult(bm != null);
        }
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
