package com.demo.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 推論前のテキスト正規化（同義語レイヤ）。
 *
 * <p>variant→canonical のマップを受け取り、入力テキスト中の variant 表現を
 * canonical に置換する。例: 「払い出し」→「出庫」、「お休み」→「休暇」。
 * これにより、辞書に書いていない言い回しでも canonical 形に揃えてから
 * TF-IDF 照合できる（小さなドメイン同義語辞書で言い換え耐性を上げる）。
 *
 * <p>置換は variant の長い順に行う（短い語が長い語の一部を先食いしないように）。
 * 単純な部分文字列置換なので、ドメインに閉じた語彙で使うこと。
 */
public class SynonymNormalizer {

    private final List<String[]> rules; // {variant, canonical}, variant 長い順

    public SynonymNormalizer(Map<String, String> variantToCanonical) {
        this.rules = new ArrayList<>();
        for (Map.Entry<String, String> e : variantToCanonical.entrySet()) {
            if (e.getKey() != null && !e.getKey().isEmpty()) {
                rules.add(new String[]{e.getKey(), e.getValue()});
            }
        }
        rules.sort((a, b) -> Integer.compare(b[0].length(), a[0].length()));
    }

    /** variant を canonical に置換して返す。 */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text;
        for (String[] r : rules) {
            s = s.replace(r[0], r[1]);
        }
        return s;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
