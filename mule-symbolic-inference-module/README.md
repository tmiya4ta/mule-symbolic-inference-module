# mule-symbolic-inference-module — 記号的（LLM不使用）推論 Mule モジュール

`intent-core`（純Java・LLM不使用の意図推論）を Mule 4 フローから呼べる
**Mule SDK 拡張（カスタムモジュール）**としてラップしたもの。決定論的・プロセス内・無料で
「自然言語 → 意図 + パラメータ（スロット）」を推論する。

- 名前空間: `http://www.mulesoft.org/schema/mule/infer`（prefix `infer`）
- オペレーション: `<infer:classify text="..."/>`（`application/json` 文字列を返す）
- コンフィグ: `<infer:config .../>`
- artifactId: `com.demo:mule-symbolic-inference-module:0.1.0`
- 成果物: `mule-symbolic-inference-module-0.1.0-mule-plugin.jar`
- 実行要件: **Mule 4.9.8+（CE/EE 両対応）** / `supportedJavaVersions=["17","21"]`

---

## ビルド

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# 事前に intent-core を install 済みであること
cd ../intent-core && mvn -s ~/.m2/settings.xml clean install -DskipTests
cd ../mule-symbolic-inference-module && mvn -s ~/.m2/settings.xml clean install -DskipTests
```

## フローでの使い方

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>mule-symbolic-inference-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

```xml
<infer:config name="infer-config"/>            <!-- 同梱デフォルト辞書 -->

<flow name="classify-flow">
    <http:listener config-ref="http-listener-config" path="/classify"/>
    <infer:classify config-ref="infer-config" text="#[payload]"/>
</flow>
```

```bash
curl -X POST http://localhost:8099/classify -d '注文12345の配送状況を教えて'
# => {"intent":"ORDER_STATUS","confidence":0.95,"ranking":{...},"slots":{"orderId":"12345"}}
```

## 設定（`<infer:config>`）

| パラメータ | 必須 | 説明 |
|---|---|---|
| `dictionaryDir` | 任意 | 辞書ディレクトリ（`intents.tsv`/`slots.tsv`/`intent.properties`）。未指定なら同梱デフォルト |
| `thresholdOverride` | 任意 | UNKNOWN 判定閾値の上書き |

## ビルド上の注意（mule-modules-parent 1.9.8 由来）
1. 親POMの groupId は `org.mule.extensions`。
2. コンパイラ encoding が ISO-8859-1 固定 → Javaソースは ASCII 限定（日本語は tsv 辞書側）。
3. CPALライセンスヘッダ検査が compile に紐づく → `<license.skip>true</license.skip>`。
4. `Map`/`Object` 戻り値は不可（OutputResolver 要求）→ `classify` は JSON 文字列を返す。
5. `@JavaVersionSupport({JAVA_17, JAVA_21})` 必須（要 `mule-sdk-api` provided 依存）。
