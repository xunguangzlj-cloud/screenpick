package com.screenpick.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 翻译与 AI 素材层。三条路：
 *  1) LLM（OpenAI 兼容接口，默认 DeepSeek）—— 质量最好，适合四六级精读；
 *  2) 免费公开接口 —— 没配 Key 时的兜底，能跑通但质量一般；
 *  3) AI 记忆素材 —— 词根、例句、情景、配图提示词、故事。
 */
public final class Translator {

    /** 翻译结果里带着用的是哪个引擎，UI 上标注一下，方便判断质量。 */
    public static final class Result {
        public final String text;
        public final String engine;
        Result(String text, String engine) { this.text = text; this.engine = engine; }
    }

    /** AI 生成的记忆素材。字段缺失时留空字符串，UI 逐块降级显示。 */
    public static final class WordAnalysis {
        public String roots = "";
        public String memory = "";
        public String scene = "";
        public String homophone = "";
        public String imagePrompt = "";
        public List<String[]> examples = new ArrayList<>();
    }

    public static final class Story {
        public String title = "";
        public String en = "";
        public String zh = "";
    }

    private Translator() {}

    private static final String SYS_TRANSLATE =
            "你是英语精读助教，服务对象是备考大学英语四六级的学生。\n" +
            "把用户给的英文翻译成简体中文，规则：\n" +
            "1. 忠实原意，中文要通顺，按意群翻译，不要逐词硬译；\n" +
            "2. 严格保持原文的段落与句子切分，原文几段就输出几段，不要合并或拆分；\n" +
            "3. 人名、机构、专业术语首次出现保留英文原词，写成「中文（English）」；\n" +
            "4. 不要输出原文、不要加任何解释、标题、序号或前后缀，只输出译文本身。";

    private static final String SYS_WORD =
            "你是英语词汇记忆教练，服务对象是备考四六级的大学生。\n" +
            "根据用户给的单词（和它出现的句子），只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释文字。字段如下：\n" +
            "{\n" +
            "  \"roots\": \"词根词缀拆解，如 re-(再) + call(叫) → 再叫一次就想起来；没有明显词根就给词源或构词联想\",\n" +
            "  \"memory\": \"一句话记忆钩子，要具体、有画面感，不要空话\",\n" +
            "  \"scene\": \"30 字以内的真实生活情景，让这个词自然出现一次\",\n" +
            "  \"examples\": [{\"en\": \"英文例句\", \"cn\": \"中文翻译\"}],\n" +
            "  \"homophone\": \"中文谐音助记，形如「俺不能死 → ambulance 救护车」。只在真的能帮上忙时给；硬凑会误导发音，不适合就返回空字符串\",\n" +
            "  \"image_prompt\": \"英文图像描述，20 词以内，具象可画，画面里不要出现文字\"\n" +
            "}\n" +
            "examples 给 2 条，第一条贴近四六级真题语域。image_prompt 要一眼唤起这个词的含义，风格写 concept illustration。";

    private static final String SYS_STORY =
            "你是英语读物作者，为备考四六级的学生写记忆短篇。\n" +
            "把用户给的单词全部用进一个连贯的小故事里，要求：\n" +
            "1. 故事 150-220 词，有具体场景和人物，不要写成例句堆砌；\n" +
            "2. 每个目标词第一次出现时用【】括起来；\n" +
            "3. 语言难度控制在四六级阅读水平，句子不要过长；\n" +
            "4. 只输出一个 JSON 对象：{\"title\":\"中文小标题\",\"en\":\"英文故事\",\"zh\":\"对应中文翻译\"}";

    // ================= 整段翻译 =================

    public static Result translate(Context c, String en) throws Exception {
        String text = en == null ? "" : en.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("没有取到英文文本");
        if (text.length() > 6000) text = text.substring(0, 6000);

        boolean llm = Prefs.ENGINE_LLM.equals(Prefs.engine(c));
        if (llm && !Prefs.apiKey(c).isEmpty()) {
            try {
                return new Result(llmChat(c, SYS_TRANSLATE, text, 1500), modelLabel(c));
            } catch (Exception e) {
                String free = freeTranslate(text);
                return new Result(free + "\n\n（AI 引擎调用失败：" + brief(e.getMessage())
                        + "，已自动降级为免费翻译）", "免费引擎");
            }
        }
        return new Result(freeTranslate(text), "免费引擎");
    }

    // ================= AI 记忆素材 =================

    /** 把一个词拆成"怎么记"的素材包。 */
    public static WordAnalysis analyzeWord(Context c, String word, String sentence) throws Exception {
        requireKey(c);
        String user = "单词：" + word + (sentence == null || sentence.isEmpty()
                ? "" : "\n它出现的句子：" + sentence);
        JSONObject o = extractJson(llmChat(c, SYS_WORD, user, 700));

        WordAnalysis a = new WordAnalysis();
        a.roots = o.optString("roots", "");
        a.memory = o.optString("memory", "");
        a.scene = o.optString("scene", "");
        a.homophone = o.optString("homophone", "");
        a.imagePrompt = o.optString("image_prompt", "");
        JSONArray ex = o.optJSONArray("examples");
        if (ex != null) {
            for (int i = 0; i < ex.length() && i < 4; i++) {
                JSONObject e = ex.optJSONObject(i);
                if (e == null) continue;
                String en = e.optString("en", "").trim();
                String cn = e.optString("cn", "").trim();
                if (!en.isEmpty()) a.examples.add(new String[]{en, cn});
            }
        }
        return a;
    }

    /** 把没掌握的词编成一个故事。 */
    public static Story makeStory(Context c, List<String> words) throws Exception {
        requireKey(c);
        if (words == null || words.isEmpty()) throw new IllegalArgumentException("没有可用的单词");
        StringBuilder sb = new StringBuilder("这些词是：");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(words.get(i));
        }
        JSONObject o = extractJson(llmChat(c, SYS_STORY, sb.toString(), 1400));
        Story st = new Story();
        st.title = o.optString("title", "");
        st.en = o.optString("en", "").trim();
        st.zh = o.optString("zh", "").trim();
        if (st.en.isEmpty()) throw new IllegalStateException("故事生成结果为空");
        return st;
    }

    private static void requireKey(Context c) {
        if (Prefs.apiKey(c).isEmpty()) {
            throw new IllegalStateException("未配置 API Key");
        }
    }

    /** 模型有时会裹一层代码块或前后加解释，这里做容错提取。 */
    private static JSONObject extractJson(String raw) throws Exception {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("模型没有返回 JSON");
        return new JSONObject(raw.substring(start, end + 1));
    }

    private static String modelLabel(Context c) {
        String m = Prefs.model(c);
        return m == null || m.isEmpty() ? "AI" : m;
    }

    private static String brief(String s) {
        if (s == null) return "未知错误";
        s = s.replace('\n', ' ').trim();
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }

    // ================= LLM =================

    private static String llmChat(Context c, String system, String user, int maxTokens) throws Exception {
        String base = Prefs.apiBase(c);
        if (base == null || base.isEmpty()) base = "https://api.deepseek.com/v1";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + "/chat/completions";

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", user));

        JSONObject body = new JSONObject();
        body.put("model", Prefs.model(c));
        body.put("messages", messages);
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);

        String resp = Http.postJson(url, body.toString(), Prefs.apiKey(c), 45000);
        JSONObject o = new JSONObject(resp);
        JSONArray choices = o.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalStateException("接口没有返回结果: " + brief(resp));
        }
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        String content = msg == null ? null : msg.optString("content", "");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("接口返回空内容");
        }
        return content.trim();
    }

    // ================= 免费引擎 =================

    private static String freeTranslate(String text) throws Exception {
        Exception last = null;
        try {
            String s = viaGoogle(text);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Exception e) { last = e; }
        try {
            String s = viaMyMemory(text);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Exception e) { last = e; }
        throw new Exception("免费翻译接口不可用"
                + (last == null ? "" : "（" + brief(last.getMessage()) + "）")
                + "，建议在设置里填一个 AI 接口 Key");
    }

    private static String viaGoogle(String text) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q="
                + Http.enc(text);
        String resp = Http.get(url, 15000);
        JSONArray root = new JSONArray(resp);
        JSONArray segs = root.getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length(); i++) {
            JSONArray seg = segs.optJSONArray(i);
            if (seg != null && seg.length() > 0) sb.append(seg.optString(0, ""));
        }
        return sb.toString();
    }

    private static String viaMyMemory(String text) throws Exception {
        String url = "https://api.mymemory.translated.net/get?q=" + Http.enc(text) + "&langpair=en|zh-CN";
        String resp = Http.get(url, 20000);
        JSONObject o = new JSONObject(resp);
        JSONObject d = o.optJSONObject("responseData");
        if (d == null) throw new IllegalStateException("响应格式异常");
        return d.optString("translatedText", "");
    }
}
