# intent-sample-app — intent-connector サンプルMuleアプリ

`intent-connector`（LLM不使用の意図推論）を使い、HTTP で
**自然言語 → 意図 + 複数パラメータ（スロット）** を返すサンプル。

- エンドポイント: `POST /classify`（port 8099、ボディに発話テキスト）
- 拡張辞書を `dictionaryDir`（このフォルダの `dictionary/`）で外部指定
  - 意図: ORDER_STATUS / CANCEL / RETURN / BUSINESS_HOURS / STOCK_CHECK / **SHIPPING**
  - スロット: `orderId` / `date` / `quantity` / `phone` / `email`

## ビルド
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# 事前に intent-core と intent-connector を mvn install 済みであること
mvn -s ~/.m2/settings.xml clean package -DskipTests -DattachMuleSources
```

## デプロイ（Mule standalone）
```bash
MH=/home/myst/srv/mule-enterprise-standalone-4.11.3
cp target/intent-sample-app-0.1.0-mule-application.jar "$MH/apps/"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 "$MH/bin/mule" start
```

## 試す
```bash
curl -X POST localhost:8099/classify -d '注文12345を3個、2026-06-10に 090-1234-5678 へ配送して'
# => { "intent":"SHIPPING", "slots":{"orderId":"12345","quantity":"3","date":"2026-06-10","phone":"090-1234-5678"} ... }
```
