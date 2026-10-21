# 拾词 ScreenPick

> 安卓悬浮取词工具：点球翻译整屏英文，点词查四六级释义，顺手收进单词本、按天复习、AI 编故事记词。

**核心链路**：悬浮球 → 抓当前屏英文文本 → AI 按句翻译 → 点任意单词 → 自动复制 + 弹词条（音标/释义/变体/考纲标签）。

**不截图、不做 OCR**：直接读无障碍文本树，拿到的是应用自己声明的原文，没有识别错字、没有延迟、不需要截屏授权，也不依赖 Google 服务。

## 功能

| 能力 | 入口 | 说明 |
|---|---|---|
| 整屏翻译 | 点悬浮球 | 抓当前屏英文文本 → 按句翻译，标注本屏出现的四六级词 |
| 点词查义 | 面板里点任意单词 | 自动复制 + 弹出音标、释义、考纲标签、柯林斯星级 |
| **真人与音标** | 音标区 + 「▶ 美音」/「▶ 英音」 | 音频和音标都分英美两套；两者相同的词明确标「英美同音」 |
| **主释义加粗** | 词条卡第一行 | 词典里排第一的义项单独加粗放大，其余降一档灰色，先记住最该记的那个 |
| **词形变化** | 释义下方的变体行 | 过去式 / 过去分词 / 现在分词 / 第三人称 / 复数 / 比较级 / 最高级，点一下跳到该变体 |
| **谐音助记** | AI 记忆里的橙色块 | 只在真能起作用时给，明确标注"不是真实发音" |
| 选中查词 | 任意 App 里长按选中英文 | 直接弹词条，不用先点悬浮球 |
| **收藏** | 词条卡里的「收藏」 | 写进单词本，附带释义快照 |
| **单词本** | 主页「我的单词本」 | 按收藏当天分组，今天 / 昨天 / 10月19日 |
| **复习** | 单词本「开始复习」 | 出英文 → 点卡片看释义 → 斩 / 跳过 |
| **记忆故事** | 单词本「编个故事」 | 把没斩的词交给 AI 编成 150-220 词短篇 |
| **牛津原文** | 词条卡里的「牛津原文」 | 实时抓取 Oxford Learner's Dictionaries 的英文原文释义 |
| **AI 记忆** | 词条卡里的「AI 记忆」 | 词根词缀、记忆钩子、情景、例句、AI 配图 |


## 源码结构与重建

```
screenpick/
├── AndroidManifest.xml
├── assets/dict.db                  内置词库（11228 词）
├── src/com/screenpick/app/
│   ├── MainActivity.java           主页、权限引导、单词本入口
│   ├── SettingsActivity.java       引擎与交互配置
│   ├── OverlayService.java         悬浮球、窗口管理、业务编排
│   ├── PanelView.java              面板 UI（纯代码构建，无布局文件）
│   ├── ScreenReaderService.java    无障碍读屏 + 选中查词
│   ├── TextGrabber.java            节点树 → 按阅读顺序的段落
│   ├── Translator.java             翻译 + AI 记忆素材 + 故事生成
│   ├── Dict.java                   只读词库查询 + 考纲词扫描
│   ├── Morphology.java             词形还原（纯 Java，有单测）
│   ├── BookDb.java                 单词本（可写库，按天分组）
│   ├── WordbookActivity.java       单词本列表
│   ├── ReviewActivity.java         复习卡片
│   ├── StoryActivity.java          记忆故事
│   ├── ImageLoader.java            配图加载（内存 LRU）
│   ├── Pronounce.java              真人发音（在线音频 + TTS 兜底）
│   ├── WordPop.java                普通页面里的点词弹窗
│   └── Http.java / Prefs.java / Ui.java
├── test/com/screenpick/app/MorphTest.java
├── build.sh                        手工构建脚本
└── dist/screenpick-v1.0.apk
```

**构建不依赖 Gradle / Android Studio**，只用 SDK 自带的 aapt2 + javac + d8 + zipalign + apksigner：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # 需要 platforms/android-34 与 build-tools/34.0.0
export JAVA_HOME=/path/to/jdk17
bash build.sh
```

## 许可与数据来源

代码以 [MIT](LICENSE) 发布。
