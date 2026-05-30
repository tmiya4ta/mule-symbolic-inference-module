# Domain dictionaries (production‑grade samples)

業務ドメインごとの**プロダクションレベルのサンプル辞書**。各ドメインは独立した辞書ディレクトリで、
`<infer:config dictionaryDir="...">` でそのまま使える（コア/モジュールの再ビルド不要）。

| Domain | Dir | Intents | Examples | Slots |
|---|---|---:|---:|---:|
| 在庫管理 (Inventory) | [`inventory/`](inventory/) | 13 | 147 | 8 |
| 調達・購買 (Procurement) | [`procurement/`](procurement/) | 14 | 154 | 7 |
| 法務 (Legal) | [`legal/`](legal/) | 13 | 147 | 8 |
| インシデント管理 (Incident / ITSM) | [`incident/`](incident/) | 13 | 143 | 8 |
| 人事 (HR) | [`hr/`](hr/) | 14 | 160 | 8 |
| **合計** | | **67** | **751** | **39** |

各ディレクトリは `intents.tsv` / `slots.tsv` / `intent.properties` の3点セット（書式はルートの README を参照）。
各意図は **約11例**（敬体/常体/口語/言い換え）で構成。

## 使い方

```xml
<!-- 例: インシデント管理ドメインを使う -->
<infer:config name="infer-config"
              dictionaryDir="/abs/path/to/examples/dictionaries/incident"/>

<flow name="classify-flow">
    <http:listener config-ref="http-listener-config" path="/classify"/>
    <infer:classify config-ref="infer-config" text="#[payload]"/>
</flow>
```

ローカルで素早く試す（Mule不要、intent-core で直接）:

```bash
export JAVA_HOME=/path/to/jdk-17
CORE=~/.m2/repository/com/demo/intent-core/0.1.0/intent-core-0.1.0.jar
# 簡単な確認用クラスを書いて loadFromDir(dir) → classify(text) を呼ぶ
```

## 各ドメインの意図（intent）一覧

- **inventory**: INVENTORY_CHECK / RECEIVE_STOCK / INCOMING_SCHEDULE / ISSUE_STOCK / STOCK_ADJUSTMENT / WAREHOUSE_TRANSFER / STOCK_RESERVATION / REORDER_ALERT / LOCATION_INQUIRY / LOT_EXPIRY_CHECK / RETURN_RECEIVE / INVENTORY_REPORT / DISPOSAL
- **procurement**: CREATE_PURCHASE_REQUEST / CREATE_PURCHASE_ORDER / CHECK_ORDER_STATUS / RECEIVE_GOODS / REGISTER_SUPPLIER / SEARCH_SUPPLIER / REQUEST_FOR_QUOTATION / COMPARE_QUOTATIONS / RENEW_CONTRACT / MATCH_INVOICE / CHECK_DELIVERY_DATE / CHECK_BUDGET / REQUEST_APPROVAL / REQUEST_PAYMENT
- **legal**: CONTRACT_REVIEW / CONTRACT_DRAFT / NDA_REQUEST / CONTRACT_STATUS / LEGAL_CONSULTATION / COMPLIANCE_CHECK / IP_TRADEMARK_PATENT / DISPUTE_LITIGATION / PRIVACY_GDPR / INTERNAL_POLICY / CONTRACT_RENEWAL / SEAL_APPROVAL / CREDIT_CHECK
- **incident**: REPORT_INCIDENT / CHECK_INCIDENT_STATUS / ESCALATE_INCIDENT / RESOLVE_INCIDENT / ASSIGN_INCIDENT / ADD_COMMENT / CHANGE_PRIORITY / SET_SEVERITY / REQUEST_RCA / CREATE_PROBLEM / REPORT_OUTAGE / CHECK_SLA / REOPEN_INCIDENT
- **hr**: LEAVE_REQUEST / LEAVE_BALANCE_INQUIRY / PAYROLL_INQUIRY / EXPENSE_REIMBURSEMENT / ATTENDANCE_CORRECTION / ONBOARDING / OFFBOARDING / BENEFITS_INQUIRY / TRAINING_ENROLLMENT / PERFORMANCE_REVIEW / CERTIFICATE_REQUEST / PERSONAL_INFO_UPDATE / TRANSFER_REQUEST / RECRUITMENT_REQUEST

## 検証結果

intent-core エンジン（Mule モジュールが内部で使うものと同一）で、各ドメイン代表クエリ8件・計**40件**を分類して確認:

```
inventory : 8/8   procurement : 8/8   legal : 8/8   incident : 8/8   hr : 8/8
合計 40/40 (intent 一致) — スロット抽出も期待どおり
```

## プロダクション利用にあたっての注意

- これらは**網羅的なスターター辞書**であり、そのまま運用にも耐えるが、最終的には
  **実際のユーザ発話ログ**で `intents.tsv` を増強するとさらに精度が上がる。
- 意図名は後続システムへの**契約キー**。組織のワークフローに合わせて取捨選択・改名する。
- スロットの正規表現は社内の **ID 体系・日付/金額表記**に合わせて調整する。
- 近縁意図（例: 在庫照会↔入荷予定、契約状況↔訴訟対応）は語彙が重なりやすい。
  誤分類が出たら、その意図に**識別語彙を含む例**を数件足すのが効く。
- 開放的な固有表現（人名・会社名・サービス名）は正規表現の限界がある。必要に応じて
  辞書(gazetteer)マッチや OpenNLP NER を `SlotExtractor` として追加する。
