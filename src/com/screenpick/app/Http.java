package com.screenpick.app;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 极简 HTTP。只做本项目需要的 GET / JSON POST。 */
final class Http {

    private Http() {}

    static String get(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ScreenPick/1.0");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (code >= 400) throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + trim(body, 200)));
            return body;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 带自定义请求头的 GET（牛津网页需要 Accept-Language 才返回英文版）。 */
    static String getWithHeaders(String urlStr, int timeoutMs, String... headers) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            for (int i = 0; i + 1 < headers.length; i += 2) {
                c.setRequestProperty(headers[i], headers[i + 1]);
            }
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (code >= 400) throw new IOException("HTTP " + code);
            return body;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 下载二进制（发音音频用）。失败抛异常，由调用方决定降级。 */
    static byte[] getBytes(String urlStr, int timeoutMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ScreenPick/1.0");
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String postJson(String urlStr, String json, String bearer, int timeoutMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            if (bearer != null && !bearer.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(payload);
                os.flush();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (code >= 400) throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + trim(body, 220)));
            return body;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String trim(String s, int max) {
        s = s.replace('\n', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 供各引擎用的 URL 编码（application/x-www-form-urlencoded）。 */
    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 读整段文本（备用）。 */
    static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] b = new char[4096];
            int n;
            while ((n = r.read(b)) > 0) sb.append(b, 0, n);
        }
        return sb.toString();
    }
}
