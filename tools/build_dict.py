#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从零重建 assets/dict.db。

仓库里已经带了构建好的 dict.db，正常构建不需要跑这个脚本。
只有在想更新词库、或想改词条字段时才需要重新生成。

数据来源：
  · ECDICT            https://github.com/skywind3000/ECDICT          (MIT)
      提供：词条骨架、cet4/cet6 标签、柯林斯星级、词形变化、WordNet 英英释义
  · qwerty-learner    https://github.com/RealKai42/qwerty-learner    (MIT)
      提供：CET4/CET6/Oxford3000/Oxford5000 的考试向中文释义

用法：
  python tools/build_dict.py

前置：把 ECDICT 的 ecdict.csv 放到 refdata/ 下（约 66MB），
      脚本会用 jsDelivr 自动下载 qwerty 的四个词表 JSON。
"""

import csv
import json
import os
import re
import sqlite3
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "refdata")
OUT = os.path.join(ROOT, "assets", "dict.db")

LN = chr(10)
BS = chr(92)
PRIMARY = chr(0x2C8)  # 主重音符 ˈ

QWERTY_DICTS = {
    "CET4_T.json": "https://cdn.jsdelivr.net/gh/RealKai42/qwerty-learner@master/public/dicts/CET4_T.json",
    "CET6_T.json": "https://cdn.jsdelivr.net/gh/RealKai42/qwerty-learner@master/public/dicts/CET6_T.json",
    "Oxford3000.json": "https://cdn.jsdelivr.net/gh/RealKai42/qwerty-learner@master/public/dicts/Oxford3000.json",
    "Oxford5000.json": "https://cdn.jsdelivr.net/gh/RealKai42/qwerty-learner@master/public/dicts/Oxford5000.json",
}

# Oxford5000 的 trans 末尾会拼上"时态:abandoned, ..."这类词形信息，在这里切断
FORM_TAIL = re.compile(
    r"(时态:|比较级:|最高级:|副\s*词:|名\s*词:|过去式:|第三人称:|复数:|过去分词:|现在分词:)")
POS_TAIL = re.compile(r"\s*\((vt|vi|n|adj|adv|prep|conj|pron|v|a|num|int)\.\)\s*$")
BRACKET = re.compile(r"^\s*\[([^\]]{1,6})\]")

FORM_LABEL = {"p": "过去式", "d": "过去分词", "i": "现在分词", "3": "第三人称",
              "s": "复数", "r": "比较级", "t": "最高级"}


def download_refs():
    os.makedirs(REF, exist_ok=True)
    for name, url in QWERTY_DICTS.items():
        path = os.path.join(REF, name)
        if os.path.exists(path) and os.path.getsize(path) > 1000:
            print(f"  已有 {name}")
            continue
        print(f"  下载 {name} …")
        urllib.request.urlretrieve(url, path)
    csv_path = os.path.join(REF, "ecdict.csv")
    if not os.path.exists(csv_path):
        sys.exit(f"缺少 {csv_path}\n请从 https://github.com/skywind3000/ECDICT 下载 ecdict.csv 放到 refdata/")
    return csv_path


def clean_oxford(items):
    out = []
    for t in items or []:
        t = (t or "").strip()
        if not t:
            continue
        m = FORM_TAIL.search(t)
        if m:
            t = t[:m.start()].strip()
        if t:
            out.append(t)
    return LN.join(out)


def clean_brief(item):
    lines = []
    for t in item.get("trans") or []:
        t = t.strip()
        if not t:
            continue
        m = POS_TAIL.search(t)
        if m:
            t = m.group(1) + ". " + POS_TAIL.sub("", t).strip()
        lines.append(t)
    return LN.join(lines)


def load_json_words(path):
    out = {}
    for x in json.load(open(path, encoding="utf-8")):
        w = (x.get("name") or "").strip().lower()
        if w:
            out[w] = x
    return out


def fmt_exchange(raw):
    kv = {}
    for part in (raw or "").split("/"):
        if ":" in part:
            k, v = part.split(":", 1)
            kv[k.strip()] = v.strip()
    return "|".join(FORM_LABEL[k] + ":" + kv[k]
                    for k in ["p", "d", "i", "3", "s", "r", "t"] if kv.get(k))


def main():
    csv_path = download_refs()

    oxford = {}
    p = os.path.join(REF, "Oxford5000.json")
    for w, x in load_json_words(p).items():
        c = clean_oxford(x.get("trans"))
        if c:
            oxford[w] = c

    brief = {}
    for name in ("Oxford3000.json", "CET4_T.json", "CET6_T.json"):
        for w, x in load_json_words(os.path.join(REF, name)).items():
            brief[w] = clean_brief(x)

    rows = {}
    with open(csv_path, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            w = (r.get("word") or "").strip()
            if not w or " " in w or len(w) < 2:
                continue
            if not w.replace("-", "").replace("'", "").isalpha():
                continue
            tag = (r.get("tag") or "").split()
            raw = (r.get("translation") or "").strip()
            if not raw:
                continue
            frq = int(r.get("frq") or 0)
            cet = ("cet4" in tag) or ("cet6" in tag)
            if not (cet or (0 < frq <= 12000)):
                continue

            general, extra = [], []
            for line in raw.replace(BS + "n", LN).split(LN):
                line = line.strip()
                if not line:
                    continue
                (extra if BRACKET.match(line) else general).append(line)
            if not general:
                general, extra = extra, []

            key = w.lower()
            prev = rows.get(key)
            if prev and prev["cet"] and not cet:
                continue

            ee = (r.get("definition") or "").strip().replace(BS + "n", LN)
            rows[key] = dict(
                word=w,
                phonetic=(r.get("phonetic") or "").strip(),
                brief=brief.get(key, ""),
                trans=LN.join(general)[:500],
                extra=LN.join(extra)[:300],
                oxford=oxford.get(key, ""),
                ee=ee[:420],
                tag=" ".join(tag),
                star=int(r.get("collins") or 0),
                ox=1 if (r.get("oxford") or "").strip() else 0,
                frq=frq,
                cet=cet,
                exch="",
            )

    # 第二遍补词形变化
    with open(csv_path, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            k = (r.get("word") or "").strip().lower()
            if k in rows:
                rows[k]["exch"] = fmt_exchange(r.get("exchange"))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)
    con = sqlite3.connect(OUT)
    con.execute("""CREATE TABLE dict(word TEXT, lower TEXT PRIMARY KEY, phonetic TEXT,
      brief TEXT, trans TEXT, extra TEXT, oxford TEXT, ee TEXT, tag TEXT,
      star INTEGER, ox INTEGER, frq INTEGER, exch TEXT)""")
    con.executemany(
        "INSERT OR REPLACE INTO dict VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(v["word"], k, v["phonetic"], v["brief"], v["trans"], v["extra"], v["oxford"],
          v["ee"], v["tag"], v["star"], v["ox"], v["frq"], v["exch"])
         for k, v in rows.items()])
    con.execute("CREATE INDEX idx_word ON dict(word)")
    con.commit()
    con.close()

    n = len(rows)
    print(f"\n词条 {n}")
    for label, key in [("牛津释义", "oxford"), ("考试向简释", "brief"), ("英英释义", "ee"),
                       ("专业义项", "extra"), ("词形变化", "exch")]:
        c = sum(1 for v in rows.values() if v[key])
        print(f"  {label:8}: {c} ({c * 100 // n}%)")
    print(f"  已写入 {OUT}（{round(os.path.getsize(OUT) / 1024)} KB）")
    print("\n提示：词库结构变了要把 Dict.java 里的 ASSET_VERSION 加 1，否则老用户不会换库。")


if __name__ == "__main__":
    main()
