package com.demo.intent;

import java.util.*;

/**
 * TF-IDFベースの意図モデル。
 *
 * <p>構築フロー:
 * <ol>
 *   <li>各例のTF（生のターム頻度）を計算。</li>
 *   <li>全例にわたるDF（ドキュメント頻度）を集計し IDF を計算:
 *       {@code idf = log((N+1)/(df+1)) + 1}（スムージング付き、N=例の総数）</li>
 *   <li>TF × IDF でTF-IDFベクトルを構築し、L2正規化して単位ベクトル化。</li>
 * </ol>
 *
 * <p>クエリ時: モデル構築時のIDFを使って同様にTF-IDF単位ベクトルを生成。
 * コサイン類似度 = 単位ベクトル同士の内積。
 */
public class IntentModel {

    /** 例の配列（インデックスはベクトルと一致） */
    private final IntentExample[] examples;

    /** 各例のTF-IDF単位ベクトル（疎表現: term -> weight） */
    private final List<Map<String, Double>> exampleVectors;

    /** IDF辞書: term -> idf値 */
    private final Map<String, Double> idf;

    /** トークナイザ */
    private final Tokenizer tokenizer;

    /**
     * @param examples  サンプル例のリスト
     * @param tokenizer トークナイザ
     */
    public IntentModel(List<IntentExample> examples, Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.examples = examples.toArray(new IntentExample[0]);
        int n = this.examples.length;

        // Step 1: 各例のTFを計算
        List<Map<String, Integer>> tfList = new ArrayList<>(n);
        for (IntentExample ex : this.examples) {
            tfList.add(computeTf(tokenizer.tokenize(ex.text())));
        }

        // Step 2: DF集計 → IDF計算
        Map<String, Integer> df = new HashMap<>();
        for (Map<String, Integer> tf : tfList) {
            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
        this.idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : df.entrySet()) {
            double idfVal = Math.log((double)(n + 1) / (entry.getValue() + 1)) + 1.0;
            this.idf.put(entry.getKey(), idfVal);
        }

        // Step 3: TF-IDFベクトル構築 + L2正規化
        this.exampleVectors = new ArrayList<>(n);
        for (Map<String, Integer> tf : tfList) {
            Map<String, Double> vec = buildTfIdfVector(tf, this.idf);
            l2Normalize(vec);
            exampleVectors.add(vec);
        }
    }

    /**
     * トークンリストからTF（生のターム頻度）を計算する。
     */
    private static Map<String, Integer> computeTf(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }

    /**
     * TFとIDFからTF-IDFベクトルを構築する（L2正規化前）。
     */
    private static Map<String, Double> buildTfIdfVector(
            Map<String, Integer> tf, Map<String, Double> idf) {
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            Double idfVal = idf.get(e.getKey());
            if (idfVal != null) {
                vec.put(e.getKey(), e.getValue() * idfVal);
            }
        }
        return vec;
    }

    /**
     * ベクトルをL2正規化（インプレース）する。ゼロベクトルはそのまま。
     */
    static void l2Normalize(Map<String, Double> vec) {
        double norm = 0.0;
        for (double v : vec.values()) {
            norm += v * v;
        }
        if (norm == 0.0) return;
        double invNorm = 1.0 / Math.sqrt(norm);
        vec.replaceAll((k, v) -> v * invNorm);
    }

    /**
     * クエリテキストをTF-IDF単位ベクトルに変換する。
     * モデル構築時のIDFを使用。モデルに存在しないタームは無視。
     *
     * @param tokens クエリのトークンリスト
     * @return L2正規化済みTF-IDFベクトル（未知タームはゼロ）
     */
    public Map<String, Double> buildQueryVector(List<String> tokens) {
        Map<String, Integer> tf = computeTf(tokens);
        Map<String, Double> vec = buildTfIdfVector(tf, idf);
        l2Normalize(vec);
        return vec;
    }

    /**
     * 2つの単位ベクトルのコサイン類似度（= 内積）を計算する。
     * 共通キーのみ走査するため効率的。
     */
    public static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        // 小さい方のベクトルでループする（効率化）
        Map<String, Double> smaller = a.size() <= b.size() ? a : b;
        Map<String, Double> larger  = a.size() <= b.size() ? b : a;
        double dot = 0.0;
        for (Map.Entry<String, Double> e : smaller.entrySet()) {
            Double bVal = larger.get(e.getKey());
            if (bVal != null) {
                dot += e.getValue() * bVal;
            }
        }
        return dot;
    }

    /** @return 例の配列 */
    public IntentExample[] getExamples() { return examples; }

    /** @return 例のTF-IDF単位ベクトルリスト */
    public List<Map<String, Double>> getExampleVectors() { return exampleVectors; }

    /** @return トークナイザ */
    public Tokenizer getTokenizer() { return tokenizer; }
}
