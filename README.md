# mule-symbolic-inference-module

**LLMを使わない**、決定論的・プロセス内・無料の「自然言語 → 意図(intent) + パラメータ(slot)」推論を
MuleSoft Mule 4 のフローから呼べる **Mule SDK 拡張（カスタムモジュール）**。

retrieval-based + TF-IDF コサイン類似度で意図を分類し、正規表現でスロット（注文番号・日付・数量など）を抽出する。
外部API・GPU・ネットワーク不要で、レイテンシ・コスト・プライバシー・監査性に強い。

```
自然言語:  「注文12345を3個、2026-06-10に配送して」
   ↓ <infer:classify>
結果(JSON): { "intent": "SHIPPING",
             "slots": { "orderId": "12345", "quantity": "3", "date": "2026-06-10" } }
```

## リポジトリ構成

| ディレクトリ | 内容 |
|---|---|
| [`intent-core/`](intent-core/) | 外部依存ゼロの純Java推論コア（TF-IDFコサイン, CJKバイグラム, データ駆動辞書）。Muleに依存しない |
| [`mule-symbolic-inference-module/`](mule-symbolic-inference-module/) | 上記コアをラップした Mule SDK 拡張。`<infer:classify>` を提供 |
| [`examples/intent-sample-app/`](examples/intent-sample-app/) | モジュールを使う実行可能なサンプルMuleアプリ（HTTP `POST /classify`） |

## ビルド（Java 17 必須）

```bash
export JAVA_HOME=/path/to/jdk-17
# 1) コア
(cd intent-core && mvn clean install -DskipTests)
# 2) Muleモジュール
(cd mule-symbolic-inference-module && mvn clean install -DskipTests)
# 3) サンプルアプリ
(cd examples/intent-sample-app && mvn clean package -DskipTests -DattachMuleSources)
```

## フローでの使い方

```xml
<infer:config name="infer-config"/>          <!-- 同梱デフォルト辞書。dictionaryDir で外部辞書も可 -->

<flow name="classify-flow">
    <http:listener config-ref="http-listener-config" path="/classify"/>
    <infer:classify config-ref="infer-config" text="#[payload]"/>   <!-- application/json を返す -->
</flow>
```

## 辞書（意図・スロットの定義）

3つのテキストファイルだけで定義する。コード変更・再ビルド不要（`dictionaryDir` 指定時）。

- `intents.tsv` … `意図名<TAB>発話例`（1意図あたり4〜6例。言い換えを多めに）
- `slots.tsv` … `スロット名<TAB>正規表現`（`(...)` の中身が値）
- `intent.properties` … `threshold=0.15`（UNKNOWN判定の閾値）

詳細は各サブディレクトリの README を参照。

## 特徴と適性

- **得意**: 限定された意図セットを高速・無料・決定論的に処理。オフライン／データ越境なし／監査可能。
- **不得意**: 任意の言い回しへの頑健性や複雑な含意の解釈は汎用LLMに劣る。
- **推奨**: 高信頼を本モジュールで即決し、低信頼(`UNKNOWN`/低confidence)のみLLMへフォールバックする **ハイブリッド** 構成。

## ライセンス / 動作要件

- Mule 4.9.8+（CE/EE 両対応）、`supportedJavaVersions=["17","21"]`。
- Mule EE standalone 4.11.3 にデプロイし、HTTP経由の意図＋スロット抽出を実機確認済み。
