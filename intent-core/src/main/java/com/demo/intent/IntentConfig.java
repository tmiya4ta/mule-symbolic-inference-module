package com.demo.intent;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 外部ファイルから読み込んだ意図分類設定のコンテナ。
 *
 * <p>保持するもの:
 * <ul>
 *   <li>{@link #getExamples()} — 意図例リスト (intents.tsv 由来)</li>
 *   <li>{@link #getSlotPatterns()} — スロット名 → Patternのマップ (slots.tsv 由来)</li>
 *   <li>{@link #getThreshold()} — コサイン類似度の分類閾値 (intent.properties 由来)</li>
 * </ul>
 *
 * <p>このクラスはイミュータブルなコンテナで、ロジックを持たない。
 * 構築には {@link IntentConfigLoader} を使う。
 */
public class IntentConfig {

    private final List<IntentExample> examples;
    private final Map<String, Pattern> slotPatterns;
    private final double threshold;
    private final Map<String, String> synonyms;

    /**
     * @param examples     意図例リスト（空でもよい）
     * @param slotPatterns スロット名 → コンパイル済みPatternのマップ（空でもよい）
     * @param threshold    コサイン類似度の最低閾値（例: 0.15）
     */
    public IntentConfig(List<IntentExample> examples,
                        Map<String, Pattern> slotPatterns,
                        double threshold) {
        this(examples, slotPatterns, threshold, Map.of());
    }

    /**
     * @param synonyms 同義語マップ（variant→canonical）。空なら正規化なし。
     */
    public IntentConfig(List<IntentExample> examples,
                        Map<String, Pattern> slotPatterns,
                        double threshold,
                        Map<String, String> synonyms) {
        this.examples = List.copyOf(examples);
        this.slotPatterns = Map.copyOf(slotPatterns);
        this.threshold = threshold;
        this.synonyms = Map.copyOf(synonyms);
    }

    /** 意図例リストを返す（変更不可）。 */
    public List<IntentExample> getExamples() {
        return examples;
    }

    /** スロット名 → Patternのマップを返す（変更不可）。 */
    public Map<String, Pattern> getSlotPatterns() {
        return slotPatterns;
    }

    /** コサイン類似度の分類閾値を返す。 */
    public double getThreshold() {
        return threshold;
    }

    /** 同義語マップ（variant→canonical）を返す。空なら正規化なし。 */
    public Map<String, String> getSynonyms() {
        return synonyms;
    }

    @Override
    public String toString() {
        return "IntentConfig{examples=" + examples.size()
             + ", slots=" + slotPatterns.keySet()
             + ", threshold=" + threshold + "}";
    }
}
