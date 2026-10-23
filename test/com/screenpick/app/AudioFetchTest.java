package com.screenpick.app;

/**
 * 发音音频下载链路的离线测试（纯 JVM，不需要设备）。
 *
 * 验证 App 里真正在用的 Http.getBytes + 有道音频接口能不能拿到完整 MP3。
 * 这一步排除后，如果手机上仍然没声音，问题就只可能在 MediaPlayer 或音量。
 *
 * 跑法：
 *   javac -encoding UTF-8 -d <out> src/com/screenpick/app/Http.java test/com/screenpick/app/AudioFetchTest.java
 *   java -cp <out> com.screenpick.app.AudioFetchTest
 */
public class AudioFetchTest {

    public static void main(String[] args) {
        String[] words = {"stack", "evidence", "schedule"};
        int ok = 0, fail = 0;

        for (String w : words) {
            for (int type : new int[]{2, 1}) {
                String label = w + (type == 2 ? " [美]" : " [英]");
                try {
                    String url = "https://dict.youdao.com/dictvoice?type=" + type
                            + "&audio=" + Http.enc(w);
                    long t0 = System.currentTimeMillis();
                    byte[] d = Http.getBytes(url, 15000);
                    long ms = System.currentTimeMillis() - t0;

                    if (d == null || d.length < 800) {
                        System.out.println("  ✗ " + label + " 音频过短: "
                                + (d == null ? "null" : d.length + "B"));
                        fail++;
                        continue;
                    }
                    // ID3 头是 "ID3"，裸 MP3 帧头是 0xFF 0xFx
                    boolean id3 = d[0] == 'I' && d[1] == 'D' && d[2] == '3';
                    boolean frame = (d[0] & 0xFF) == 0xFF && (d[1] & 0xE0) == 0xE0;
                    if (!id3 && !frame) {
                        System.out.println("  ✗ " + label + " 不是 MP3，头字节 "
                                + String.format("%02X %02X %02X", d[0], d[1], d[2]));
                        fail++;
                        continue;
                    }
                    System.out.printf("  ✓ %s  %5d B  %4d ms  %s%n",
                            label, d.length, ms, id3 ? "ID3" : "MPEG帧");
                    ok++;
                } catch (Exception e) {
                    System.out.println("  ✗ " + label + " 下载失败: " + e.getMessage());
                    fail++;
                }
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
        }

        System.out.println();
        System.out.printf("结果：成功 %d / 失败 %d%n", ok, fail);
        if (fail > 0) System.exit(1);
    }
}
