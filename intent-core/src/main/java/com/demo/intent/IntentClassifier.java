package com.demo.intent;

import java.util.*;

/**
 * TF-IDFコサイン類似度による意図分類器。
 *
 * <p>分類フロー:
 * <ol>
 *   <li>入力テキストをトークン化し、TF-IDF単位ベクトルを生成。</li>
 *   <li>各サンプル例のベクトルとのコサイン類似度（= 内積）を計算。</li>
 *   <li>意図ごとに最大スコアを集約。</li>
 *   <li>最良スコアが閾値未満なら intent="UNKNOWN"。</li>
 * </ol>
 *
 * <p>構築方法:
 * <ul>
 *   <li>{@link #IntentClassifier(IntentModel, double, SlotExtractor)} — 直接指定</li>
 *   <li>{@link #fromConfig(IntentConfig, Tokenizer)} — {@link IntentConfig} 経由（データ駆動）</li>
 * </ul>
 */
public class IntentClassifier {

    private final IntentModel model;
    private final double threshold;
    private final SlotExtractor slotExtractor;

    /**
     * @param model         学習済みモデル
     * @param threshold     意図を採用するコサイン類似度の最低閾値（例: 0.15）
     * @param slotExtractor スロット抽出器（null可）
     */
    public IntentClassifier(IntentModel model, double threshold, SlotExtractor slotExtractor) {
        this.model = model;
        this.threshold = threshold;
        this.slotExtractor = slotExtractor;
    }

    /**
     * {@link IntentConfig} から分類器を構築するファクトリメソッド（データ駆動向け）。
     *
     * <p>内部で {@link IntentModel} を組み立て、{@link RegexSlotExtractor} を作成する。
     * スロットパターンが空の場合は slotExtractor = null として扱う。
     *
     * @param cfg IntentConfigLoader で読み込んだ設定
     * @param tok トークナイザ（例: {@link CjkBigramTokenizer}）
     * @return 構築済み IntentClassifier
     */
    public static IntentClassifier fromConfig(IntentConfig cfg, Tokenizer tok) {
        Tokenizer effective = tok;
        if (cfg.getSynonyms() != null && !cfg.getSynonyms().isEmpty()) {
            effective = new NormalizingTokenizer(tok, new SynonymNormalizer(cfg.getSynonyms()));
        }
        IntentModel model = new IntentModel(cfg.getExamples(), effective);
        SlotExtractor slotExtractor = cfg.getSlotPatterns().isEmpty()
            ? null
            : new RegexSlotExtractor(cfg.getSlotPatterns());
        return new IntentClassifier(model, cfg.getThreshold(), slotExtractor);
    }

    /**
     * テキストを分類する。
     *
     * @param text 入力テキスト
     * @return 分類結果
     */
    public IntentResult classify(String text) {
        // Step 1: トークン化 → クエリTF-IDF単位ベクトル
        List<String> tokens = model.getTokenizer().tokenize(text);
        Map<String, Double> queryVec = model.buildQueryVector(tokens);

        // Step 2: 各例との類似度を計算し、意図ごとに最大スコアを集約
        Map<String, Double> intentScores = new HashMap<>();
        IntentExample[] examples = model.getExamples();
        List<Map<String, Double>> exVectors = model.getExampleVectors();

        for (int i = 0; i < examples.length; i++) {
            String intent = examples[i].intent();
            double score = IntentModel.cosineSimilarity(queryVec, exVectors.get(i));
            intentScores.merge(intent, score, Math::max);
        }

        // Step 3: ランキング（降順ソート）
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(intentScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // LinkedHashMapで順序保持
        Map<String, Double> ranking = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : sorted) {
            ranking.put(e.getKey(), e.getValue());
        }

        // Step 4: 最良スコアと意図を決定
        String bestIntent;
        double bestScore;
        if (sorted.isEmpty() || sorted.get(0).getValue() < threshold) {
            bestIntent = "UNKNOWN";
            bestScore = sorted.isEmpty() ? 0.0 : sorted.get(0).getValue();
        } else {
            bestIntent = sorted.get(0).getKey();
            bestScore = sorted.get(0).getValue();
        }

        // Step 5: スロット抽出
        Map<String, String> slots;
        if (slotExtractor != null) {
            slots = slotExtractor.extract(text);
        } else {
            slots = Map.of();
        }

        return new IntentResult(bestIntent, bestScore, ranking, slots);
    }
}
