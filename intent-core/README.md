# intent-core — 純Java意図推論コアライブラリ

外部依存ゼロ（JDK 17標準ライブラリのみ）で動作する、日本語対応のretrieval-based意図推論ライブラリ。
LLMを一切使わず、TF-IDFコサイン類似度のみで意図分類とスロット抽出を行う。

---

## クイックスタート（javac単体）

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# コンパイル
rm -rf build && mkdir -p build
"$JAVA_HOME/bin/javac" -encoding UTF-8 -d build $(find src/main/java -name '*.java')

# デモ実行（-cp に src/main/resources を含めてクラスパスリソースを読み込む）
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "build:src/main/resources" com.demo.intent.Demo
```

Mavenを使う場合:

```bash
mvn clean package
java -Dfile.encoding=UTF-8 -cp target/intent-core-0.1.0.jar com.demo.intent.Demo
```

---

## 辞書ファイルの編集方法

辞書・スロット定義・閾値はすべて `src/main/resources/` 以下の外部ファイルで管理する。
コードを変更せずに意図の追加・例文の変更・スロット追加が可能。

### `intents.tsv` — 意図例の追加・変更

```
# 書式: <INTENT>\t<発話例>
# '#' 始まり行・空行は無視される
ORDER_STATUS	注文の状況を知りたい
ORDER_STATUS	配送はいつ届きますか
CANCEL	注文をキャンセルしたい
```

- 1行 = 1例。タブ区切りで意図ラベルと発話例を指定する
- 既存の意図例を増やすと分類精度が向上する（各意図4〜6例以上を推奨）
- 新しい意図を追加する場合は新しいラベルで行を追加するだけでよい
- テストクエリそのものは含めない（過学習防止）

### `slots.tsv` — スロット正規表現の追加・変更

```
# 書式: <slotName>\t<正規表現>
orderId	(\d{4,})
date	(\d{4}[/-]\d{1,2}[/-]\d{1,2})
```

- キャプチャグループ `(...)` を使うと group(1) が値になる
- キャプチャグループなしの場合はマッチ全体が値になる

### `intent.properties` — 閾値等の設定

```properties
threshold=0.15
```

- `threshold`: コサイン類似度の最低閾値。これ未満のスコアは `UNKNOWN` と判定
- バイグラムTF-IDFでは通常 0.05〜0.60 の範囲に分布する
- 誤検出が多い場合は値を上げる（例: 0.20）、正しく分類されない場合は下げる（例: 0.10）

### `synonyms.tsv` — 同義語正規化（任意）

推論前にテキストを正規化する小さなドメイン同義語辞書。`dictionaryDir` / クラスパスに置けば自動で読まれる。

```
# 書式: <canonical>\t<variant1>,<variant2>,...
出庫	払い出し,払出,ピッキング
有給休暇	休み,お休み,有休
秘密保持	機密保持,守秘義務
```

- 入力中の variant を canonical に置換してから照合するので、辞書に無い言い回しに強くなる。
- **canonical は intents.tsv の実語彙に合わせる**こと（例文が `秘密保持契約` を使うなら canonical も `秘密保持`）。
- 置換は単純な部分文字列置換（variant の長い順）。ドメインに閉じた語彙で使う。
- 実装: `SynonymNormalizer` + `NormalizingTokenizer`（例文・クエリの双方が同一正規化を通る）。

### コードからの利用

```java
// クラスパスから読む（JAR/Mule 運用）
IntentConfig config = IntentConfigLoader.loadFromClasspath(
    "/intents.tsv", "/slots.tsv", "/intent.properties");

// ディレクトリから読む（外部ファイル運用）
IntentConfig config = IntentConfigLoader.loadFromDir(Path.of("/etc/intent"));

// 分類器を構築
Tokenizer tokenizer = new CjkBigramTokenizer();
IntentClassifier classifier = IntentClassifier.fromConfig(config, tokenizer);

// 推論
IntentResult result = classifier.classify("注文12345の配送状況を教えてください");
System.out.println(result.toJson());
```

---

## アルゴリズム概要

### 1. CjkBigramTokenizer — テキスト → トークン列

| 文字種 | 処理 |
|-------|------|
| ASCII英数字 | 連続ランを1トークン（小文字化） |
| CJK (ひらがな/カタカナ/漢字) | オーバーラップするバイグラムを生成。1文字ならユニグラム |
| 空白・句読点 | トークン境界として破棄 |

例: `「注文の状況」` → `["注文", "文の", "の状", "状況"]`

### 2. IntentModel — TF-IDF + L2正規化

1. **TF（生ターム頻度）**: 各サンプル例のトークン出現回数
2. **IDF（逆文書頻度）**: `idf = log((N+1)/(df+1)) + 1`（スムージング付き）
3. **TF-IDFベクトル**: `tf × idf` で疎ベクトル（`Map<String,Double>`）を構築
4. **L2正規化**: 各ベクトルを単位ベクトル化 → コサイン類似度が内積だけで計算できる

### 3. IntentClassifier — コサイン類似度で分類

1. クエリをトークン化 → モデルのIDFでTF-IDF単位ベクトルを構築
2. 全サンプル例ベクトルとの内積（= コサイン類似度）を計算
3. 意図ごとに最大スコアを集約 → ランキング生成
4. 最良スコア < `threshold` (デフォルト 0.15) なら `UNKNOWN`

### 4. RegexSlotExtractor — スロット抽出

`Map<String, Pattern>` を受け取り、各パターンの最初のキャプチャグループ（なければ全体マッチ）を抽出。

---

## 主要クラス

| クラス/インタフェース | 役割 |
|---------------------|------|
| `Tokenizer` | トークン化インタフェース |
| `CjkBigramTokenizer` | CJKバイグラム + ASCII英数字ユニグラム |
| `IntentExample` | record(intent, text) — サンプル例 |
| `IntentModel` | TF-IDF単位ベクトルの構築・保持 |
| `IntentResult` | 分類結果（intent/confidence/ranking/slots） |
| `SlotExtractor` | スロット抽出インタフェース |
| `RegexSlotExtractor` | 正規表現ベースのスロット抽出 |
| `IntentClassifier` | 分類器本体 |

---

## 使用例

```java
// サンプル例を定義
List<IntentExample> examples = List.of(
    new IntentExample("ORDER_STATUS", "注文の状況を知りたい"),
    new IntentExample("ORDER_STATUS", "配送はいつ届きますか"),
    new IntentExample("CANCEL",       "注文をキャンセルしたい")
    // ...
);

// モデル構築
Tokenizer tokenizer = new CjkBigramTokenizer();
IntentModel model = new IntentModel(examples, tokenizer);

// スロット抽出器（省略可）
Map<String, Pattern> slotPatterns = Map.of("orderId", Pattern.compile("(\\d{4,})"));
SlotExtractor slotExtractor = new RegexSlotExtractor(slotPatterns);

// 分類器
IntentClassifier classifier = new IntentClassifier(model, 0.15, slotExtractor);

// 推論
IntentResult result = classifier.classify("注文12345の配送状況を教えてください");
System.out.println(result.toJson());
// => { "intent": "ORDER_STATUS", "confidence": 0.xxxx, "slots": {"orderId": "12345"}, ... }
```

---

## 次の拡張方針

1. **学習データ拡充**: 各カテゴリ10〜20例に増やすことで精度向上が見込める。特にバイグラムの重なりが薄いカテゴリへの対策。

2. **N-gram拡張**: バイグラムに加えてユニグラムやトライグラムを混合し、単語単位の特徴も捉える（BM25への移行も検討）。

3. **形態素解析の統合**: 将来的に外部JARを許容する場合、Kuromoji等を差し込んで `Tokenizer` を実装するだけで置き換え可能な設計。

4. **Mule統合**: `IntentClassifier` をMule Javaコンポーネントとして登録し、DataWeaveでスロット変換する想定（pom.xmlは依存追加の準備済み）。

5. **閾値の動的チューニング**: 意図ごとにthresholdを変えるマルチ閾値化、または calibration（Platt scaling相当）を実装。

6. **永続化**: `IntentModel` のIDFとベクトルをJSONやバイナリにシリアライズして起動時ロードを高速化。
