package com.demo.intent;

import java.util.Map;

/**
 * 意図分類の結果を保持するクラス。
 */
public class IntentResult {

    /** 推論された意図ラベル（閾値未満の場合は "UNKNOWN"） */
    public final String intent;

    /** 最良スコア（コサイン類似度 0.0〜1.0） */
    public final double confidence;

    /**
     * 意図 → 最高スコアのランキングマップ（降順）。
     * LinkedHashMapで挿入順=降順を保持。
     */
    public final Map<String, Double> ranking;

    /** 抽出されたスロット（SlotExtractorがnullなら空マップ） */
    public final Map<String, String> slots;

    public IntentResult(String intent, double confidence,
                        Map<String, Double> ranking, Map<String, String> slots) {
        this.intent = intent;
        this.confidence = confidence;
        this.ranking = ranking;
        this.slots = slots;
    }

    /**
     * 結果を簡易JSONに変換する（外部ライブラリ不使用・手書き）。
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"intent\": \"").append(escapeJson(intent)).append("\",\n");
        sb.append("  \"confidence\": ").append(String.format("%.4f", confidence)).append(",\n");

        // ranking
        sb.append("  \"ranking\": {\n");
        int ri = 0;
        for (Map.Entry<String, Double> e : ranking.entrySet()) {
            sb.append("    \"").append(escapeJson(e.getKey())).append("\": ")
              .append(String.format("%.4f", e.getValue()));
            if (ri < ranking.size() - 1) sb.append(",");
            sb.append("\n");
            ri++;
        }
        sb.append("  },\n");

        // slots
        sb.append("  \"slots\": {\n");
        int si = 0;
        for (Map.Entry<String, String> e : slots.entrySet()) {
            sb.append("    \"").append(escapeJson(e.getKey())).append("\": \"")
              .append(escapeJson(e.getValue())).append("\"");
            if (si < slots.size() - 1) sb.append(",");
            sb.append("\n");
            si++;
        }
        sb.append("  }\n");

        sb.append("}");
        return sb.toString();
    }

    /** JSON文字列エスケープ（最低限: バックスラッシュ・ダブルクォート・制御文字） */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return toJson();
    }
}
