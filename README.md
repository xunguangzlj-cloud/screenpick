# 拾词 ScreenPick

> 安卓悬浮取词工具：点球翻译整屏英文，点词查四六级释义，顺手收进单词本、按天复习、AI 编故事记词。

**核心链路**：悬浮球 → 抓当前屏英文文本 → AI 按句翻译 → 点任意单词 → 自动复制 + 弹词条（音标/释义/变体/考纲标签）。

**不截图、不做 OCR**：直接读无障碍文本树，拿到的是应用自己声明的原文，没有识别错字、没有延迟、不需要截屏授权，也不依赖 Google 服务。

| | |
|---|---|
| 包名 | `com.screenpick.app` |
| 体积 | APK 约 2 MB（含离线词库） |
| 最低版本 | Android 8.0（API 26） |
| 依赖 | 零第三方库，纯 Java + 系统 API |
| 许可 | MIT（词典数据另见 [LICENSE](LICENSE)） |

### 自己编译

不需要 Android Studio 或 Gradle，只用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner`：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk   # 需 platforms/android-34 与 build-tools/34.0.0
export JAVA_HOME=/path/to/jdk17
bash build.sh                                   # 产出 dist/screenpick-v1.0.apk
```

之所以用脚本而不是 Gradle：整个工程 22 个源文件、零第三方依赖，为编一个 2 MB 的包去拉几百 MB 的构建依赖不划算。

---


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

### 复习是怎么走的

严格按"先想、再看、再决定"三步，不会替你跳过思考：

1. 屏幕只给英文单词和发音，两个按钮是暗的
2. 点卡片 → 翻出中文释义，按钮亮起
3. **斩**：写库标记掌握，之后再也不出现在任何复习队列里
   **跳过**：原样放回队尾，本轮还会再遇到

一轮走完给结算：斩了几个、跳了几次、还剩多少。剩的可以「再来一轮」，因为队列是按 `reviews` 次数排序的，跳过越少的词越靠前。

### 无障碍服务的"默认开启"

Android 不允许 App 自己打开无障碍服务（这是安全设计，没有绕过办法）。

所以实际做法是：**每次打开 App，只要检测到没开，就直接跳到「拾词 · 屏幕取词」这个开关的详情页**——不是丢你去无障碍列表里翻。这条路径用的是 `android.settings.ACCESSIBILITY_DETAILS_SETTINGS` + `EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME`，能精确落到我们自己的服务上，只需按一下开关。

### 牛津词典这条路是怎么走通的

先说走不通的：**Oxford Dictionaries API**（`od-api.oxforddictionaries.com`）确实存在，无认证访问返回 `401 Authentication parameters missing or invalid`，说明端点活着；但它要 app_id/app_key 注册申请，免费档每月 1000 次，而且**只给英文**。对四六级备考，中文释义才是主力，这条路的边际价值不高。

真正走通的是 **Oxford Learner's Dictionaries 网页**：`/definition/english/<word>` 公开可读，返回词性、音标和编号释义。覆盖面比本地 Oxford5000 词表（5836 条）大得多——`stack`、`ambiguous` 这类不在核心词表里的词，官网都有。

接入方式是**用户点「牛津原文」才去取**，不做自动请求：单次响应约 100KB，自动拉会让每次查词都变慢。

抓取解析踩过两个坑，都修了：

1. 不存在的词返回 **HTTP 404**，最初被当成"网络失败"报错——应该走"未收录"分支
2. 释义里嵌着 `<a>` 链接，正则 `[^<]{0,260}` 会在第一个 `<` 处截断，`stack` 的第 3 条释义被截成 `a tall`。改成允许内部标签再从结果里剥标签，恢复成 `a tall chimney`

核验结果：抽 6 个词抓官方原文对照，本地中文释义**全部对得上**（`evidence` 的 "the facts, signs or objects that make you believe that something is true" ↔ "证据，证明；迹象"）。

真机实测（`stack`）：

```
牛津词典原文
/stæk/  noun
1. a pile of something, usually neatly arranged
2. a large number or amount of something; a lot of something
3. a tall chimney
4. the part of a library, sometimes not open to the public, where books that are not often needed are stored
来源：oxfordlearnersdictionaries.com
```

第一个版本有个体验问题：结果追加在弹窗底部，**落在可视区外**，用户点完以为没反应（`logcat` 里能看到 `Skipping invisible child ... visible: false`）。现在结果出来后自动 `fullScroll(FOCUS_DOWN)`。

### 词库是怎么拼出来的

单一词库满足不了备考：ECDICT 词量大、带考纲标签和词形变化，但释义冗长、混着专业标注；qwerty-learner 的 CET 词表释义精炼、贴近考试，但词量只有 3920 个。

所以三个源融合：

| 来源 | 提供什么 | 覆盖 |
|---|---|---|
| ECDICT | 骨架：11228 词、cet4/cet6 标签、柯林斯星级、词形变化、WordNet 英英释义 | 全部（英英 99%） |
| Oxford5000（qwerty 收录） | **牛津高阶风格中文释义**，义项顺序最权威 | 4894 词（43%） |
| CET4/CET6/Oxford3000 词表 | 考试向简短释义 | 4615 词（41%） |
| 有道词典（在线） | 英美音标 | 查词时实时 |

释义按"权威度"叠了四层，默认只露最上面一层：

- `oxford` 牛津高阶释义 —— 有就放最前，标一个绿色"牛津释义"角标
- `brief` 考试向短释义 —— 没有牛津时顶上
- `trans` ECDICT 完整释义 —— 再退一步
- `ee` / `extra` WordNet 英英释义、`[医][计][法]` 专业义项 —— 全部收进「更多释义」，点开才展开

**牛津释义最直接的价值是义项顺序**：`evidence` 以前第一行是「n. 根据」，现在是「n. 证据，证明；迹象；明显 / vt. 证明」——这才是考生该先看到的。

**为什么值得拆**：实测 41% 的 CET 词释义里混着专业标注，全库 `[医]` 就有 13.7 万条。以前 `stack` 的第一行是「n. 堆叠, 堆, 大量, 书库, 枪架, 烟囱, 堆积, 存贮栈 vt. 堆积...」，现在第一行就是「n. 堆，垛」。

### 音标和音频都分英美

ECDICT 只给一套音标，所以本地先显示那一套；拿到词条后异步向有道补 `usphone`/`ukphone`，回来再替换：

- 有差异 → 上下两行 `美 /təˈmeɪtoʊ/`、`英 /təˈmɑːtəʊ/`，各带色标
- 没差异 → 一行，后面标「英美同音」（如 stack 的 `/stæk/`）

这一步是**增强不是依赖**：请求失败就保持本地音标，查词主流程不受影响。同一套逻辑在悬浮面板和单词本弹窗里都做了。

### 发音、变体、谐音这三个东西的分工

三件事解决的是三个不同问题，别混在一起看：

- **发音**解决"读不对"。音频走有道词典（`type=2` 美音 / `type=1` 英音），是真人录音，不是 TTS 合成的机械音。网络失败才回退系统 TTS——所以点了永远有声音，只是音质会掉一档。
- **变体**解决"认得但没见过它的变化形式"。数据来自 ECDICT 的 `exchange` 字段，5802 个 CET 词里 **4702 个（81%）** 能列出变体，包括不规则变化（run → ran、good → better）。变体本身可点，点了直接查那个形式。
- **谐音**解决"记不住"。这条最容易帮倒忙——谐音记的是中文声音，跟英文发音经常对不上，用久了会把口语带偏。所以它：① 只让 AI 在真能起作用时给，不适合就返回空；② 单独一块橙色卡片，标题写死"只帮记忆，不是真实发音"。

## 三个关键设计决策

这三个决定构成了它和"又一个屏幕翻译 App"的区别。

### 1. 不截图 OCR，直接读无障碍文本树

`ciddwd/overlay-translator`（1k★）用 MediaProjection 截屏 + OCR，通用性最强，但代价是：每次要授权截屏、OCR 有错字、还要吃 100~300ms。

拾词走另一条路——`AccessibilityService.getRootInActiveWindow()` 拿应用**自己声明的文本**，再按屏幕坐标还原成段落（`TextGrabber`）。收益：

- 零识别错误：拿到的是原始字符串，不是识别结果
- 零延迟、不耗电
- 不需要截屏授权，也不上传任何画面
- 不依赖 Google 服务，国内手机直接可用

代价是只对暴露无障碍文本的界面有效。这是明确的取舍，不是遗漏。

### 2. 不用 Activity 展示译文，全部走 WindowManager 悬浮层

从 Service 后台启动 Activity 在 Android 10+ 会被系统直接拦掉（后台启动限制）。虽然 `SYSTEM_ALERT_WINDOW` 是官方豁免路径之一，但把整个交互都放进悬浮层反而更好：不闪屏、不打断阅读、天然"叠在屏幕上"。

顺带解决了一个隐患：悬浮球不可聚焦（`FLAG_NOT_FOCUSABLE`），所以点它的时候 `getRootInActiveWindow()` 拿到的仍然是底层应用的窗口，不会抓到自己。

### 3. 词库内置，而不是查在线词典

四六级备考真正需要的信息是**这个词在不在考纲里、考的频率多高**。这些信息在线词典要么不给，要么要 token。

所以内置一个 1.4MB 的 SQLite（`assets/dict.db`）：从 [ECDICT](https://github.com/skywind3000/ECDICT)（8.5k★）的 66MB 全量库筛出 **11228 词**，其中 **5802 个带 cet4/cet6 标签**，另附柯林斯星级、牛津 3000 标记和词频排名。

查不到的词走 AI 在线解释——两条路，不是二选一。

## 参考的开源项目

| 项目 | Stars | 借鉴的点 |
|---|---|---|
| [ciddwd/overlay-translator](https://github.com/ciddwd/overlay-translator) | 1075 | 悬浮球触发、截屏→识字→翻译→叠加的管线分层 |
| [dominostars/playtranslate](https://github.com/dominostars/playtranslate) | 1294 | 实时翻译同时承担语言学习职能的产品定位 |
| [ramjke/Translumo](https://github.com/ramjke/Translumo) | 5858 | OCR + 翻译管线的工程化组织 |
| [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT) | 8476 | 词库数据源与 cet4/cet6 考纲标签 |
| [RealKai42/qwerty-learner](https://github.com/RealKai42/qwerty-learner) | 23454 | CET 词表组织方式、音标 + 释义的数据结构 |
| [yomidevs/yomitan](https://github.com/yomidevs/yomitan) | — | "点词弹出释义"的交互范式 |

---

## 安装

APK 在 `dist/screenpick-v1.0.apk`（745 KB，minSdk 26 / targetSdk 34，已签名）。

```bash
adb install -r dist/screenpick-v1.0.apk
```

装完打开「拾词」，按主页从上到下点三下：

1. **去授权** → 允许"显示在其他应用上层"
2. **去开启** → 系统无障碍设置里打开「拾词 · 屏幕取词」
3. **开启悬浮球** → 屏幕上出现蓝色 A 球

## 配置翻译引擎

**这一步建议不要跳过。** 内置的免费通道（Google / MyMemory）在国内经常连不上，质量也一般。

设置页填三样东西，默认都指向 DeepSeek：

| 项 | 默认值 |
|---|---|
| 接口地址 | `https://api.deepseek.com/v1` |
| 模型 | `deepseek-chat` |
| API Key | 空（去 platform.deepseek.com 申请） |

任何 OpenAI 兼容接口都能用：OpenAI、通义千问、Kimi、硅基流动……改地址和模型即可。填完点「测试连通性」，会真的发一句英文过去验证。

翻译 prompt 是为四六级调的：按句翻、保段落、术语首次出现保留英文原词、不带任何解释性前后缀（否则原文和译文对不上，点词会错位）。

## 用法

| 操作 | 效果 |
|---|---|
| 点悬浮球 | 翻译当前整屏英文 |
| 面板里点任意单词 | 复制 + 弹词条（音标、释义、考纲标签、柯林斯星级） |
| 别的 App 里长按选中英文 | 直接弹词条（可关） |
| 长按悬浮球 | 回主页 |
| 拖动悬浮球 | 换位置 |

译文下方还会列出**本屏出现的四六级词**（按词频排序，最该认识的排前面），点一下就能查。

## 构建事故记录（2026-10-21）

这一轮踩了一个差点交付废品的坑，写下来防止复发。

改词库时把 `Entry.oxford` 从 `int`（牛津3000标记）改成了 `String`（牛津释义），但 `PanelView` 里还留着 `if (e.oxford == 1)`。javac 报错，**但 `build.sh` 里写的是**：

```bash
javac ... @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true
```

管道 + `|| true` 把退出码彻底吞了。构建照常"成功"，d8 打包的却是上一轮残留的 41 个 class（应为 126 个），APK 缺了 `Ui`、`PanelView` 等一大批类。

装到手机上表现为：

```
ActivityTaskManager: Activity top resumed state loss timeout
DigestGenerator: digest=============exp_appbootfailcom.screenpick.app
FATAL EXCEPTION: NoClassDefFoundError: Lcom/screenpick/app/Ui;
```

**症状极具误导性**：看起来像主线程阻塞导致的启动超时，我第一反应也是"4MB 词库复制卡住了"，还为此把词库读取改成了异步——那部分改动无害，但**不是病因**。真相要靠 `logcat | grep -A 40 FATAL` 的堆栈才看得出来。

现在构建脚本做了两件事防复发：

1. `javac` 用 `if ! ... then exit 1`，日志落盘后原样打印
2. **产物校验**：遍历每个源文件，检查 `$OBJ/com/screenpick/app/<类名>.class` 是否存在，缺一个立即中止——即使 javac 返回 0 也拦得住

## 已知边界

- **图片、视频、游戏、Canvas 渲染的文字读不到。** 那些没有无障碍文本节点，需要 OCR 通道，本版没做。
- **必须开无障碍服务。** 这是读屏的唯一通道；App 只能把你送到开关页，按下去得你自己来。
- 依赖第三方翻译接口，网络不通就翻不了（会明确报错，不会静默失败）。
- **AI 记忆和故事需要 API Key**，词根例句配图都是现生成的，没法内置。
- **配图走 Pollinations 免费生图接口**，不保证每次都出图，慢的时候十几秒；加载失败只影响那一块，词条本身照常显示。图是"意象图"，不是这个词的标准插画。
- **有道的牛津释义拿不到**：`oxford` / `oxfordAdvance` / `webster` 三个模块返回的都是 `encryptedData`（加密串），解不开。所以牛津释义用的是 qwerty 收录的 Oxford5000 词表，覆盖 43%，不是完整词典。`stack`、`ambiguous` 这类不在牛津 5000 核心词表里的词就只有考试简释。
- **qwerty 词表里的英美音标有错位**：实测 `context` 给的是 美 `ˈkɒntekst` / 英 `ˈkɑːntekst`（正好反了），`retain` 的美音也不对。所以这份离线音标只作为**不带标签的单条参考**显示，绝不分"美/英"；只有有道在线返回的才分两套。宁可少给，不给错的。
- **ECDICT 的多义项之间存的是字面 `
`**（反斜杠 + n），不是真实换行。所有显示释义的地方都必须先 `.replace("\n", "
")`，否则用户会看到一排 `
` 字符。
- **词库升级会覆盖已释放的旧库**：`Dict` 里有个 `ASSET_VERSION` 常量，改词库结构时必须 +1，否则老用户手上的旧库永远不会被换掉（这个坑踩过一次）。
- 收藏记录里存的是**收藏那一刻的释义快照**。以后词库升级不会让旧收藏变成空壳，但也意味着释义不会自动更新。
- 只在 Android 16 / 小米真机上验证过启动与权限流程；单词本的建表、分组、复习队列、跳过重排用真实 SQLite 跑过一遍断言，但复习与故事两个页面的渲染没做真机回归。

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

之所以这么搭：这套工程只有 13 个 Java 文件、零第三方依赖，用 Gradle 反而要拉几百 MB 的依赖才能编一个 745KB 的包。

### 跑词形还原测试

```bash
javac -encoding UTF-8 -d /tmp/mt src/com/screenpick/app/Morphology.java test/com/screenpick/app/MorphTest.java
python -c "import sqlite3;print('\n'.join(r[0] for r in sqlite3.connect('assets/dict.db').execute('SELECT lower FROM dict')))" > /tmp/words.txt
java -cp /tmp/mt com.screenpick.app.MorphTest /tmp/words.txt
```

87 个变形词用例，当前 **87/87 = 100%**。

这个测试不是形式主义——它抓出过一个真实 bug：`using` 原先被还原成 `us`（代词），因为候选顺序把"直接去 ing"排在"词干 + e"前面。查 `using` 给出代词释义，比查不到还糟。修法是调整候选优先级，并把这条规则锁进测试。

## 许可与数据来源

代码以 [MIT](LICENSE) 发布。

词条数据来自两个 MIT 开源项目：

- [ECDICT](https://github.com/skywind3000/ECDICT) —— 词条骨架、考纲标签、柯林斯星级、词形变化、WordNet 英英释义
- [qwerty-learner](https://github.com/RealKai42/qwerty-learner) —— CET4/CET6/Oxford3000/Oxford5000 的考试向中文释义

Oxford Learner's Dictionaries 的原文释义由 App **运行时按需抓取**，不随仓库或安装包分发，仅供个人学习。

词库可用 `python tools/build_dict.py` 从零重建（需自备 ECDICT 的 `ecdict.csv`）。
