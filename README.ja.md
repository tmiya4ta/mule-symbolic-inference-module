# mule-symbolic-inference-module

[English](README.md) | **日本語**

**LLMを使わずに**、自然言語から「意図(intent) + パラメータ(slot)」を推論する **MuleSoft Mule 4 SDK モジュール**。
retrieval-based の TF‑IDF コサイン類似度で意図を分類し、正規表現でパラメータ（スロット）を抽出する。
**完全に決定論的・プロセス内で動作し、外部API・GPU・ネットワークを一切必要としない**。

```
入力 (自然言語):  「注文12345を3個、2026-06-10に配送して」
   │  <infer:classify>
   ▼
出力 (JSON):  {
  "intent": "SHIPPING",
  "confidence": 0.50,
  "ranking": { "SHIPPING": 0.50, "RETURN": 0.17, ... },
  "slots":   { "orderId": "12345", "quantity": "3", "date": "2026-06-10" }
}
```

すべてJVM内で動くため、**レイテンシ・コスト・決定性・プライバシー・監査性**が重要な場面で強い。
難しいケースだけLLMにフォールバックする構成とも相性が良い（[できること・できないこと](#できることできないこと)参照）。

---

## 目次

- [なぜLLMを使わないのか](#なぜllmを使わないのか)
- [リポジトリ構成](#リポジトリ構成)
- [仕組み](#仕組み)
- [動作要件](#動作要件)
- [ビルド](#ビルド)
- [Muleフローでの使い方](#muleフローでの使い方)
- [設定](#設定)
- [出力スキーマ](#出力スキーマ)
- [辞書の作り方](#辞書の作り方)
- [できること・できないこと](#できることできないこと)
- [ビルド上の注意](#ビルド上の注意)
- [動作確認状況](#動作確認状況)
- [ライセンス](#ライセンス)

---

## なぜLLMを使わないのか

| 観点 | 本モジュール (TF‑IDF + 正規表現) | 汎用LLM |
|---|---|---|
| レイテンシ | サブミリ秒・プロセス内 | ネットワーク往復（数百ms） |
| コスト | 呼び出し無料 | トークン課金 |
| 決定性・再現性 | 同じ入力→必ず同じ出力。監査可能 | 確率的 |
| プライバシー / データ越境 | JVM/VPC外に出ない | 発話を外部APIへ送信 |
| 可用性 | オフライン・APIキー/レート制限なし | 外部サービス依存 |
| 説明可能性 | コサインスコア＋当たった正規表現が見える | ブラックボックス |
| 開放的な精度 | 低め（辞書次第） | 高い（言い換えに頑健） |

最適な用途は **意図が限定的で安定したセットを大量に捌く**ケース。頑健性を最大化したいなら、
高信頼の多数を本モジュールで即決し、**低信頼 / `UNKNOWN` だけLLMにフォールバック**する。

---

## リポジトリ構成

| パス | 内容 |
|---|---|
| [`intent-core/`](intent-core/) | **外部依存ゼロの純Java推論コア。** TF‑IDFコサイン類似度・CJKバイグラムトークナイザ・ファイル駆動辞書。Mule非依存で単体テスト可能。 |
| [`mule-symbolic-inference-module/`](mule-symbolic-inference-module/) | コアをラップし `<infer:classify>` オペレーションと `<infer:config>` を提供する **Mule SDK 拡張**。 |
| [`examples/intent-sample-app/`](examples/intent-sample-app/) | `POST /classify` をHTTPで公開する実行可能なサンプルMuleアプリ。拡張辞書（`SHIPPING`意図と `quantity`/`phone`/`email` スロットを追加）を使用。 |

---

## 仕組み

```
text ──► トークナイザ ──► TF-IDFクエリベクトル ──► 各例とコサイン類似度
                                                       │
                                  意図ごとに最大スコア・降順ランキング
                                                       │
                              最良 < threshold ? "UNKNOWN" : 最良意図
                                                       │
text ──► SlotExtractor(正規表現) ──────────────────────┴──► { intent, confidence, ranking, slots }
```

1. **トークン化** (`CjkBigramTokenizer`) — ASCII連続は小文字の単語トークン、CJK連続（ひらがな/カタカナ/漢字）は**オーバーラップ文字バイグラム**。丁寧表現・疑問文末などはストップトークンとして除去し、類似度を支配しないようにする。
2. **ベクトル化** (`IntentModel`) — 各辞書例をTF‑IDFベクトル化（`idf = log((N+1)/(df+1)) + 1`）し、L2正規化してコサイン類似度を内積で計算。
3. **分類** (`IntentClassifier`) — クエリベクトルを全例と比較し、**意図ごとの最大スコア**を採ってランキング。最良が `threshold` 未満なら `UNKNOWN`。
4. **スロット抽出** (`RegexSlotExtractor`) — 各正規表現を原文にマッチさせ、最初のキャプチャグループ（無ければ全体）を値に。意図とスロットは**独立**なので片方を強化しても他方に影響しない。

すべて**データ駆動**：意図・スロット・閾値はプレーンテキストにあり、再コンパイルなしで挙動を変えられる（[辞書の作り方](#辞書の作り方)）。

---

## 動作要件

- ビルドに **Java 17**（コアがrecordを使用）。モジュールは `supportedJavaVersions = ["17","21"]` を宣言。
- **Mule 4.9.8+** ランタイム（CE/EE 両対応）。`requiredProduct = MULE`。
- Maven 3.9+。

---

## ビルド

依存順にビルドする。コアを先にローカルMavenへ install すること。

```bash
export JAVA_HOME=/path/to/jdk-17

# 1) 純Javaコア
(cd intent-core && mvn clean install -DskipTests)

# 2) Mule SDKモジュール → mule-symbolic-inference-module-0.1.0-mule-plugin.jar を生成
(cd mule-symbolic-inference-module && mvn clean install -DskipTests)

# 3) サンプルアプリ
(cd examples/intent-sample-app && mvn clean package -DskipTests -DattachMuleSources)
```

モジュールの座標: `com.demo:mule-symbolic-inference-module:0.1.0`（classifier `mule-plugin`）。

---

## Muleフローでの使い方

アプリの `pom.xml` に依存追加:

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>mule-symbolic-inference-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

最小フロー — `classify` は `application/json` を返すのでそのままHTTP応答にできる:

```xml
<infer:config name="infer-config"/>            <!-- 同梱デフォルト辞書 -->

<flow name="classify-flow">
    <http:listener config-ref="http-listener-config" path="/classify"/>
    <infer:classify config-ref="infer-config" text="#[payload]"/>
</flow>
```

呼び出し:

```bash
curl -X POST http://localhost:8081/classify \
     -H 'Content-Type: text/plain; charset=UTF-8' \
     -d '注文12345の配送状況を教えて'
# => {"intent":"ORDER_STATUS","confidence":0.95,"ranking":{...},"slots":{"orderId":"12345"}}
```

---

## 設定

`<infer:config>` のパラメータ:

| パラメータ | 必須 | 説明 |
|---|---|---|
| `dictionaryDir` | 任意 | `intents.tsv`/`slots.tsv`/`intent.properties` を置いたディレクトリの絶対パス。未指定ならモジュール同梱の辞書を使用。 |
| `thresholdOverride` | 任意 | `UNKNOWN` 判定閾値の上書き。未指定なら `intent.properties` の値（デフォルト `0.15`）。 |

分類器（TF‑IDFモデル）は設定の初期化時（`Initialisable#initialise`）に**1度だけ**構築され、以降のリクエストで再利用される（リクエストごとの再構築なし）。

---

## 出力スキーマ

`<infer:classify>` はJSONを返す:

```json
{
  "intent": "ORDER_STATUS",
  "confidence": 0.9544,
  "ranking": { "ORDER_STATUS": 0.9544, "BUSINESS_HOURS": 0.32, "...": 0.0 },
  "slots": { "orderId": "12345" }
}
```

| フィールド | 意味 |
|---|---|
| `intent` | 最良意図。閾値未満なら `"UNKNOWN"` |
| `confidence` | 最良意図のコサイン類似度（0.0–1.0） |
| `ranking` | 各意図 → 最高スコア（降順） |
| `slots` | 抽出されたパラメータ（正規表現名 → 値） |

DataWeave: `read(payload, "application/json").intent`。

---

## 辞書の作り方

挙動は**3つのテキストファイル**で定義する。`dictionaryDir` 指定時は、ファイルを編集してアプリを再デプロイするだけ（コア/モジュールの再ビルド不要）。

### `intents.tsv` — 何をしたいか
書式: `意図名<TAB>発話例`（1行1例。空行と `#` は無視）。

```
ORDER_STATUS	注文の状況を知りたい
ORDER_STATUS	配送はいつ届きますか
CANCEL	注文をキャンセルしたい
```

コツ:
- **1意図あたり4〜6例以上**、言い回し（敬体/常体/言い換え）をばらけさせる。文字バイグラム照合なので語彙の多様性が効く。
- 意図名は後続処理で使う**契約キー**。固定して使う。
- テストで使う文そのものは辞書に入れない（過学習防止）。

### `slots.tsv` — 取り出すパラメータ
書式: `スロット名<TAB>正規表現`。キーは**開発者が命名**、値は文から抽出。

```
orderId	注文\s*(\d{4,})
quantity	(\d+)\s*個
date	(\d{4}[/-]\d{1,2}[/-]\d{1,2})
```

- キャプチャグループ `(...)` があればgroup(1)、無ければマッチ全体が値。
- 形が決まったもの（ID・日付・金額・電話・メール）に最適。商品名・人名・地名など開放的な固有表現は、辞書マッチや統計的NER（OpenNLP `NameFinderME` 等）を `SlotExtractor` として追加する。

### `intent.properties` — 閾値
```
threshold=0.15
```
誤検出が多ければ上げる、正しい発話が `UNKNOWN` に落ちるなら下げる。

---

## できること・できないこと

**得意:** 限定的で安定した意図セット／高速・無料・決定論的・大量処理／オフライン・データ越境なし／説明可能／構造化スロット抽出。

**そのままでは不得意:** 任意の言い回しへの頑健性、「明日」などの相対表現や漢数字、開放的な固有表現。これらは**LLMなしでも**、正規化レイヤ（相対日付→絶対日付）、辞書（gazetteer）、統計的NERで対応できる。`SlotExtractor` インターフェースで `正規表現 → 辞書 → NER` を多段に重ねて結果をマージできる。

**推奨する本番構成（ハイブリッド）:**
1. 正規表現で高信頼・定型を即決（無料・確定）。
2. TF‑IDF retrieval で主要意図を分類。`confidence` が高ければ採用。
3. 低信頼 / `UNKNOWN` の少数だけLLMに回す。社外に出せないケースは記号側で完結。

---

## ビルド上の注意

親POM `org.mule.extensions:mule-modules-parent:1.9.8` 由来の制約に本リポジトリは対処済み:

1. 親POMの groupId は `org.mule.extensions`（`org.mule.modules` ではない）。
2. コンパイラ encoding が ISO‑8859‑1 固定 → **モジュールのJavaソースはASCII限定**（日本語は実行時に読む `*.tsv` 辞書側にUTF‑8で持たせる）。
3. CPALライセンスヘッダ検査が `compile` に紐づく → `<license.skip>true</license.skip>` で無効化。
4. `Map`/`Object` 戻り値は `OutputResolver` を要求 → `classify` は **JSON文字列**を返す。
5. `@JavaVersionSupport({JAVA_17, JAVA_21})` 必須（無いと `supportedJavaVersions` が 1.8/11 になり、Java17ビルドが `extension-validate` で弾かれる）。これには `org.mule.sdk:mule-sdk-api` を `provided` 依存に追加。

---

## 動作確認状況

- 3プロジェクトとも `mvn clean install` 成功（Java 17）。
- **Mule EE standalone 4.11.3** にデプロイし、HTTP（`POST /classify`、port 8099）でエンドツーエンド確認済み。例:
  - `注文12345を3個、2026-06-10に 090-1234-5678 へ配送して` → `SHIPPING` + `{orderId, quantity, date, phone}`（HTTP 200）
  - `注文55667の配送状況を教えて` → `ORDER_STATUS` + `{orderId}`（HTTP 200）
  - `今日の天気はどうですか` → `UNKNOWN`（HTTP 200）

---

## ライセンス

未設定。配布前に `LICENSE` ファイルを追加してください。
