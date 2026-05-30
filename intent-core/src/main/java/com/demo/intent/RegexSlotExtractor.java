package com.demo.intent;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正規表現パターンを使ってスロットを抽出する実装。
 *
 * <p>各パターンに対して:
 * <ul>
 *   <li>キャプチャグループがあれば最初のグループ（group(1)）を値とする。</li>
 *   <li>キャプチャグループがなければマッチ全体（group(0)）を値とする。</li>
 * </ul>
 */
public class RegexSlotExtractor implements SlotExtractor {

    /** スロット名 → 正規表現パターン */
    private final Map<String, Pattern> patterns;

    /**
     * @param patterns スロット名 → コンパイル済みPatternのマップ
     */
    public RegexSlotExtractor(Map<String, Pattern> patterns) {
        this.patterns = Map.copyOf(patterns);
    }

    @Override
    public Map<String, String> extract(String text) {
        Map<String, String> slots = new HashMap<>();
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            Matcher m = entry.getValue().matcher(text);
            if (m.find()) {
                // キャプチャグループがあればgroup(1)、なければgroup(0)
                String value = m.groupCount() >= 1 ? m.group(1) : m.group(0);
                if (value != null) {
                    slots.put(entry.getKey(), value);
                }
            }
        }
        return slots;
    }
}
