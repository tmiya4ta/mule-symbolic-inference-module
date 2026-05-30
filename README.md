# mule-symbolic-inference-module

**English** | [日本語](README.ja.md)

A **MuleSoft Mule 4 SDK module that performs intent + parameter inference on natural language *without using an LLM***.
It classifies the user's intent with retrieval-based TF‑IDF cosine similarity and extracts parameters (slots) with regular expressions — fully **deterministic, in‑process, and free of any external API, GPU, or network call**.

```
Input  (natural language):  "注文12345を3個、2026-06-10に配送して"
   │  <infer:classify>
   ▼
Output (JSON):  {
  "intent": "SHIPPING",
  "confidence": 0.50,
  "ranking": { "SHIPPING": 0.50, "RETURN": 0.17, ... },
  "slots":   { "orderId": "12345", "quantity": "3", "date": "2026-06-10" }
}
```

Because everything runs inside the JVM, it shines where **latency, cost, determinism, privacy, and auditability** matter — and it composes well with an LLM as a fallback for the hard cases (see [Capabilities & limitations](#capabilities--limitations)).

---

## Table of contents

- [Why no LLM?](#why-no-llm)
- [Repository layout](#repository-layout)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Build](#build)
- [Use it in a Mule flow](#use-it-in-a-mule-flow)
- [Configuration](#configuration)
- [Output schema](#output-schema)
- [Authoring the dictionary](#authoring-the-dictionary)
- [Capabilities & limitations](#capabilities--limitations)
- [Build notes / gotchas](#build-notes--gotchas)
- [Verified status](#verified-status)
- [License](#license)

---

## Why no LLM?

| Dimension | This module (TF‑IDF + regex) | General LLM |
|---|---|---|
| Latency | Sub‑millisecond, in‑process | Network round‑trip (100s ms) |
| Cost | Free per call | Per‑token billing |
| Determinism / reproducibility | Same input → same output, auditable | Stochastic |
| Privacy / data residency | Nothing leaves the JVM/VPC | Utterance sent to an external API |
| Availability | Offline, no API key, no rate limit | External dependency |
| Explainability | Cosine scores + which regex matched | Black box |
| Open‑ended accuracy | Lower; depends on the dictionary | Higher; robust to paraphrase |

The sweet spot is a **bounded, well‑defined set of intents** processed at high volume. For maximum robustness, run this for the high‑confidence majority and **fall back to an LLM only for low‑confidence / `UNKNOWN`** results.

---

## Repository layout

| Path | Description |
|---|---|
| [`intent-core/`](intent-core/) | **Zero‑dependency, pure‑Java inference core.** TF‑IDF cosine similarity, CJK bigram tokenizer, file‑driven dictionary. No Mule dependency, so it is unit‑testable on its own. |
| [`mule-symbolic-inference-module/`](mule-symbolic-inference-module/) | **The Mule SDK extension** that wraps the core and exposes the `<infer:classify>` operation and `<infer:config>` configuration. |
| [`examples/intent-sample-app/`](examples/intent-sample-app/) | A runnable sample Mule application exposing `POST /classify` over HTTP, using an extended dictionary (adds a `SHIPPING` intent and `quantity` / `phone` / `email` slots). |
| [`examples/dictionaries/`](examples/dictionaries/) | **Production‑grade sample dictionaries** for 5 business domains — Inventory, Procurement, Legal, Incident/ITSM, HR (67 intents, 751 example utterances, 39 slot patterns). Point `dictionaryDir` at any of them. |

---

## How it works

```
text ──► Tokenizer ──► TF-IDF query vector ──► cosine vs. each example
                                                     │
                                  per-intent max score, ranked
                                                     │
                            best < threshold ? "UNKNOWN" : best intent
                                                     │
text ──► SlotExtractor (regex) ──────────────────────┴──► { intent, confidence, ranking, slots }
```

1. **Tokenization** (`CjkBigramTokenizer`) — ASCII runs become lowercased word tokens; CJK runs (Hiragana/Katakana/Kanji) become **overlapping character bigrams**. Common polite/question endings are dropped as stop‑tokens so they do not dominate the similarity.
2. **Vectorization** (`IntentModel`) — each dictionary example becomes a TF‑IDF vector (`idf = log((N+1)/(df+1)) + 1`), L2‑normalized so cosine similarity is a plain dot product.
3. **Classification** (`IntentClassifier`) — the query vector is compared to every example; the best score **per intent** is kept and ranked. If the top score is below `threshold`, the intent becomes `UNKNOWN`.
4. **Slot extraction** (`RegexSlotExtractor`) — each configured pattern is matched against the raw text; the first capture group (or the whole match) becomes the value. Intent and slots are **independent**, so improving one does not affect the other.

Everything is **data‑driven**: intents, slots, and the threshold live in plain text files, so behavior changes without recompiling (see [Authoring the dictionary](#authoring-the-dictionary)).

---

## Requirements

- **Java 17** to build (the core uses records). The module declares `supportedJavaVersions = ["17","21"]`.
- **Mule 4.9.8+** runtime (Community or Enterprise). `requiredProduct = MULE`.
- Maven 3.9+.

---

## Build

Build in dependency order. The core must be installed to the local Maven repo first.

```bash
export JAVA_HOME=/path/to/jdk-17

# 1) Pure-Java core
(cd intent-core && mvn clean install -DskipTests)

# 2) Mule SDK module  → produces mule-symbolic-inference-module-0.1.0-mule-plugin.jar
(cd mule-symbolic-inference-module && mvn clean install -DskipTests)

# 3) Sample application
(cd examples/intent-sample-app && mvn clean package -DskipTests -DattachMuleSources)
```

Coordinates of the module: `com.demo:mule-symbolic-inference-module:0.1.0` (classifier `mule-plugin`).

---

## Use it in a Mule flow

Add the dependency to your Mule application's `pom.xml`:

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>mule-symbolic-inference-module</artifactId>
    <version>0.1.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

A minimal flow — `classify` returns `application/json`, so it can be the HTTP response directly:

```xml
<infer:config name="infer-config"/>            <!-- bundled default dictionary -->

<flow name="classify-flow">
    <http:listener config-ref="http-listener-config" path="/classify"/>
    <infer:classify config-ref="infer-config" text="#[payload]"/>
</flow>
```

Call it:

```bash
curl -X POST http://localhost:8081/classify \
     -H 'Content-Type: text/plain; charset=UTF-8' \
     -d '注文12345の配送状況を教えて'
# => {"intent":"ORDER_STATUS","confidence":0.95,"ranking":{...},"slots":{"orderId":"12345"}}
```

---

## Configuration

`<infer:config>` parameters:

| Parameter | Required | Description |
|---|---|---|
| `dictionaryDir` | optional | Absolute path to a directory holding `intents.tsv` / `slots.tsv` / `intent.properties`. If empty, the dictionary bundled inside the module is used. |
| `thresholdOverride` | optional | Overrides the `UNKNOWN` threshold. If empty, the value from `intent.properties` (default `0.15`) is used. |

The classifier (TF‑IDF model) is built **once** when the configuration initializes (`Initialisable#initialise`) and reused for every request — there is no per‑request rebuild.

---

## Output schema

`<infer:classify>` returns a JSON document:

```json
{
  "intent": "ORDER_STATUS",
  "confidence": 0.9544,
  "ranking": { "ORDER_STATUS": 0.9544, "BUSINESS_HOURS": 0.32, "...": 0.0 },
  "slots": { "orderId": "12345" }
}
```

| Field | Meaning |
|---|---|
| `intent` | Best‑matching intent, or `"UNKNOWN"` if below threshold |
| `confidence` | Cosine‑similarity score (0.0–1.0) of the best intent |
| `ranking` | Every intent → its best score, descending |
| `slots` | Extracted parameters (regex name → value) |

In DataWeave: `read(payload, "application/json").intent`.

---

## Authoring the dictionary

Behavior is defined by **three text files**. With `dictionaryDir` set, you edit them and redeploy the app — no code change, no rebuild of the core/module.

### `intents.tsv` — what the user wants to do
Format: `INTENT_NAME<TAB>example utterance` (one example per line; blank lines and `#` comments ignored).

```
ORDER_STATUS	注文の状況を知りたい
ORDER_STATUS	配送はいつ届きますか
CANCEL	注文をキャンセルしたい
```

Tips:
- Provide **4–6+ examples per intent**, varying the phrasing (polite/casual/synonyms). Because matching uses character bigrams, lexical variety matters.
- The intent name is your **contract key** (used downstream) — keep it stable.
- Do **not** put your test queries verbatim into the dictionary (avoids overfitting).

### `slots.tsv` — the parameters to pull out
Format: `slotName<TAB>regular expression`. The key is **chosen by you**; the value comes from the text.

```
orderId	注文\s*(\d{4,})
quantity	(\d+)\s*個
date	(\d{4}[/-]\d{1,2}[/-]\d{1,2})
```

- If the regex has a capture group `(...)`, group 1 is the value; otherwise the whole match is used.
- Great for structured tokens (IDs, dates, amounts, phone, email). For open‑ended entities (product/person/place names) add a gazetteer match or a statistical NER (e.g. OpenNLP `NameFinderME`) as an additional `SlotExtractor`.

### `intent.properties` — the threshold
```
threshold=0.15
```
Raise it if you get false positives; lower it if valid utterances fall through to `UNKNOWN`.

---

## Capabilities & limitations

**Good at:** a bounded, stable set of intents; high‑volume, low‑latency, zero‑cost, deterministic classification; offline / no data egress; explainable decisions; structured slot extraction.

**Not good at (without help):** robustness to arbitrary phrasing; implicit/relative parameters such as "tomorrow" or kanji numerals; open‑ended entity types. These are addressable **without an LLM** via a normalization layer (relative date → absolute), gazetteers, or statistical NER — and the `SlotExtractor` interface lets you chain `regex → dictionary → NER` and merge the results.

**Recommended production shape (hybrid):**
1. Regex resolves high‑confidence, well‑formed inputs instantly (free, deterministic).
2. TF‑IDF retrieval classifies the main intents; accept when `confidence` is high.
3. Route only the low‑confidence / `UNKNOWN` tail to an LLM. Cases that must not leave your network stay on the symbolic path.

---

## Build notes / gotchas

The module inherits `org.mule.extensions:mule-modules-parent:1.9.8`, which imposes a few constraints handled in this repo:

1. The parent's groupId is `org.mule.extensions` (not `org.mule.modules`).
2. The compiler encoding is fixed to ISO‑8859‑1, so **the module's Java sources are ASCII‑only** (Japanese lives in the runtime `*.tsv` dictionary, read as UTF‑8).
3. A CPAL license‑header check is bound to `compile` → disabled with `<license.skip>true</license.skip>`.
4. `Map`/`Object` return types require an `OutputResolver`, so `classify` returns a **JSON string** instead.
5. `@JavaVersionSupport({JAVA_17, JAVA_21})` is required (otherwise `supportedJavaVersions` defaults to 1.8/11 and `extension-validate` rejects a Java‑17 build). This needs `org.mule.sdk:mule-sdk-api` as a `provided` dependency.

---

## Verified status

- `mvn clean install` succeeds for all three projects (Java 17).
- Deployed to **Mule EE standalone 4.11.3** and exercised end‑to‑end over HTTP (`POST /classify`, port 8099). Sample results:
  - `注文12345を3個、2026-06-10に 090-1234-5678 へ配送して` → `SHIPPING` + `{orderId, quantity, date, phone}` (HTTP 200)
  - `注文55667の配送状況を教えて` → `ORDER_STATUS` + `{orderId}` (HTTP 200)
  - `今日の天気はどうですか` → `UNKNOWN` (HTTP 200)

---

## License

Not yet specified. Add a `LICENSE` file before distributing.
